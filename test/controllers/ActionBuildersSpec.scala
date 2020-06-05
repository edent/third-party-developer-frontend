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

import uk.gov.hmrc.play.test.UnitSpec
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.WithFakeApplication
import config.ApplicationConfig
import service.SessionService
import config.ErrorHandler
import play.api.http.Status.{OK, NOT_FOUND}
// import uk.gov.hmrc.play.test.status
import play.api.test.Helpers._
import play.api.test.FakeRequest
import service.ApplicationService

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.mvc.Result
import play.api.mvc.AnyContent
import play.api.mvc.Action
import play.api.mvc.Request
import play.api.libs.crypto.CookieSigner
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext

class ActionBuildersSpec extends UnitSpec with MockitoSugar with WithFakeApplication{
  trait Setup {
    
    implicit var playApplication = fakeApplication
    
    val underTest = new ApplicationController {
      val errorHandler: ErrorHandler = fakeApplication.injector.instanceOf[ErrorHandler]
      val applicationService: ApplicationService = mock[ApplicationService]
      val appConfig: ApplicationConfig = mock[ApplicationConfig]
      val cookieSigner: CookieSigner = fakeApplication.injector.instanceOf[CookieSigner]
      lazy val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      implicit val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]
      val sessionService: SessionService =  mock[SessionService]

      def stuff(applicationId: String, apiContext: String, apiVersion: String) : Action[AnyContent] = {
        subFieldsDefinitionsExistActionByApi(applicationId, apiContext, apiVersion) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
          ???
        }
      }
    }  
  }
  
  "subscriptionFieldsRefiner" should {
    "Not found" in new Setup {
      
      //val loggedInRequest: FakeRequest = ???

      val request: Request[AnyContent] = ???
      
      val result: Result = await(underTest.stuff("appId", "context", "verion")(request))

      status(result) shouldBe NOT_FOUND
    }

    "Not found2" in new Setup {
      
      //val loggedInRequest: FakeRequest = ???

      val request: Request[AnyContent] = ???
      
      val result = await(underTest.subFieldsDefinitionsExistActionByApi("appId", "context", "version"){ 
        definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
        //  Future.successful(Ok(""))
          ???
        }(request))
      //val result: Result = await(underTest.stuff("appId", "context", "verion")(request))

      status(result) shouldBe NOT_FOUND
    }
    

    "Fond one" in {

    }
    "More than one errors" in {

    }
  }
}
