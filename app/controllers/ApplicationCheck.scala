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
import controllers.FormKeys._
import domain.Capabilities.SupportsAppChecks
import domain.Permissions.AdministratorOnly
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, optional, text}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.voa.play.form.ConditionalMappings._
import views.html.{applicationcheck, editapplication}

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationCheck @Inject()(val applicationService: ApplicationService,
                                 val apiSubscriptionsHelper: ApiSubscriptionsHelper,
                                 val sessionService: SessionService,
                                 val errorHandler: ErrorHandler,
                                 val messagesApi: MessagesApi
                                 )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController() with ApplicationHelper {

  private def canUseChecksAction(applicationId: String)
                                (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(SupportsAppChecks,AdministratorOnly)(applicationId)(fun)

  def requestCheckPage(appId: String) = canUseChecksAction(appId) { implicit request =>
    val application = request.application

    Future.successful(Ok(applicationcheck.landingPage(application,
      ApplicationInformationForm.form.fill(CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))))))
  }

  def requestCheckAction(appId: String) = canUseChecksAction(appId) { implicit request =>

    def withFormErrors(app: Application)(form: Form[CheckInformationForm]): Future[Result] = {
      Future.successful(BadRequest(applicationcheck.landingPage(app, form)))
    }

    def withValidForm(app: Application)(form: CheckInformationForm): Future[Result] = {
      Future.successful(Redirect(routes.CheckYourAnswers.answersPage(appId)))
    }

    val app = request.application
    val requestForm = ApplicationInformationForm.form.fillAndValidate(CheckInformationForm.fromCheckInformation(app.checkInformation.getOrElse(CheckInformation())))

    requestForm.fold(withFormErrors(app), withValidForm(app))
  }

  def credentialsRequested(appId: String) = whenTeamMemberOnApp(appId) { implicit request =>
    Future.successful(Ok(editapplication.nameSubmitted(appId, request.application)))
  }

  def contactPage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    val contactForm = for {
      approvalInfo <- app.checkInformation
      contactDetails <- approvalInfo.contactDetails
    } yield ContactForm(contactDetails.fullname, contactDetails.email, contactDetails.telephoneNumber)


    Future.successful(contactForm match {
      case Some(form) => Ok(applicationcheck.contactDetails(app, ContactForm.form.fill(ContactForm(form.fullname, form.email, form.telephone)), mode))
      case _ => Ok(applicationcheck.contactDetails(app, ContactForm.form, mode))
    })
  }

  def contactAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val requestForm = ContactForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[ContactForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.contactDetails(app, form, mode)))
    }

    def withValidForm(form: ContactForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      applicationService.updateCheckInformation(app.id, information.copy(contactDetails = Some(ContactDetails(form.fullname, form.email, form.telephone)))) map { _ =>
        if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
          Redirect(routes.CheckYourAnswers.answersPage(app.id))
        } else {
          Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
        }
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def namePage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.confirmName(request.application, NameForm.form.fill(NameForm(request.application.name)), mode)))
  }

  def nameAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val requestForm = NameForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[NameForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.confirmName(app, form, mode)))
    }

    def updateNameIfChanged(form: NameForm) = {
      if (app.name != form.applicationName) {
        applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, form.applicationName, app.description, app.access))
      } else {
        Future.successful(())
      }
    }

    def withValidForm(form: NameForm): Future[Result] = {
      applicationService.isApplicationNameValid(form.applicationName, app.deployedTo, Some(app.id))
        .flatMap({
          case Valid =>
            val information = app.checkInformation.getOrElse(CheckInformation())
            for {
              _ <- updateNameIfChanged(form)
              _ <- applicationService.updateCheckInformation(app.id, information.copy(confirmedName = true))
            } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
              Redirect(routes.CheckYourAnswers.answersPage(app.id))
            } else {
              Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
            }
          case invalid : Invalid =>
            def invalidNameCheckForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

            Future.successful(BadRequest(views.html.applicationcheck.confirmName(request.application, invalidNameCheckForm, mode)))
        })
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def apiSubscriptionsPage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    apiSubscriptionsHelper.fetchAllSubscriptions(app, request.user)(hc).flatMap {
      case Some(subsData) =>
        Future.successful(Ok(apiSubscriptionsView(app, subsData, mode)))
      case None =>
        Future.successful(NotFound(errorHandler.notFoundTemplate))
    }
  }

  def apiSubscriptionsAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    val information = app.checkInformation.getOrElse(CheckInformation())

    def hasNonExampleSubscription(subscriptionData: SubscriptionData) =
      subscriptionData.subscriptions.fold(false)(subs => subs.apis.exists(_.hasSubscriptions))

    apiSubscriptionsHelper.fetchAllSubscriptions(app, request.user)(hc) flatMap {
      case None =>
        Future.successful(NotFound(errorHandler.notFoundTemplate))
      case Some(subscriptionData) if !hasNonExampleSubscription(subscriptionData) =>
        val form = DummySubscriptionsForm.form.bind(Map("hasNonExampleSubscription" -> "false"))
        Future.successful(BadRequest(apiSubscriptionsView(app, subscriptionData, mode, Some(form))))
      case _ =>
        for {
          _ <- applicationService.updateCheckInformation(app.id, information.copy(apiSubscriptionsConfirmed = true))
        } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
          Redirect(routes.CheckYourAnswers.answersPage(app.id))
        } else {
          Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
        }
    }
  }

  private def apiSubscriptionsView(app: Application, subscriptionData: SubscriptionData, mode: CheckYourAnswersPageMode,
    form: Option[Form[DummySubscriptionsForm]] = None)(implicit request: ApplicationRequest[AnyContent]) = {
    views.html.applicationcheck.apiSubscriptions(app, subscriptionData.role, subscriptionData.subscriptions, app.id, mode, form)
  }

  def privacyPolicyPage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = PrivacyPolicyForm(
          hasUrl(std.privacyPolicyUrl, app.checkInformation.map(_.providedPrivacyPolicyURL)),
          std.privacyPolicyUrl)
        Ok(applicationcheck.privacyPolicy(app, PrivacyPolicyForm.form.fill(form), mode))
      case _ => Ok(applicationcheck.privacyPolicy(app, PrivacyPolicyForm.form, mode))
    })
  }

  def privacyPolicyAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val requestForm = PrivacyPolicyForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[PrivacyPolicyForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.privacyPolicy(app, form, mode)))
    }

    def updateUrl(form: PrivacyPolicyForm) = {
      val access = app.access match {
        case s: Standard => s.copy(privacyPolicyUrl = form.privacyPolicyURL, overrides = Set.empty)
        case other => other
      }

      applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, app.name, app.description, access))
    }

    def withValidForm(form: PrivacyPolicyForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      for {
        _ <- updateUrl(form)
        _ <- applicationService.updateCheckInformation(app.id, information.copy(providedPrivacyPolicyURL = true))
      } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
        Redirect(routes.CheckYourAnswers.answersPage(app.id))
      } else {
        Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def termsAndConditionsPage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = TermsAndConditionsForm(
          hasUrl(std.termsAndConditionsUrl, app.checkInformation.map(_.providedTermsAndConditionsURL)),
          std.termsAndConditionsUrl)
        Ok(applicationcheck.termsAndConditions(app, TermsAndConditionsForm.form.fill(form), mode))
      case _ => Ok(applicationcheck.termsAndConditions(app, TermsAndConditionsForm.form, mode))
    })
  }

  def termsAndConditionsAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val requestForm = TermsAndConditionsForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[TermsAndConditionsForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.termsAndConditions(app, form, mode)))
    }

    def updateUrl(form: TermsAndConditionsForm) = {
      val access = app.access match {
        case s: Standard => s.copy(termsAndConditionsUrl = form.termsAndConditionsURL, overrides = Set.empty)
        case other => other
      }

      applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, app.name, app.description, access))
    }

    def withValidForm(form: TermsAndConditionsForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      for {
        _ <- updateUrl(form)
        _ <- applicationService.updateCheckInformation(app.id, information.copy(providedTermsAndConditionsURL = true))
      } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
        Redirect(routes.CheckYourAnswers.answersPage(app.id))
      } else {
        Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def termsOfUsePage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    val checkInformation = app.checkInformation.getOrElse(CheckInformation())
    val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)

    Future.successful(Ok(applicationcheck.termsOfUse(app, TermsOfUseForm.form.fill(termsOfUseForm), mode)))
  }

  def termsOfUseAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>

    val version = appConfig.currentTermsOfUseVersion
    val app = request.application

    val requestForm = TermsOfUseForm.form.bindFromRequest

    def withFormErrors(form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.termsOfUse(app, form, mode)))
    }

    def withValidForm(form: TermsOfUseForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())

      val updatedInformation = if (information.termsOfUseAgreements.exists(terms => terms.version == version)) {
        information
      }
      else {
        information.copy(termsOfUseAgreements = information.termsOfUseAgreements :+ TermsOfUseAgreement(request.user.email, DateTimeUtils.now, version))
      }

      for {
        _ <- applicationService.updateCheckInformation(app.id, updatedInformation)
      } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
        Redirect(routes.CheckYourAnswers.answersPage(app.id))
      } else {
        Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def team(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.team.team(request.application, request.role, request.user)))
  }

  def teamAction(appId: String) = canUseChecksAction(appId) { implicit request =>

    val information = request.application.checkInformation.getOrElse(CheckInformation())
    for {
      _ <- applicationService.updateCheckInformation(appId, information.copy(teamConfirmed = true))
    } yield Redirect(routes.ApplicationCheck.requestCheckPage(appId))
  }

  def teamAddMember(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.team.teamMemberAdd(request.application, AddTeamMemberForm.form, request.user, mode)))
  }

  def teamMemberRemoveConfirmation(appId: String, teamMemberHash:  String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    successful(request.application.findCollaboratorByHash(teamMemberHash)
      .map(collaborator => Ok(applicationcheck.team.teamMemberRemoveConfirmation(request.application, request.user, collaborator.emailAddress)))
      .getOrElse(Redirect(routes.ApplicationCheck.team(appId, mode))))
  }

  def teamMemberRemoveAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request => {

    def handleValidForm(form: RemoveTeamMemberCheckPageConfirmationForm) : Future[Result] = {
        applicationService
          .removeTeamMember(request.application, form.email, request.user.email)
          .map(_ => Redirect(routes.ApplicationCheck.team(appId, mode)))
      }

    def handleInvalidForm(form: Form[RemoveTeamMemberCheckPageConfirmationForm]) : Future[Result] = {
      successful(BadRequest)
    }

    RemoveTeamMemberCheckPageConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }
  }

  private def hasUrl(url: Option[String], hasCheckedUrl: Option[Boolean]) = {
    (url, hasCheckedUrl) match {
      case (Some(_), _) => Some("true")
      case (None, Some(true)) => Some("false")
      case _ => None
    }
  }
}

