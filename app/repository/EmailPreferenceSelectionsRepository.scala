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

package repository

import akka.stream.Materializer
import javax.inject.Inject
import model.APICategory
import model.APICategory.APICategory
import org.joda.time.{DateTime, Duration}
import play.api.libs.json.{Format, JsString, JsSuccess, Json, Reads, Writes}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import repository.EmailPreferenceSelectionsRepository.EmailPreferenceSelections
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

class EmailPreferenceSelectionsRepository @Inject()(mongo: ReactiveMongoComponent)(implicit val mat: Materializer, val ec: ExecutionContext)
  extends ReactiveRepository[EmailPreferenceSelections, BSONObjectID](
      "emailPreferenceSelections",
      mongo.mongoConnector.db,
      EmailPreferenceSelections.emailPreferenceSelectionsFormat,
      ReactiveMongoFormats.objectIdFormats) {

  override def indexes = List(
    Index(key = List("email" -> Ascending), name = Some("emailIndex"), unique = true),
    Index(
      key = List("lastUpdate" -> Ascending),
      name = Some("expiryIndex"),
      background = true,
      options = BSONDocument("expireAfterSeconds" -> BSONLong(1800)))
  )

  def fetchByEmail(email: String): Future[Option[EmailPreferenceSelections]] =
    find("email" -> email)
      .map(_.headOption)

  def deleteByEmail(email: String): Future[Boolean] =
    remove("email" -> email)
      .map(_.ok)
}

object EmailPreferenceSelectionsRepository {
  private[repository] case class TaxRegimeServices(taxRegime: APICategory, services: Set[String])
  object TaxRegimeServices {
    val apiCategoryReads: Reads[APICategory] = Reads(j => JsSuccess(APICategory.withName(j.as[String])))
    val apiCategoryWrites: Writes[APICategory] = Writes(a => JsString(a.toString))

    implicit val apiCategoryFormat: Format[APICategory] = Format(apiCategoryReads, apiCategoryWrites)
    implicit val format = Json.format[TaxRegimeServices]
  }

  private[repository] case class EmailPreferenceSelections(email:String,
                                                           servicesAvailableToUser: List[TaxRegimeServices],
                                                           servicesSelected: List[TaxRegimeServices],
                                                           topicsSelected: Set[String],
                                                           lastUpdate: DateTime)

  object EmailPreferenceSelections {
    implicit val emailPreferenceSelectionsFormat: Format[EmailPreferenceSelections] =
      Format(Json.reads[EmailPreferenceSelections], Json.writes[EmailPreferenceSelections])
  }
}
