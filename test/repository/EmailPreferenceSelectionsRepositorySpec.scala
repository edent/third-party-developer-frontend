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
import controllers.UserEmailPreferences
import model.APICategory
import model.APICategory.{CUSTOMS, VAT_MTD, AGENTS}
import org.joda.time.DateTime
import org.scalatest._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDocument, BSONLong}
import repository.EmailPreferenceSelectionsRepository.{EmailPreferenceSelections, TaxRegimeServices}
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

  private val repositoryUnderTest = new EmailPreferenceSelectionsRepository(reactiveMongoComponent)

  override def beforeEach() {
    await(repositoryUnderTest.drop)
    await(repositoryUnderTest.ensureIndexes)
  }

  override protected def afterAll() {
    await(repositoryUnderTest.drop)
  }

  "The 'emailPreferenceSelections' collection" should {
    def toIndexComparison(index: Index) =
      Tuple5(index.key, index.name, index.unique, index.background, index.sparse)

    "have all the current indexes" in {
      val expectedIndexes = Set(
        Index(key = List("email" -> Ascending), name = Some("emailIndex"), unique = true),
        Index(
          key = List("lastUpdate" -> Ascending),
          name = Some("expiryIndex"),
          background = true,
          options = BSONDocument("expireAfterSeconds" -> BSONLong(1800)))
      )

      val actualIndexes = await(repositoryUnderTest.collection.indexesManager.list()).toSet

      actualIndexes.map(toIndexComparison) should contain allElementsOf expectedIndexes.map(toIndexComparison)
    }
  }

  "fetchByEmail" should {

    "retrieve the matching record if it exists" in {
      val matchingEmail = "foo@bar.com"
      val matchingRecord = newRecord(matchingEmail, List(TaxRegimeServices(CUSTOMS, Set("cds-api-1"))))

      await(repositoryUnderTest.bulkInsert(Seq(matchingRecord, newRecord("nonmatching@foo.com"))))

      val retrievedRecord: Option[UserEmailPreferences] = await(repositoryUnderTest.fetchByEmail(matchingEmail))

      retrievedRecord.isDefined should be (true)
      retrievedRecord.get.servicesAvailableToUser.keys should contain only CUSTOMS
      retrievedRecord.get.servicesAvailableToUser(CUSTOMS) should contain only "cds-api-1"
    }

    "return None if record does not exists" in {
      val retrievedRecord = await(repositoryUnderTest.fetchByEmail("nonmatching@foo.com"))

      retrievedRecord.isDefined should be (false)
    }
  }

  "deleteByEmail" should {
    "remove record and return true on successful deletion" in {
      val matchingEmail = "foo@bar.com"
      val matchingRecord = newRecord(matchingEmail, List(TaxRegimeServices(CUSTOMS, Set("cds-api-1"))))

      await(repositoryUnderTest.bulkInsert(Seq(matchingRecord, newRecord("nonmatching@foo.com"))))

      val result = await(repositoryUnderTest.deleteByEmail(matchingEmail))

      result should be (true)
      await(repositoryUnderTest.fetchByEmail(matchingEmail)) should be (None)
    }

    "return true if record does not exist" in {
      val result = await(repositoryUnderTest.deleteByEmail("nonmatching@foo.com"))

      result should be (true)
    }
  }

  "removeSelectedTaxRegimes" should {
    "remove specified tax regimes from selected options" in {
      val matchingEmail = "foo@bar.com"
      val matchingRecord =
        newRecord(
          matchingEmail,
          servicesSelected = List(TaxRegimeServices(CUSTOMS, Set("cds-api-1")), TaxRegimeServices(VAT_MTD, Set("vat-mtd-api-1"))),
          lastUpdate = DateTime.now.minusDays(1))

      await(repositoryUnderTest.insert(matchingRecord))

      val result: UserEmailPreferences = await(repositoryUnderTest.removeSelectedTaxRegimes(matchingEmail, Set(VAT_MTD)))

      result.servicesSelected.size should be (1)
      result.servicesSelected.head._1 should be(CUSTOMS)
    }
  }

  "addSelectedTaxRegimes" should {
    "add specified tax regimes to selected options" in {
      val matchingEmail = "foo@bar.com"
      val matchingRecord =
        newRecord(
          matchingEmail,
          servicesSelected = List(TaxRegimeServices(CUSTOMS, Set("cds-api-1"))),
          lastUpdate = DateTime.now.minusDays(1))

      await(repositoryUnderTest.insert(matchingRecord))

      val result: UserEmailPreferences = await(repositoryUnderTest.addSelectedTaxRegimes(matchingEmail, Set(VAT_MTD, AGENTS)))

      result.servicesSelected.size should be (3)
      result.servicesSelected.find(_._1 == CUSTOMS).head._2 should be (Set("cds-api-1"))
      result.servicesSelected.find(_._1 == VAT_MTD).head._2 should be (Set.empty)
      result.servicesSelected.find(_._1 == AGENTS).head._2 should be (Set.empty)
    }
  }

  private[repository] def newRecord(email:String,
                servicesAvailableToUser: List[TaxRegimeServices] = List.empty,
                servicesSelected: List[TaxRegimeServices] = List.empty,
                topicsSelected: Set[String] = Set.empty,
                lastUpdate: DateTime = DateTime.now): EmailPreferenceSelections =
    EmailPreferenceSelections(email, servicesAvailableToUser, servicesSelected, topicsSelected, lastUpdate)
}
