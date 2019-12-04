/*
 * Copyright 2019 HM Revenue & Customs
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

import java.io

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.FormKeys.{appNameField, applicationNameInvalidKey, emailaddressAlreadyInUseGlobalKey, emailaddressField, emailalreadyInUseKey}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import domain.Environment
import domain.Environment.{PRODUCTION, SANDBOX}
import play.api.mvc.Result
import service._
import views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddApplication @Inject()(val applicationService: ApplicationService,
  val developerConnector: ThirdPartyDeveloperConnector,
  val sessionService: SessionService,
  val auditService: AuditService,
  val errorHandler: ErrorHandler,
  val messagesApi: MessagesApi,
  implicit val appConfig: ApplicationConfig)
  (implicit ec: ExecutionContext) extends ApplicationController {

  def addApplicationSubordinate = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.addApplicationStartSubordinate()))
  }

  def addApplicationProductionSuccess(applicationId: String) = whenTeamMemberOnApp(applicationId) { implicit request =>
    Future.successful(Ok(views.html.addApplicationProductionSuccess(request.application.name, applicationId)))
  }

  def nameAddApplication(environment: String) = loggedInAction { implicit request =>
    Environment.from(environment) match {
      case Some(SANDBOX) =>
        Future.successful(Ok(views.html.addApplicationName(AddApplicationNameForm.form, Environment.from(environment))))
      case Some(PRODUCTION) =>
        Future.successful(Ok(views.html.addApplicationName(AddApplicationNameForm.form, Environment.from(environment))))
      case _ => Future.successful(NotFound(errorHandler.notFoundTemplate(request)))
    }
  }

    def nameApplicationAction(environment: String) = loggedInAction { implicit request =>
      val requestForm: Form[AddApplicationNameForm] = AddApplicationNameForm.form.bindFromRequest

      def nameApplicationWithErrors(errors: Form[AddApplicationNameForm], environment: String) =
        Future.successful(Ok(views.html.addApplicationName(errors, Environment.from(environment))))

      def nameApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationNameForm) = {
        applicationService.isApplicationNameValid(formThatPassesSimpleValidation.applicationName,
          Environment.from(environment).getOrElse(SANDBOX), selfApplicationId = None).flatMap {
          case Valid =>
            applicationService
              .createForUser(CreateApplicationRequest.fromSandboxJourney(loggedIn, formThatPassesSimpleValidation,
                Environment.from(environment).getOrElse(SANDBOX)))
              .map(appCreated => {
                if (Environment.from(environment) == Some(PRODUCTION)) {
                  Redirect(routes.AddApplication.addApplicationProductionSuccess(appCreated.id))
                } else Redirect(routes.Subscriptions.subscriptions(appCreated.id))
              })
          case invalid: Invalid => {
            def invalidApplicationNameForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

            Future.successful(BadRequest(views.html.addApplicationName(invalidApplicationNameForm, Environment.from(environment))))
          }
        }
      }

      requestForm.fold(formWithErrors => nameApplicationWithErrors(formWithErrors, environment), nameApplicationWithValidForm)
    }
}