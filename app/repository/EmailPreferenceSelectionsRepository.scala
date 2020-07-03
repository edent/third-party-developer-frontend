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
import controllers.EmailPreferenceSelections
import javax.inject.Inject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.BSONObjectID
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
      Index(key = List("email" -> Ascending), name = Some("emailIndex"), unique = true)
  )

  def fetchByEmail(email: String): Future[Option[EmailPreferenceSelections]] =
    find("email" -> email)
      .map(_.headOption)

  def deleteByEmail(email: String): Future[Boolean] =
    remove("email" -> email)
      .map(_.ok)
}
