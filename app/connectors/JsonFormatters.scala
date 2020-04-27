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

import connectors.SubscriptionFieldsConnector.{ApiFieldDefinitions, FieldDefinition}
import cats.data.{NonEmptyList => NEL}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper
import cats.data.{NonEmptyList => NEL}
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper

//trait NonEmptyListFormatters {
//
//  implicit def nelReads[A](implicit r: Reads[A]): Reads[NEL[A]] =
//    Reads
//      .of[List[A]]
//      .collect(
//          ValidationError("expected a NonEmptyList but got an empty list")
//      ) {
//        case head :: tail => NEL(head, tail)
//      }
//
//  implicit def nelWrites[A](implicit w: Writes[A]): Writes[NEL[A]] =
//    Writes
//      .of[List[A]]
//      .contramap(_.toList)
//}

//trait SeqFormatters {
//
//  implicit def nelReads[A](implicit r: Reads[A]): Reads[Seq[A]] =
//    Reads
//      .of[Seq[A]]
//      .collect(
//        ValidationError("expected a NonEmptyList but got an empty list")
//      ) {
//        case head :: tail => Seq(head, tail)
//      }
//
////  implicit def nelWrites[A](implicit w: Writes[A]): Writes[NEL[A]] =
////    Writes
////      .of[List[A]]
////      .contramap(_.toList)
//}

trait FieldDefinitionFormatters extends AccessRequirementsFormatters{

  implicit val FieldDefinitionReads: Reads[FieldDefinition] = (
  (JsPath \ "name").read[String] and
  (JsPath \ "description").read[String] and
  ((JsPath \ "hint").read[String] or Reads.pure("")) and
    // TODO: Use enums from api-subs-fields
//  (JsPath \ "type").read[FieldDefinitionType] and
  (JsPath \ "type").read[String] and
  ((JsPath \ "shortDescription").read[String] or Reads.pure("")) and
//  (JsPath \ "validation").readNullable[ValidationGroup] and
  ((JsPath \ "access").read[AccessRequirements] or Reads.pure(AccessRequirements.Default))
  )(FieldDefinition.apply _)


  implicit val FieldDefinitionListReads: Reads[ApiFieldDefinitions] = (
    (JsPath \ "apiContext").read[String] and
      (JsPath \ "apiVersion").read[String] and
      (JsPath \ "fieldDefinitions").read[Set[ApiFieldDefinitions]]
    )(ApiFieldDefinitions.apply _)
}

trait AccessRequirementsFormatters {
  import DevhubAccessRequirement._

  def ignoreDefaultField[T](value: T, default: T, jsonFieldName: String)(implicit w: Writes[T]) =
    if(value == default) None else Some((jsonFieldName, Json.toJsFieldJsValueWrapper(value)))

  implicit val DevhubAccessRequirementFormat: Format[DevhubAccessRequirement] = new Format[DevhubAccessRequirement] {

    override def writes(o: DevhubAccessRequirement): JsValue = JsString(o match {
      case AdminOnly => "adminOnly"
      case Anyone => "anyone"
      case NoOne => "noOne"
    })

    override def reads(json: JsValue): JsResult[DevhubAccessRequirement] = json match {
      case JsString("adminOnly") => JsSuccess(AdminOnly)
      case JsString("anyone") => JsSuccess(Anyone)
      case JsString("noOne") => JsSuccess(NoOne)
      case _ => JsError("Not a recognized DevhubAccessRequirement")
    }
  }

  implicit val DevhubAccessRequirementsReads: Reads[DevhubAccessRequirements] = (
    ((JsPath \ "read").read[DevhubAccessRequirement] or Reads.pure(DevhubAccessRequirement.Default)) and
      ((JsPath \ "write").read[DevhubAccessRequirement] or Reads.pure(DevhubAccessRequirement.Default))
    )(DevhubAccessRequirements.apply _)

  implicit val DevhubAccessRequirementsWrites: OWrites[DevhubAccessRequirements] = new OWrites[DevhubAccessRequirements] {
    def writes(requirements: DevhubAccessRequirements) = {
      Json.obj(
        (
          ignoreDefaultField(requirements.read, DevhubAccessRequirement.Default, "read") ::
            ignoreDefaultField(requirements.write, DevhubAccessRequirement.Default, "write") ::
            List.empty[Option[(String, JsValueWrapper)]]
          ).filterNot(_.isEmpty).map(_.get): _*
      )
    }
  }

  implicit val AccessRequirementsReads: Reads[AccessRequirements] = Json.reads[AccessRequirements]

  implicit val AccessRequirementsWrites: Writes[AccessRequirements] = Json.writes[AccessRequirements]
}