/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.controllers


import controllers.SpliceCSRFToken
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.typedmap.TypedKey
import play.api.mvc.{Call, RequestHeader}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class SpliceCSRFTokenSpec extends UnitSpec with MockitoSugar {

  val nameRequestTag: TypedKey[String] = TypedKey("CSRF_TOKEN_NAME")
  val reSignedRequestTag: TypedKey[String] = TypedKey("CSRF_TOKEN")
  val requestTag: TypedKey[String] = TypedKey("CSRF_TOKEN_RE_SIGNED")

  trait SetupWithCSRFToken {
    implicit val rh = mock[RequestHeader]
    when(rh.attrs.get(nameRequestTag)).thenReturn(Some("csrfToken"))
    when(rh.attrs.get(requestTag)).thenReturn(Some("token"))
  }

  trait SetupWithReSignedCSRFToken extends SetupWithCSRFToken {
    when(rh.attrs.get(reSignedRequestTag)).thenReturn(Some("resigned"))
  }

  "SpliceCSRFTokenSpec" should {

    "fail if no CSRF token in scope" in {
      implicit val request = FakeRequest()
      val caught = intercept[RuntimeException] {
        SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd"))
      }
      caught.getMessage shouldBe "No CSRF token present!"
    }

    "insert a CSRF token to a call at the start of the query string" in new SetupWithCSRFToken {
      val call = SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd?parameter=efgh"))
      call.url shouldBe "https://example.com/abcd?csrfToken=token&parameter=efgh"
    }

    "add a CSRF token to a call with no query params" in new SetupWithCSRFToken {
      val call = SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd"))
      call.url shouldBe "https://example.com/abcd?csrfToken=token"
    }

    "add a CSRF token (preferring re-signed request tag) to a call with no query params" in new SetupWithReSignedCSRFToken {
      val call = SpliceCSRFToken(Call(method = "POST", url = "https://example.com/abcd"))
      call.url shouldBe "https://example.com/abcd?csrfToken=resigned"
    }
  }
}
