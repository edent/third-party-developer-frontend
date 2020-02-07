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

package unit.service

import java.util.UUID

import connectors.{ApiSubscriptionFieldsConnector, ThirdPartyApplicationConnector}
import domain.ApiSubscriptionFields.{SubscriptionField, SubscriptionFields, fields}
import domain.{APIIdentifier, Application, Environment}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, anyString, eq => meq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.CREATED
import service.{Connectors, ConnectorsWrapper, SubscriptionFieldsService}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionFieldsServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  val apiContext: String = "sub-ser-test"
  val apiVersion: String = "1.0"
  val applicationName: String = "third-party-application"
  val applicationId: String = "application-id"
  val clientId = "clientId"
  val application = Application(applicationId, clientId, applicationName, DateTime.now(), DateTime.now(), Environment.PRODUCTION)

  trait Setup {

    lazy val locked = false

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockConnectorsWrapper: ConnectorsWrapper = mock[ConnectorsWrapper]
    val mockSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsConnector]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]

    val underTest = new SubscriptionFieldsService(mockConnectorsWrapper)

    given(mockConnectorsWrapper.forApplication(anyString())(any[HeaderCarrier]))
      .willReturn(Future.successful(Connectors(mockThirdPartyApplicationConnector, mockSubscriptionFieldsConnector)))

    given(mockConnectorsWrapper.connectorsForEnvironment(application.deployedTo))
      .willReturn(Connectors(mockThirdPartyApplicationConnector, mockSubscriptionFieldsConnector))

    given(mockThirdPartyApplicationConnector.fetchApplicationById(meq(applicationId))(any[HeaderCarrier]))
      .willReturn(Future.successful(Some(Application(applicationId, clientId, "name", DateTime.now(), DateTime.now(), Environment.PRODUCTION))))
  }

  "fetchFieldsValues" should {
    "return empty sequence when there are none" in new Setup {
      private val subscriptionFieldValues = await(underTest.fetchFieldsValues(application,fieldDefinitions = Seq.empty, APIIdentifier("context", "version-1")))

      subscriptionFieldValues shouldBe Seq.empty
    }

    "find and return matching values" in new Setup {
      private val apiIdentifier: APIIdentifier = APIIdentifier("context1", "version-1")

      private val subscriptionFieldDefinition1 = SubscriptionField("name1", "description1", "hint1", "STRING", None)
      private val subscriptionFieldDefinition2 = SubscriptionField("name2", "description2", "hint2", "STRING", None)

      private val subscriptionFieldValue1: SubscriptionField = subscriptionFieldDefinition1.copy(value = Some("value1"))
      private val subscriptionFieldValue2: SubscriptionField = subscriptionFieldDefinition2.copy(value = Some("value2"))

      val fieldDefinitions = Seq(subscriptionFieldDefinition1, subscriptionFieldDefinition2)

      private val subscriptionFields = SubscriptionFields(
        "clientId1",
        apiIdentifier.context,
        apiIdentifier.version,
        fieldsId = UUID.randomUUID(),
        Map(subscriptionFieldDefinition1.name -> subscriptionFieldValue1.value.get,
          subscriptionFieldDefinition2.name -> subscriptionFieldValue2.value.get))

      given(mockSubscriptionFieldsConnector.fetchFieldValues(meq(application.clientId), meq(apiIdentifier.context), meq(apiIdentifier.version))(any[HeaderCarrier]))
        .willReturn(Future.successful(Some(subscriptionFields)))

      private val subscriptionFieldValues = await(underTest.fetchFieldsValues(application,fieldDefinitions, apiIdentifier))

      subscriptionFieldValues shouldBe Seq(subscriptionFieldValue1, subscriptionFieldValue2)
    }

    "find no matching values" in new Setup {
      private val apiIdentifier: APIIdentifier = APIIdentifier("context1", "version-1")

      private val subscriptionFieldDefinition: SubscriptionField = SubscriptionField("name1", "description1", "hint1", "STRING", None)

      val fieldDefinitions = Seq(subscriptionFieldDefinition)

      given(mockSubscriptionFieldsConnector.fetchFieldValues(meq(application.clientId), meq(apiIdentifier.context), meq(apiIdentifier.version))(any[HeaderCarrier]))
        .willReturn(Future.successful(None))

      private val subscriptionFieldValues = await(underTest.fetchFieldsValues(application,fieldDefinitions, apiIdentifier))

      subscriptionFieldValues shouldBe Seq(subscriptionFieldDefinition)
    }
  }

  "saveFields" should {
    "save the fields" in new Setup {
      private val fieldsId = UUID.randomUUID()
      private val fieldsValues = fields("field1" -> "val001", "field2" -> "val002")
      val fieldValuesResponse: SubscriptionFields = SubscriptionFields(clientId, apiContext, apiVersion, fieldsId, fieldsValues)

      given(mockSubscriptionFieldsConnector.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
        .willReturn(Future.successful(HttpResponse(CREATED)))

      await(underTest.saveFieldValues(applicationId, apiContext, apiVersion, fieldsValues))

      verify(mockSubscriptionFieldsConnector).saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
    }
  }
}
