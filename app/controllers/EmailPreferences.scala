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
import connectors.{ApiPlatformMicroserviceConnector, ThirdPartyDeveloperConnector}
import javax.inject.Inject
import model.APICategory.APICategory
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent}
import service.SessionService
import views.html.emailpreferences._

import scala.concurrent.{ExecutionContext, Future}

class EmailPreferences @Inject()(val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
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
    userEmailPreferences.map { userEmailPreferences =>
      Ok(taxRegimeSelection(userEmailPreferences.availableTaxRegimes, userEmailPreferences.selectedTaxRegimes))
    }
  }

  def serviceSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    userEmailPreferences.map { userEmailPreferences =>
      Ok(serviceSelection(userEmailPreferences))
    }
  }

  def topicSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(topicSelection()))
  }

  def emailPreferencesCompletePage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(confirmation()))
  }

  private def userEmailPreferences()(implicit request: UserRequest[AnyContent]): Future[UserEmailPreferences] = {
    val userEmail = request.developerSession.email

    apiPlatformMicroserviceConnector.fetchApiDefinitionsForCollaborator(userEmail)
      .map(servicesAvailable =>
        UserEmailPreferences(
          servicesAvailableToUser = servicesAvailable.map(regime => (regime._1, regime._2)),
          servicesSelected = Map.empty,
          topicsSelected = Set.empty))
  }
}

case class UserEmailPreferences(servicesAvailableToUser: Map[APICategory, Set[String]],
                                servicesSelected: Map[APICategory, Set[String]],
                                topicsSelected: Set[String]) {

  def availableTaxRegimes: Set[APICategory] = servicesAvailableToUser.keys.toSet
  def selectedTaxRegimes: Set[APICategory] = servicesSelected.keys.toSet
}