object ApplicationInformationForm {
  def form: Form[CheckInformationForm] = Form(
    mapping(
      "confirmedNameCompleted" -> boolean.verifying("confirm.name.required.field", cn => cn),
      "apiSubscriptionsCompleted" -> boolean.verifying("api.subscriptions.required.field", subsConfirmed => subsConfirmed),
      "contactDetailsCompleted" -> boolean.verifying("contact.details.required.field", cd => cd),
      "providedPolicyURLCompleted" -> boolean.verifying("privacy.links.required.field", provided => provided),
      "providedTermsAndConditionsURLCompleted" -> boolean.verifying("tnc.links.required.field", provided => provided),
      "teamConfirmedCompleted" -> boolean.verifying("team.required.field", provided => provided),
      "termsOfUseAgreementsCompleted" -> boolean.verifying("agree.terms.of.use.required.field", terms => terms)
    )(CheckInformationForm.apply)(CheckInformationForm.unapply)
  )
}

case class TermsAndConditionsForm(urlPresent: Option[String], termsAndConditionsURL: Option[String])

object TermsAndConditionsForm {
  def form: Form[TermsAndConditionsForm] = Form(
    mapping(
      "hasUrl" -> optional(text).verifying(tNcUrlNoChoiceKey, s => s.isDefined),
      "termsAndConditionsURL" -> mandatoryIfTrue(
        "hasUrl",
        text.verifying(tNcUrlInvalidKey, s => s.isEmpty || isValidUrl(s)).verifying(tNcUrlRequiredKey, _.nonEmpty)
      )
    )(TermsAndConditionsForm.apply)(TermsAndConditionsForm.unapply)
  )
}

