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

package views

import config.ApplicationConfig
import org.mockito.BDDMockito.given
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits.applicationMessages
import play.twirl.api.Html
import uk.gov.hmrc.play.test.UnitSpec
import utils.SharedMetricsClearDown
import html._

class MainTemplateSpec extends UnitSpec with Matchers with MockitoSugar with OneServerPerSuite with SharedMetricsClearDown {


  "MainTemplateSpec" should {

    implicit val mockConfig = mock[ApplicationConfig]

    "Application title meta data set by configuration" in {
      given(mockConfig.title).willReturn("Application Title")

      val mainView: Html = html.include.main("Test", developerSession = None)()

      mainView.body should include("data-title=\"Application Title")
    }
  }
}
