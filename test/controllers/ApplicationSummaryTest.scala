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

import domain.Role.DEVELOPER
import domain.State.TESTING
import domain._
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}

class ApplicationSummaryTest extends WordSpec with Matchers {

  "noProductionApplications" should {
    val sandboxApp =
      ApplicationSummary(
        "", "", "Sandbox", DEVELOPER, TermsOfUseStatus.AGREED, TESTING, new DateTime(), serverTokenUsed = false, new DateTime(), AccessType.STANDARD)
    val productionApp =
      ApplicationSummary(
        "", "", "Production", DEVELOPER, TermsOfUseStatus.AGREED, TESTING, new DateTime(), serverTokenUsed = false, new DateTime(), AccessType.STANDARD)

    "return true if only sandbox apps" in {
      val apps = Seq(sandboxApp)

      ApplicationSummary.noProductionApplications(apps) shouldBe true
    }

    "return false if there is a production app" in {
      val apps = Seq(productionApp, sandboxApp)

      ApplicationSummary.noProductionApplications(apps) shouldBe false
    }
  }

  "from" should {
    val user = new Collaborator("foo@bar.com", DEVELOPER)

    val serverTokenApplication = new Application("", "", "", DateTime.now, DateTime.now, Some(DateTime.now), Environment.PRODUCTION, collaborators = Set(user))
    val noServerTokenApplication = new Application("", "", "", DateTime.now, DateTime.now, None, Environment.PRODUCTION, collaborators = Set(user))

    "set serverTokenUsed if Application has a date set for lastAccessTokenUsage" in {
      val summary = ApplicationSummary.from(serverTokenApplication, user.emailAddress)

      summary.serverTokenUsed should be (true)
    }

    "not set serverTokenUsed if Application does not have a date set for lastAccessTokenUsage" in {
      val summary = ApplicationSummary.from(noServerTokenApplication, user.emailAddress)

      summary.serverTokenUsed should be (false)
    }
  }
}
