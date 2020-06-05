/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import cats.data.NonEmptyList
import com.google.inject.{Inject, Singleton}
import config.{ApplicationConfig, ErrorHandler}
import domain.{APISubscriptionStatusWithSubscriptionFields, CheckInformation, Environment, Application}
import domain.ApiSubscriptionFields.{SaveSubscriptionFieldsFailureResponse, SaveSubscriptionFieldsResponse, SaveSubscriptionFieldsSuccessResponse, SubscriptionFieldValue}
import model.NoSubscriptionFieldsRefinerBehaviour
import play.api.data
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.libs.crypto.CookieSigner
import play.twirl.api.Html
import service.{ApplicationService, AuditService, SessionService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.managesubscriptions.editApiMetadata

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import domain.SaveSubsFieldsPageMode

object ManageSubscriptions {

  case class FieldValue(name: String, value: String)

  case class ApiDetails(name: String, context: String, version: String, displayedStatus: String, subsValues: NonEmptyList[FieldValue])

  def toFieldValue(sfv: SubscriptionFieldValue): FieldValue = {
    def default(in: String, default: String) = if (in.isEmpty) default else in

    FieldValue(sfv.definition.shortDescription, default(sfv.value, "None"))
  }

  def toDetails(in: APISubscriptionStatusWithSubscriptionFields): ApiDetails = {
    ApiDetails(
      name = in.name,
      context = in.context,
      version = in.apiVersion.version,
      displayedStatus = in.apiVersion.displayedStatus,
      subsValues = in.fields.fields.map(toFieldValue)
    )
  }

  def toForm(in: APISubscriptionStatusWithSubscriptionFields): EditApiMetadata = {
    EditApiMetadata(fields = in.fields.fields.toList.map(field => EditSubscriptionValueFormData(field.definition.name, field.value)))
  }

  case class EditSubscriptionValueFormData(name: String, value: String)
  case class EditApiMetadata(fields: List[EditSubscriptionValueFormData])

  object EditApiMetadata {
    val form: Form[EditApiMetadata] = Form(
      mapping(
        "fields" -> list(
          mapping(
            "name" -> text,
            "value" -> text
          )(fromFormValues)(toFormValues)
        )
      )(EditApiMetadata.apply)(EditApiMetadata.unapply)
    )
  }

  case class EditApiMetadataViewModel(fieldsForm: Form[EditApiMetadata])

  def toViewModel(in: APISubscriptionStatusWithSubscriptionFields): EditApiMetadataViewModel = {
    val data = toForm(in)
    EditApiMetadataViewModel(EditApiMetadata.form.fill(data))
  }

  def fromFormValues(
      name: String,
      value: String
  ) = {
      EditSubscriptionValueFormData(name, value)
  }

  def toFormValues(
      editSubscriptionValueFormData: EditSubscriptionValueFormData
  ): Option[(String, String)] = {
    Some(
      (
        editSubscriptionValueFormData.name,
        editSubscriptionValueFormData.value
      )
    )
  }
}

@Singleton
class ManageSubscriptions @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val applicationService: ApplicationService,
    val errorHandler: ErrorHandler,
    val messagesApi: MessagesApi,
    val subFieldsService: SubscriptionFieldsService,
    val cookieSigner : CookieSigner
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController
      with ApplicationHelper {

  import ManageSubscriptions._

  def listApiSubscriptions(applicationId: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val details = definitionsRequest
        .fieldDefinitions
        .map(toDetails)
        .toList

      successful(Ok(views.html.managesubscriptions.listApiSubscriptions(definitionsRequest.applicationRequest.application, details)))
    }

  def editApiMetadataPage(applicationId: String, context: String, version: String, mode: SaveSubsFieldsPageMode): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      // TODO: Can we do this in refiner?
      definitionsRequest.fieldDefinitions
        .filter(s => s.context.equalsIgnoreCase(context) && s.apiVersion.version.equalsIgnoreCase(version))
        .headOption
        .map( (vm : APISubscriptionStatusWithSubscriptionFields) => {
          successful(Ok(views.html.managesubscriptions.editApiMetadata(appRQ.application, vm.fields, toViewModel(vm), mode)))
        })
        .getOrElse(successful(NotFound(errorHandler.notFoundTemplate)))
    }

  def saveSubscriptionFields(applicationId: String,
                             apiContext: String,
                             apiVersion: String,
                             mode: SaveSubsFieldsPageMode) : Action[AnyContent] = 
      subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
 
    implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
    implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

    import SaveSubsFieldsPageMode._
    val successRedirectUrl = mode match {
      case LeftHandNavigation => routes.ManageSubscriptions.listApiSubscriptions(applicationId)
      case CheckYourAnswers => checkpages.routes.CheckYourAnswers.answersPage(applicationId).withFragment("configurations")
    }

    val x : APISubscriptionStatusWithSubscriptionFields = definitionsRequest.fieldDefinitions
      .filter(s => s.context.equalsIgnoreCase(apiContext) && s.apiVersion.version.equalsIgnoreCase(apiVersion))
      .headOption.get // TODO: Naked get / empty list
      
    subscriptionConfigurationSave(apiContext, apiVersion, successRedirectUrl, vm =>
      editApiMetadata(definitionsRequest.applicationRequest.application,x.fields, vm, mode)
    )
  }

  private def subscriptionConfigurationSave(apiContext: String,
                                            apiVersion: String,
                                            successRedirect: Call,
                                            validationFailureView : EditApiMetadataViewModel => Html)
                                           (implicit hc: HeaderCarrier, request: ApplicationRequest[_]): Future[Result] = {

    def handleValidForm(validForm: EditApiMetadata) = {
      def saveFields(validForm: EditApiMetadata)(implicit hc: HeaderCarrier): Future[SaveSubscriptionFieldsResponse] = {
        if (validForm.fields.nonEmpty) {

          // TODO: Lookup field definitions
          subFieldsService.saveFieldValues(request.application, apiContext, apiVersion, Map(validForm.fields.map(f => f.name -> f.value): _*))
        } else {
          Future.successful(SaveSubscriptionFieldsSuccessResponse)
        }
      }

      saveFields(validForm) map {
        case SaveSubscriptionFieldsSuccessResponse => Redirect(successRedirect)
        case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
          val errors = fieldErrors.map(fe => data.FormError(fe._1, fe._2)).toSeq
          val errorForm = EditApiMetadata.form.fill(validForm).copy(errors = errors)
          val vm = EditApiMetadataViewModel(errorForm)

          BadRequest(validationFailureView(vm))
      }
    }

    def handleInvalidForm(formWithErrors: Form[EditApiMetadata]) = {
      val displayedStatus = formWithErrors.data.getOrElse("displayedStatus", throw new Exception("Missing form field: displayedStatus"))

      val vm = EditApiMetadataViewModel(formWithErrors)
      Future.successful(BadRequest(validationFailureView(vm)))
    }

    EditApiMetadata.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def subscriptionConfigurationStart(applicationId: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId,
      NoSubscriptionFieldsRefinerBehaviour.Redirect(routes.AddApplication.addApplicationSuccess(applicationId))) {

      definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] => {

        implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
        implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

        val details = definitionsRequest
          .fieldDefinitions
          .map(toDetails)
          .toList

        Future.successful(Ok(views.html.createJourney.subscriptionConfigurationStart(definitionsRequest.applicationRequest.application, details)))
      }
    }

  def subscriptionConfigurationPage(applicationId: String, pageNumber: Int) : Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request

      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val fields = definitionsRequest.apiSubscriptionStatus.fields

      Future.successful(Ok(views.html.createJourney.subscriptionConfigurationPage(
        definitionsRequest.applicationRequest.application,
        pageNumber,
        fields,
        toViewModel(definitionsRequest.apiSubscriptionStatus))
      ))
    }

  def subscriptionConfigurationPagePost(applicationId: String, pageNumber: Int) : Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>

      implicit val applicationRequest: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val successRedirectUrl = routes.ManageSubscriptions.subscriptionConfigurationStepPage(applicationId,  pageNumber)

      val fields = definitionsRequest.apiSubscriptionStatus.fields

      subscriptionConfigurationSave(
        definitionsRequest.apiDetails.context,
        definitionsRequest.apiDetails.version,
        successRedirectUrl,
        viewModel => {
          views.html.createJourney.subscriptionConfigurationPage(definitionsRequest.applicationRequest.application, pageNumber, fields, viewModel)
        })
    }

  def subscriptionConfigurationStepPage(applicationId: String, pageNumber: Int): Action[AnyContent] = {
    def doEndOfJourneyRedirect(application: Application)(implicit hc: HeaderCarrier) = {
      if (application.deployedTo.isSandbox){
        Future.successful(Redirect(routes.AddApplication.addApplicationSuccess(application.id)))
      } else {
        val information = application.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionConfigurationsConfirmed = true)
        applicationService.updateCheckInformation(application, information) map { _ =>
          Redirect(checkpages.routes.ApplicationCheck.requestCheckPage(application.id))
        }
      }
    }

    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request

      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val application = definitionsRequest.applicationRequest.application

      if (pageNumber == definitionsRequest.totalPages) {
        doEndOfJourneyRedirect(application)

      } else {
        Future.successful (Ok(views.html.createJourney.subscriptionConfigurationStepPage(application, pageNumber, definitionsRequest.totalPages)))
      }
    }
  }
}
