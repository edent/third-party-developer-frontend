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
import domain.ApiSubscriptionFields._
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
import service.SubscriptionFieldsService.ValidateAgainstRole
import domain.ApiSubscriptionFields.SubscriptionFieldDefinition
import domain.Role._
import domain.Role
import domain.DevhubAccessLevel

object ManageSubscriptions {

  case class FieldValue(name: String, value: String)

  case class ApiDetails(name: String, context: String, version: String, displayedStatus: String, subsValues: Seq[FieldValue])

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

  case class EditApiConfigurationViewModel(
    apiName: String,
    apiVersion: String,
    apiContext: String,
    displayedStatus: String,
    fields: Seq[SubscriptionFieldViewModel],
    form: Form[EditApiConfigurationFormData])

  object EditApiConfigurationViewModel {
    def toViewModel(apiSubscription : APISubscriptionStatusWithSubscriptionFields, form : Form[EditApiConfigurationFormData]): EditApiConfigurationViewModel = {
     val fieldsViewModel : Seq[SubscriptionFieldViewModel] = apiSubscription.fields.fields
        .map(field => SubscriptionFieldViewModel(field.definition.description, field.definition.hint))
      
      EditApiConfigurationViewModel(apiSubscription.name, apiSubscription.apiVersion.version, apiSubscription.context, apiSubscription.apiVersion.displayedStatus, fieldsViewModel, form)
    }
  }

  case class SubscriptionFieldViewModel(description: String, hint: String)
 
  case class EditApiConfigurationFormData(fields: List[EditSubscriptionValueFormData])
  case class EditSubscriptionValueFormData(name: String, value: String)

  object EditApiConfigurationFormData {
    val form: Form[EditApiConfigurationFormData] = Form(
      mapping(
        "fields" -> list(
          mapping(
            "name" -> text,
            "value" -> text
          )(fromFormValues)(toFormValues)
        )
      )(EditApiConfigurationFormData.apply)(EditApiConfigurationFormData.unapply)
      )
  }

  def toFormData(in: APISubscriptionStatusWithSubscriptionFields): Form[EditApiConfigurationFormData] = {
    def toEditSubscriptionValueFormData(fieldValue: SubscriptionFieldValue) : EditSubscriptionValueFormData = {
      // TODO: Fix to make access control work
      // val role : Role = Role.DEVELOPER
      // val accessLevel = DevhubAccessLevel.fromRole(role)
      // fieldValue.definition.access.devhub.satisfiesWrite(accessLevel)
      // val canWrite = true
      EditSubscriptionValueFormData(fieldValue.definition.name, fieldValue.value)
    }

    val data = EditApiConfigurationFormData(fields = in.fields.fields.toList.map(toEditSubscriptionValueFormData(_)))
    EditApiConfigurationFormData.form.fill(data)
  }

  def fromFormValues(
      name: String,
      value: String
  ) = {
      EditSubscriptionValueFormData(name, value)
  }

  def toFormValues(editSubscriptionValueFormData: EditSubscriptionValueFormData): Option[(String, String)] = {
    Some((editSubscriptionValueFormData.name, editSubscriptionValueFormData.value))
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
    subFieldsDefinitionsExistActionByApi(applicationId, context, version) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val apiSubscription : APISubscriptionStatusWithSubscriptionFields =  definitionsRequest.apiSubscription
  
      val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, toFormData(apiSubscription))

