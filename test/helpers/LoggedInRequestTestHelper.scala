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

package helpers

import mocks.service.SessionServiceMock
import controllers.BaseControllerSpec
import uk.gov.hmrc.play.test.WithFakeApplication
import security.DevHubAuthorization
import domain.Developer
import service.SessionService
import domain.LoggedInState
import domain.Session
import play.filters.csrf.CSRF.TokenProvider
import play.api.test.FakeRequest
import play.api.mvc.AnyContentAsEmpty
import utils.WithLoggedInSession._

trait LoggedInRequestTestHelper extends SessionServiceMock with WithFakeApplication with DevHubAuthorization {
  this: BaseControllerSpec =>    
    val sessionService = mock[SessionService]

    val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    fetchSessionByIdReturns(sessionId, session)
   
    private val sessionParams = Seq(
      "csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken
    )

    lazy val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(this, implicitly)(sessionId)
      .withSession(sessionParams: _*)
}
