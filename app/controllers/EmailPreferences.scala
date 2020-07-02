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
import play.api.mvc.{Action, AnyContent, Result}
import service.SessionService
import views.html.emailpreferences.{confirmation, emailPreferences, serviceSelection, taxRegimeSelection, topicSelection}

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
    emailPreferenceSelections.map { selections =>
      Ok(taxRegimeSelection(selections))
    }

  }

  def serviceSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    emailPreferenceSelections.map { selections =>
      Ok(serviceSelection(selections))
    }
  }

  def topicSelectionPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(topicSelection()))
  }

  def emailPreferencesCompletePage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(confirmation()))
  }

  private def emailPreferenceSelections()(implicit request: UserRequest[AnyContent]): Future[EmailPreferenceSelections] = {
    apiPlatformMicroserviceConnector.fetchApiDefinitionsForCollaborator(request.developerSession.email)
      .map(servicesAvailable => EmailPreferenceSelections(servicesAvailable, Map.empty, Set.empty))
  }
}

case class EmailPreferenceSelections(servicesAvailableToUser: Map[APICategory, Set[String]],
                                     servicesSelected: Map[APICategory, Set[String]],
                                     topicsSelected: Set[String])