case class PrivacyPolicyForm(urlPresent: Option[String], privacyPolicyURL: Option[String])

object PrivacyPolicyForm {
  def form: Form[PrivacyPolicyForm] = Form(
    mapping(
      "hasUrl" -> optional(text).verifying(privacyPolicyUrlNoChoiceKey, s => s.isDefined),
      "privacyPolicyURL" -> mandatoryIfTrue(
        "hasUrl",
        text.verifying(privacyPolicyUrlInvalidKey, s => s.isEmpty || isValidUrl(s)).verifying(privacyPolicyUrlRequiredKey, _.nonEmpty)
      )
    )(PrivacyPolicyForm.apply)(PrivacyPolicyForm.unapply)
  )
}

case class NameForm(applicationName: String)

object NameForm {
  def form: Form[NameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator
    )(NameForm.apply)(NameForm.unapply)
  )
}

case class DetailsForm(applicationDetails: String)

object DetailsForm {
  def form: Form[DetailsForm] = Form(
    mapping(
      "applicationDetails" -> textValidator(detailsRequiredKey, detailsMaxLengthKey, 3000)
    )(DetailsForm.apply)(DetailsForm.unapply)
  )
}

case class TermsOfUseForm(termsOfUseAgreed: Boolean)

object TermsOfUseForm {
  def form: Form[TermsOfUseForm] = Form(
    mapping(
      "termsOfUseAgreed" -> boolean.verifying(termsOfUseAgreeKey, b => b)
    )(TermsOfUseForm.apply)(TermsOfUseForm.unapply)
  )

  def fromCheckInformation(checkInformation: CheckInformation) = {
    TermsOfUseForm(checkInformation.termsOfUseAgreements.nonEmpty)
  }
}


case class ContactForm(fullname: String, email: String, telephone: String)

object ContactForm {
  def form: Form[ContactForm] = Form(
    mapping(
      "fullname" -> fullnameValidator,
      "email" -> emailValidator(),
      "telephone" -> telephoneValidator
    )(ContactForm.apply)(ContactForm.unapply)
  )
}

case class DummySubscriptionsForm(hasNonExampleSubscription: Boolean)

object DummySubscriptionsForm {
  def form: Form[DummySubscriptionsForm] = Form(
    mapping(
      "hasNonExampleSubscription" -> boolean
    )(DummySubscriptionsForm.apply)(DummySubscriptionsForm.unapply)
      .verifying("error.must.subscribe", x => x.hasNonExampleSubscription)
  )
}
