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

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import domain.{DeveloperSession, UpdateProfileRequest}
import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import play.api.libs.crypto.CookieSigner
import service.{ApplicationService, AuditService, SessionService}
import views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Profile @Inject()(
  val applicationService: ApplicationService,
  val auditService: AuditService,
  val sessionService: SessionService,
  val connector: ThirdPartyDeveloperConnector,
  val errorHandler: ErrorHandler,
  val messagesApi: MessagesApi,
  val cookieSigner : CookieSigner
)
(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedInController with PasswordChange {

  import ErrorFormBuilder.GlobalError
  import play.api.data._

  val profileForm: Form[ProfileForm] = ProfileForm.form
  val passwordForm: Form[ChangePasswordForm] = ChangePasswordForm.form
  val deleteProfileForm: Form[DeleteProfileForm] = DeleteProfileForm.form

  private def changeProfileView(developerSession: DeveloperSession)(implicit req: UserRequest[_]) = {
    views.html.changeProfile(profileForm.fill(ProfileForm(developerSession.developer.firstName, developerSession.developer.lastName, developerSession.developer.organisation)))
  }

  def showProfile(): Action[AnyContent] = loggedInAction2 { implicit request =>
    Future.successful(Ok(views.html.profile()))
  }

  def changeProfile(): Action[AnyContent] = loggedInAction2 { implicit request =>
    Future.successful(Ok(changeProfileView(loggedIn2)))
  }

  def updateProfile(): Action[AnyContent] = loggedInAction2 { implicit request =>
    val requestForm = profileForm.bindFromRequest
    requestForm.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.changeProfile(formWithErrors.firstnameGlobal().lastnameGlobal())))
      },
      profile => connector.updateProfile(loggedIn2.email, UpdateProfileRequest(profile.firstName.trim, profile.lastName.trim, profile.organisation)) map {
        _ => {

          val updatedDeveloper = loggedIn2.developer.copy(
              firstName = profile.firstName,
              lastName = profile.lastName,
              organisation = profile.organisation)

          val updatedLoggedIn = loggedIn2.copy(
            session = loggedIn2.session.copy(developer = updatedDeveloper)
          )

          Ok(profileUpdated("profile updated", "Manage profile", "manage-profile", updatedLoggedIn))
        }
      }
    )
  }

  def showPasswordPage(): Action[AnyContent] = loggedInAction2 { implicit request =>
    Future.successful(Ok(changeProfilePassword(passwordForm)))
  }

  def updatePassword(): Action[AnyContent] = loggedInAction2 { implicit request =>
    processPasswordChange(loggedIn2.email,
      Ok(passwordUpdated("password changed", "Password changed", "change-password")),
      changeProfilePassword(_))
  }

  def requestDeletion(): Action[AnyContent] = loggedInAction2 { implicit request =>
    Future.successful(Ok(profileDeleteConfirmation(DeleteProfileForm.form)))
  }

  def deleteAccount(): Action[AnyContent] = loggedInAction2 { implicit request =>
    val form = deleteProfileForm.bindFromRequest

    form.fold(
      invalidForm => {
        Future.successful(BadRequest(profileDeleteConfirmation(invalidForm)))
      },
      validForm => {
        validForm.confirmation match {
          case Some("true") => applicationService
            .requestDeveloperAccountDeletion(loggedIn2.displayedName, loggedIn2.email)
            .map(_ => Ok(profileDeleteSubmitted()))

          case _ => Future.successful(Ok(changeProfileView(loggedIn2)))
        }
      }
    )
  }
}
