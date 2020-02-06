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

package unit.connectors

import java.util.UUID

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import connectors.{ApiSubscriptionFieldsConnector, ProxiedHttpClient}
import domain.ApiSubscriptionFields._
import domain.Environment
import helpers.FutureTimeoutSupportImpl
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import uk.gov.hmrc.http.{BadRequestException, HttpResponse, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ApiSubscriptionFieldsBaseConnectorSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val actorSystem = ActorSystem("test-actor-system")

  private val futureTimeoutSupport = new FutureTimeoutSupportImpl
  private val clientId = UUID.randomUUID().toString
  private val apiContext = "i-am-a-test"
  private val apiVersion = "1.0"
  private val fieldsId = UUID.randomUUID()
  private val urlPrefix = "/field"
  private val upstream500Response = Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)

  trait Setup {
    val apiKey: String = UUID.randomUUID().toString
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
    when(mockAppConfig.retryCount).thenReturn(1)
    when(mockAppConfig.retryDelayMilliseconds).thenReturn(0)
    when(mockAppConfig.apiSubscriptionFieldsSandboxApiKey).thenReturn(apiKey)

    val underTest = new ApiSubscriptionFieldsTestConnector(
      useProxy = false, apiKey = "", mockHttpClient, mockProxiedHttpClient, actorSystem, futureTimeoutSupport, mockAppConfig)
  }

  trait ProxiedSetup extends Setup {

    when(mockProxiedHttpClient.withHeaders(any(), any())).thenReturn(mockProxiedHttpClient)

    override val underTest = new ApiSubscriptionFieldsTestConnector(
      useProxy = true, apiKey, mockHttpClient, mockProxiedHttpClient, actorSystem, futureTimeoutSupport, mockAppConfig)

  }

  class ApiSubscriptionFieldsTestConnector(useProxy: Boolean,
                                           apiKey: String,
                                           val httpClient: HttpClient,
                                           val proxiedHttpClient: ProxiedHttpClient,
                                           val actorSystem: ActorSystem,
                                           val futureTimeout: FutureTimeoutSupport,
                                           val appConfig: ApplicationConfig)(implicit val ec: ExecutionContext)
    extends ApiSubscriptionFieldsConnector(
      Environment.SANDBOX,
      useProxy = useProxy,
      bearerToken = "",
      apiKey = apiKey,
      serviceBaseUrl = "",
      httpClient = httpClient,
      proxiedHttpClient = proxiedHttpClient) {
  }

  private def squidProxyRelatedBadRequest = {
    new BadRequestException(
      "GET of 'https://api.development.tax.service.gov.uk:443/testing/api-subscription-fields/field/application/" +
        "xxxyyyzzz/context/api-platform-test/version/7.0' returned 400 (Bad Request). Response body " +
        "'<html>\n<head><title>400 Bad Request</title></head>\n<body bgcolor=\"white\">\n" +
        "<center><h1>400 Bad Request</h1></center>\n<hr><center>nginx</center>\n</body>\n</html>\n'")
  }

  "fetchFieldValues" should {
    val response = SubscriptionFields(clientId, apiContext, apiVersion, fieldsId, fields("field001" -> "field002"))
    val getUrl = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return subscription fields for an API" in new Setup {
      when(mockHttpClient.GET[SubscriptionFields](meq(getUrl))(any(), any(), any())).thenReturn(Future.successful(response))

      val result: Option[SubscriptionFields] = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))

      result shouldBe Some(response)
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.GET[SubscriptionFields](meq(getUrl))(any(), any(), any()))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
      }
    }

    "return None when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.GET[SubscriptionFields](meq(getUrl))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result: Option[SubscriptionFields] = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
      result shouldBe None
    }

    "when retry logic is enabled should retry if call returns 400 Bad Request" in new Setup {

      when(mockHttpClient.GET[SubscriptionFields](meq(getUrl))(any(), any(), any()))
        .thenReturn(
          Future.failed(squidProxyRelatedBadRequest),
          Future.successful(response)
        )
      val result: Option[SubscriptionFields] = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))

      result shouldBe Some(response)
    }

    "send the x-api-header key when retrieving subscription fields for an API" in new ProxiedSetup {

      when(mockProxiedHttpClient.GET[SubscriptionFields](any())(any(), any(), any())).thenReturn(Future.successful(response))

      await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))

      verify(mockProxiedHttpClient).withHeaders(any(), meq(apiKey))
    }
  }

  "fetchFieldDefinitions" should {

    val fields = List(SubscriptionField("field1", "desc1", "hint1", "some type"), SubscriptionField("field2", "desc2", "hint2", "some other type"))
    val validResponse = FieldDefinitionsResponse(fields, "context", "version")
    val url = s"/definition/context/$apiContext/version/$apiVersion"

    "return subscription fields definition for an API" in new Setup {

      when(mockHttpClient.GET[FieldDefinitionsResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.successful(validResponse))

      val result: Seq[SubscriptionField] = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))

      result shouldBe fields
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.GET[FieldDefinitionsResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      }
    }

    "return empty sequence when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.GET[SubscriptionFields](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result: Seq[SubscriptionField] = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      result shouldBe Seq.empty[SubscriptionField]
    }

    "fail when api-subscription-fields returns unexpected response" in new Setup {

      when(mockHttpClient.GET[FieldDefinitionsResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new JsValidationException("", "", FieldDefinitionsResponse.getClass, "")))

      intercept[JsValidationException] {
        await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      }
    }

    "when retry logic is enabled should retry if call returns 400 Bad Request" in new Setup {
      when(mockHttpClient.GET[FieldDefinitionsResponse](meq(url))(any(), any(), any()))
        .thenReturn(
          Future.failed(new BadRequestException("")),
          Future.successful(validResponse)
        )
      val result: Seq[SubscriptionField] = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))

      result shouldBe fields
    }
  }

  "saveFieldValues" should {

    val fieldsValues = fields("field001" -> "value001", "field002" -> "value002")
    val subFieldsPutRequest = SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fieldsValues)

    val putUrl = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "save the fields" in new Setup {

      when(mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](meq(putUrl), meq(subFieldsPutRequest), any())(any(),any(),any(),any()))
        .thenReturn(Future.successful(HttpResponse(OK)))

      await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))

      verify(mockHttpClient).PUT[SubscriptionFieldsPutRequest, HttpResponse](meq(putUrl), meq(subFieldsPutRequest), any())(any(),any(),any(),any())
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](meq(putUrl), meq(subFieldsPutRequest), any())(any(),any(),any(),any()))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }
    }

    "fail when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](meq(putUrl), meq(subFieldsPutRequest), any())(any(),any(),any(),any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[NotFoundException] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }
    }
  }

  "deleteFieldValues" should {

    val url = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return true after delete call has returned 204 NO CONTENT" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      private val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe true
    }

    "return false if api-subscription-fields returns unexpected status" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(ACCEPTED)))

      private val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe false
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))
      }
    }

    "return true when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.failed(new NotFoundException("")))

      private val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))
      result shouldBe true
    }

  }
}
