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

package connectors

import connectors.ApiPlatformMicroserviceConnector.{APIDefinition, servicesByCategory}
import javax.inject.{Inject, Singleton}
import model.APICategory.APICategory
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import cats.implicits._
import config.ApplicationConfig
import model.APICategory

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiPlatformMicroserviceConnector @Inject()(config: ApplicationConfig, http: HttpClient)(implicit ec: ExecutionContext) {

  def fetchApiDefinitionsForCollaborator(collaboratorEmail: String): Future[Map[APICategory, Set[String]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    http.GET[Seq[APIDefinition]](s"${config.apiPlatformMicroserviceUrl}/combined-api-definitions", Seq("collaboratorEmail" -> collaboratorEmail))
      .map(servicesByCategory)
  }
}

object ApiPlatformMicroserviceConnector {
  implicit val formatAPIDefinition: Format[APIDefinition] = Json.format[APIDefinition]

  private[connectors] case class APIDefinition(serviceName: String, categories: Seq[String] = Seq.empty)

  def servicesByCategory(apiDefinitions: Seq[APIDefinition]): Map[APICategory, Set[String]] = {
    def serviceCategories(apiDefinition: APIDefinition): Map[APICategory, Set[String]] =
      apiDefinition.categories
        .map(category => APICategory.withName(category) -> Set(apiDefinition.serviceName))
        .toMap

    apiDefinitions
      .map(serviceCategories)
      .fold(Map.empty)(_ combine _)
  }
}
