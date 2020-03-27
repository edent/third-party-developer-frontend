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

package domain

import java.util.UUID

import play.api.libs.json.{Format, Json}

object ApiSubscriptionFields {

  case class SubscriptionFieldDefinition(
      name: String,
      description: String,
      hint: String,
      `type`: String
  )

  case class SubscriptionFieldValue(definition: SubscriptionFieldDefinition, value: String)

  object SubscriptionFieldValue {
    def fromFormValues(
        name: String,
        description: String,
        hint: String,
        `type`: String,
        value: String
    ) = {
      SubscriptionFieldValue(SubscriptionFieldDefinition(name, description, hint, `type`), value)
    }

    def toFormValues(
        subscriptionFieldValue: SubscriptionFieldValue
    ): Option[(String, String, String, String, String)] = {
      Some(
        (
          subscriptionFieldValue.definition.name,
          subscriptionFieldValue.definition.description,
          subscriptionFieldValue.definition.hint,
          subscriptionFieldValue.definition.`type`,
          subscriptionFieldValue.value
        )
      )
    }
  }

  type Fields = Map[String, String]

  sealed trait FieldsDeleteResult
  case object FieldsDeleteSuccessResult extends FieldsDeleteResult
  case object FieldsDeleteFailureResult extends FieldsDeleteResult

  case class SubscriptionFieldsWrapper(
      applicationId: String,
      clientId: String,
      apiContext: String,
      apiVersion: String,
      fields: Seq[SubscriptionFieldValue]
  )

  // TODO : Probably needs deleting
  object Fields {
    val empty = Map.empty[String, String]
  }

  // TODO : Probably needs deleting
  case class SubscriptionFields(
      clientId: String,
      apiContext: String,
      apiVersion: String,
      fieldsId: UUID,
      fields: Map[String, String]
  )

  object SubscriptionFields {
    implicit val format: Format[SubscriptionFields] = Json.format[SubscriptionFields]
  }

  case class SubscriptionFieldsPutRequest(
      clientId: String,
      apiContext: String,
      apiVersion: String,
      fields: Map[String, String]
  )
  object SubscriptionFieldsPutRequest {
    implicit val format: Format[SubscriptionFieldsPutRequest] =
      Json.format[SubscriptionFieldsPutRequest]
  }
}
