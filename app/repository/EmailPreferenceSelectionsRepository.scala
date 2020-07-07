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
import controllers.UserEmailPreferences
import javax.inject.{Inject, Singleton}
import model.APICategory
import model.APICategory.APICategory
import org.joda.time.DateTime
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import repository.EmailPreferenceSelectionsRepository.{EmailPreferenceSelections, TaxRegimeServices, fromModel, toModel}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
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

  def save(email: String, userEmailPreferences: UserEmailPreferences): Future[Boolean] =
    insert(fromModel(email, userEmailPreferences))
      .map(_.ok)

  def fetchByEmail(email: String): Future[Option[UserEmailPreferences]] =
    find("email" -> email)
      .map(_.headOption)
      .map {
        case Some(selections) => Some(toModel(selections))
        case _ => None
      }

  def deleteByEmail(email: String): Future[Boolean] =
    remove("email" -> email)
      .map(_.ok)

  def addSelectedTaxRegimes(email: String, regimesToAdd: Set[APICategory]): Future[UserEmailPreferences] = {
    val newRegimeObjects = regimesToAdd.map(regime => Json.obj("taxRegime" -> regime.toString, "services" -> Json.arr()))

    val updates =
      Json.obj(
        "$push" ->
          Json.obj("servicesSelected" ->
            Json.obj("$each" ->
              Json.arr(newRegimeObjects.toSeq:_*))))

    findAndUpdate(Json.obj("email" -> email), updates, fetchNewObject = true)
      .map(_.result[EmailPreferenceSelections].head)
      .map(toModel)
  }

  def removeSelectedTaxRegimes(email: String, regimesToRemove: Set[APICategory]): Future[UserEmailPreferences] = {
    val update =
      Json.obj(
        "$pull" -> Json.obj("servicesSelected" -> Json.obj("taxRegime" -> Json.obj("$in" -> Json.arr(regimesToRemove.map(_.toString).mkString(","))))),
        "$currentDate" -> Json.obj("lastUpdate" -> true))

    findAndUpdate(Json.obj("email" -> email), update, fetchNewObject = true)
      .map(_.result[EmailPreferenceSelections].head)
      .map(toModel)
  }
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
    implicit val dateTimeFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
    implicit val emailPreferenceSelectionsFormat: Format[EmailPreferenceSelections] =
      Format(Json.reads[EmailPreferenceSelections], Json.writes[EmailPreferenceSelections])
  }

  def toModel: EmailPreferenceSelections => UserEmailPreferences = { emailPreferencesSelection =>
    def regimesAndServicesAsMap(regimesAndServices: List[TaxRegimeServices]): Map[APICategory, Set[String]] =
      regimesAndServices
        .map(t => (t.taxRegime, t.services))
        .toMap

    UserEmailPreferences(
      regimesAndServicesAsMap(emailPreferencesSelection.servicesAvailableToUser),
      regimesAndServicesAsMap(emailPreferencesSelection.servicesSelected),
      emailPreferencesSelection.topicsSelected)
  }

  def fromModel(userEmail: String, userEmailPreferences: UserEmailPreferences): EmailPreferenceSelections = {
    def regimesAndServicesAsList(regimesAndServices: Map[APICategory, Set[String]]): List[TaxRegimeServices] =
      regimesAndServices
        .map(t => TaxRegimeServices(t._1, t._2))
        .toList

    EmailPreferenceSelections(
      userEmail,
      regimesAndServicesAsList(userEmailPreferences.servicesAvailableToUser),
      regimesAndServicesAsList(userEmailPreferences.servicesSelected),
      userEmailPreferences.topicsSelected,
      DateTime.now)
  }
}
