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

package connectors

import connectors.DevhubAccessRequirement.NoOne
import connectors.SubscriptionFieldsConnector.{ApiFieldDefinitions, FieldDefinition}
import play.api.libs.json.{Format, JsSuccess, Json}
import uk.gov.hmrc.play.test.UnitSpec

class ApiFieldDefinitionsSpec extends UnitSpec {

  private def basicFieldDefinitionJson =
    """{
       |    "apiContext": "my-context",
       |    "apiVersion": "1.0",
       |    "fieldDefinitions": [
       |        {
       |            "name": "field-name",
       |            "description": "my-description",
       |            "hint": "my-hint",
       |            "type": "STRING",
       |            "shortDescription": "my-shortDescription",
       |            "access": {}
       |        }
       |    ]
       |}""".stripMargin

  private val basicFieldDefinition: ApiFieldDefinitions = {
    ApiFieldDefinitions("my-context", "1.0", List(FieldDefinition("field-name",
      "my-description",
      "my-shortDescription",
      "my-hint",
      "STRING", AccessRequirements.Default)))
  }

  private def fieldDefinitionWithAccessJson =
    """{
      |    "apiContext": "my-context",
      |    "apiVersion": "1.0",
      |    "fieldDefinitions": [
      |        {
      |            "name": "field-name",
      |            "description": "my-description",
      |            "hint": "my-hint",
      |            "type": "STRING",
      |            "shortDescription": "my-shortDescription",
      |            "access": {
      |                "devhub": {
      |                    "read": "noOne",
      |                    "write": "noOne"
      |                }
      |            }
      |        }
      |    ]
      |}""".stripMargin

  "from json" should {
    import SubscriptionFieldsConnector.JsonFormatters._
    "for basic field definition" in {
      Json.fromJson[ApiFieldDefinitions](Json.parse(basicFieldDefinitionJson)) shouldBe JsSuccess(basicFieldDefinition)
    }

    // TODO: Need to make this red.
    "for field definition with access" in {
      val apiFieldDefinitionsWithAccess: ApiFieldDefinitions = {
        ApiFieldDefinitions("my-context", "1.0", List(FieldDefinition("field-name",
          "my-description",
          "my-shortDescription",
          "my-hint",
          "STRING",
          access = AccessRequirements(devhub = DevhubAccessRequirements(NoOne, NoOne)))))
      }

      Json.fromJson[ApiFieldDefinitions](Json.parse(fieldDefinitionWithAccessJson)) shouldBe JsSuccess(apiFieldDefinitionsWithAccess)
    }
  }
}