      successful(Ok(views.html.managesubscriptions.editApiMetadata(appRQ.application, viewModel, apiSubscription, toFormData(apiSubscription), mode))) 
    }

  def saveSubscriptionFields(applicationId: String,
                             apiContext: String,
                             apiVersion: String,
                             mode: SaveSubsFieldsPageMode) : Action[AnyContent] = 
      subFieldsDefinitionsExistActionByApi(applicationId, apiContext, apiVersion) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
 
    implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
    implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

    import SaveSubsFieldsPageMode._
    val successRedirectUrl = mode match {
      case LeftHandNavigation => routes.ManageSubscriptions.listApiSubscriptions(applicationId)
      case CheckYourAnswers => checkpages.routes.CheckYourAnswers.answersPage(applicationId).withFragment("configurations")
    }

    val apiSubscription = definitionsRequest.apiSubscription
    
    val subscriptionFieldDefinitions = apiSubscription.fields.fields.map(value => value.definition)

    subscriptionConfigurationSave(apiContext, apiVersion, successRedirectUrl, subscriptionFieldDefinitions, (formWithErrors : Form[EditApiConfigurationFormData])=>{

        val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, formWithErrors)

        editApiMetadata(definitionsRequest.applicationRequest.application, viewModel, apiSubscription, formWithErrors, mode)
      }
    )
  }

  private def subscriptionConfigurationSave(apiContext: String,
                                            apiVersion: String,
                                            successRedirect: Call,
                                            subscriptionFieldDefinitions: Seq[SubscriptionFieldDefinition],
                                            validationFailureView : Form[EditApiConfigurationFormData] => Html)
                                           (implicit hc: HeaderCarrier, request: ApplicationRequest[_]): Future[Result] = {

    def handleValidForm(validForm: EditApiConfigurationFormData) = {
      def saveFields(validForm: EditApiConfigurationFormData)(implicit hc: HeaderCarrier): Future[ServiceSaveSubscriptionFieldsResponse] = {
        if (validForm.fields.nonEmpty) {
          val subscriptionLookup = subscriptionFieldDefinitions.map(d => (d.name -> d)).toMap

          val valuesToSave : Seq[SubscriptionFieldValue] = validForm.fields.map(formField => {
            val subscriptionName = formField.name
            val newValue = formField.value

            val definition = subscriptionLookup.get(subscriptionName).getOrElse(throw new RuntimeException("Bang"))
            SubscriptionFieldValue(definition , newValue)
          })

          subFieldsService
            .saveFieldValues(ValidateAgainstRole(request.role), request.application, apiContext, apiVersion, valuesToSave)
        } else {
          Future.successful(SaveSubscriptionFieldsSuccessResponse)
        }
      }

      saveFields(validForm) map {
        case SaveSubscriptionFieldsSuccessResponse => Redirect(successRedirect)
        case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
          val errors = fieldErrors.map(fe => data.FormError(fe._1, fe._2)).toSeq
          val formWithErrors  = EditApiConfigurationFormData.form.fill(validForm).copy(errors = errors)
          BadRequest(validationFailureView(formWithErrors))
        case SaveSubscriptionFieldsAccessDeniedResponse => Forbidden(errorHandler.badRequestTemplate)
      }
    }

    def handleInvalidForm(formWithErrors: Form[EditApiConfigurationFormData]) = {
      val displayedStatus = formWithErrors.data.getOrElse("displayedStatus", throw new Exception("Missing form field: displayedStatus"))
    
      Future.successful(BadRequest(validationFailureView(formWithErrors)))
    }

    EditApiConfigurationFormData.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
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

      val apiSubscription = definitionsRequest.apiSubscriptionStatus

      Future.successful(Ok(views.html.createJourney.subscriptionConfigurationPage(
        definitionsRequest.applicationRequest.application,
        pageNumber,
        apiSubscription,
        toFormData(definitionsRequest.apiSubscriptionStatus))
      ))
    }

  def subscriptionConfigurationPagePost(applicationId: String, pageNumber: Int) : Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>

      implicit val applicationRequest: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val successRedirectUrl = routes.ManageSubscriptions.subscriptionConfigurationStepPage(applicationId,  pageNumber)

      val apiSubscription = definitionsRequest.apiSubscriptionStatus

      val subscriptionFieldDefinitions = apiSubscription.fields.fields.map(value => value.definition)

      subscriptionConfigurationSave(
        definitionsRequest.apiDetails.context,
        definitionsRequest.apiDetails.version,
        successRedirectUrl,
        subscriptionFieldDefinitions,
        viewModel => {
          views.html.createJourney.subscriptionConfigurationPage(definitionsRequest.applicationRequest.application, pageNumber, apiSubscription, viewModel)
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
