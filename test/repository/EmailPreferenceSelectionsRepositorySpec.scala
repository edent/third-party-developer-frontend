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

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import controllers.{EmailPreferenceSelections, TaxRegimeServices}
import model.APICategory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, OptionValues, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class EmailPreferenceSelectionsRepositorySpec
  extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with OptionValues
    with MongoSpecSupport
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  implicit var s : ActorSystem = ActorSystem("test")
  implicit var m : Materializer = ActorMaterializer()

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private val emailPreferenceSelectionsRepository = new EmailPreferenceSelectionsRepository(reactiveMongoComponent)

  override def beforeEach() {
    await(emailPreferenceSelectionsRepository.drop)
    await(emailPreferenceSelectionsRepository.ensureIndexes)
  }

  override protected def afterAll() {
    await(emailPreferenceSelectionsRepository.drop)
  }

  "The 'emailPreferenceSelections' collection" should {
    def toIndexComparison(index: Index) =
      Tuple5(index.key, index.name, index.unique, index.background, index.sparse)

    "have all the current indexes" in {
      val expectedIndexes = Set(
        Index(key = List("email" -> Ascending), name = Some("emailIndex"), unique = true)
      )

      val actualIndexes = await(emailPreferenceSelectionsRepository.collection.indexesManager.list()).toSet

      actualIndexes.map(toIndexComparison) should contain allElementsOf expectedIndexes.map(toIndexComparison)
    }
  }

  "fetchByEmail" should {
    "retrieve the matching record if it exists" in {
      val matchingEmail = "foo@bar.com"
      val matchingRecord = EmailPreferenceSelections(matchingEmail, List(TaxRegimeServices(APICategory.CUSTOMS, Set("cds-api-1"))), List.empty, Set.empty)

      await(emailPreferenceSelectionsRepository
        .bulkInsert(
          Seq(
            matchingRecord,
            EmailPreferenceSelections("nonmatching@foo.com", List(TaxRegimeServices(APICategory.CUSTOMS, Set("cds-api-1"))), List.empty, Set.empty))))

      val retrievedRecord = await(emailPreferenceSelectionsRepository.fetchByEmail(matchingEmail))

      retrievedRecord.isDefined should be (true)
      retrievedRecord.get should be (matchingRecord)
    }

    "return None if record does not exists" in {
      val retrievedRecord = await(emailPreferenceSelectionsRepository.fetchByEmail("nonmatching@foo.com"))

      retrievedRecord.isDefined should be (false)
    }
  }
}
