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

import controllers.UserEmailPreferences.fromRepositoryModel
import config.{ApplicationConfig, ErrorHandler}
import connectors.{ApiPlatformMicroserviceConnector, ThirdPartyDeveloperConnector}
import domain.Developer
import javax.inject.Inject
import model.APICategory
import model.APICategory.APICategory
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent}
import repository.EmailPreferenceSelectionsRepository
import repository.EmailPreferenceSelectionsRepository.{EmailPreferenceSelections, TaxRegimeServices}
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import views.html.emailpreferences._

import scala.concurrent.{ExecutionContext, Future}

class EmailPreferences @Inject()(val emailPreferenceSelectionsRepository: EmailPreferenceSelectionsRepository,
                                 val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                                 val apiPlatformMicroserviceConnector: ApiPlatformMicroserviceConnector,
                                 val sessionService: SessionService,
                                 val messagesApi: MessagesApi,
                                 val errorHandler: ErrorHandler,
                                 val cookieSigner : CookieSigner)
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedInController {

  def emailPreferencesStartPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(emailPreferences()))
  }

  def taxRegimeSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    userEmailPreferences(request.developerSession.email).map { userEmailPreferences =>
      Ok(taxRegimeSelection(userEmailPreferences.availableTaxRegimes, userEmailPreferences.selectedTaxRegimes))
    }
  }

  def serviceSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    userEmailPreferences(request.developerSession.email).map { userEmailPreferences =>
      Ok(serviceSelection(userEmailPreferences))
    }
  }

  def topicSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(topicSelection()))
  }

  def emailPreferencesCompletePage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(confirmation()))
  }

  private def userEmailPreferences(userEmail: String): Future[UserEmailPreferences] =
    emailPreferenceSelectionsRepository.fetchByEmail(userEmail, fromRepositoryModel)
      .flatMap(localUserEmailPreferences =>
        if(localUserEmailPreferences.isDefined) Future.successful(localUserEmailPreferences.head) else userEmailPreferencesFromSourceServices(userEmail))

  private def userEmailPreferencesFromSourceServices(userEmail: String): Future[UserEmailPreferences] = {
    val servicesAvailableToUser: Future[Map[APICategory, Set[String]]] = apiPlatformMicroserviceConnector.fetchApiDefinitionsForCollaborator(userEmail)
    val userDetails: Future[Option[Developer]] = thirdPartyDeveloperConnector.fetchDeveloper(userEmail)(HeaderCarrier())

    for {
      userServices <- servicesAvailableToUser
      userServicesSelected <- userDetails
    } yield
      UserEmailPreferences(
        servicesAvailableToUser = userServices,
        servicesSelected = userServicesSelected.get.emailPreferences.interests.map(t => (APICategory.withName(t.regime), t.services)).toMap,
        topicsSelected = userServicesSelected.get.emailPreferences.topics.map(_.toString))
  }
}

case class UserEmailPreferences(servicesAvailableToUser: Map[APICategory, Set[String]],
                                servicesSelected: Map[APICategory, Set[String]],
                                topicsSelected: Set[String]) {


  def availableTaxRegimes: Set[APICategory] = servicesAvailableToUser.keys.toSet
  def selectedTaxRegimes: Set[APICategory] = servicesSelected.keys.toSet
}

object UserEmailPreferences {
  def fromRepositoryModel: EmailPreferenceSelections => UserEmailPreferences = { emailPreferencesSelection =>
    def regimesAndServicesAsMap(regimesAndServices: List[TaxRegimeServices]): Map[APICategory, Set[String]] =
      regimesAndServices
        .map(t => (t.taxRegime, t.services))
        .toMap

    UserEmailPreferences(
      regimesAndServicesAsMap(emailPreferencesSelection.servicesAvailableToUser),
      regimesAndServicesAsMap(emailPreferencesSelection.servicesSelected),
      emailPreferencesSelection.topicsSelected)
  }
}