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

import com.codahale.metrics.SharedMetricRegistries
import config.{ApplicationConfig, ErrorHandler}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.twirl.api.Html
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.SharedMetricsClearDown
import mocks.service.ErrorHandlerMock

import scala.concurrent.ExecutionContext

class BaseControllerSpec extends UnitSpec with MockitoSugar with ScalaFutures with WithFakeApplication with SharedMetricsClearDown with ErrorHandlerMock {

  SharedMetricRegistries.clear()

  implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  implicit val appConfig: ApplicationConfig = mock[ApplicationConfig]

  implicit val cookieSigner: CookieSigner = fakeApplication.injector.instanceOf[CookieSigner]

  implicit lazy val materializer = fakeApplication.materializer

  lazy val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
}
