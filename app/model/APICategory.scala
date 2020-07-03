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

package model

object APICategory extends Enumeration {
  type APICategory = Value

  protected case class Val(displayName: String) extends super.Val
  implicit def valueToAPICategoryVal(x: Value): Val = x.asInstanceOf[Val]

  val EXAMPLE = Val("Example")
  val AGENTS = Val("Agents")
  val BUSINESS_RATES = Val("Business Rates")
  val CHARITIES = Val("Charities")
  val CONSTRUCTION_INDUSTRY_SCHEME = Val("Construction Industry Scheme")
  val CORPORATION_TAX = Val("Corporation Tax")
  val CUSTOMS = Val("Customs")
  val ESTATES = Val("Estates")
  val HELP_TO_SAVE = Val("Help to Save")
  val INCOME_TAX_MTD = Val("Income Tax (Making Tax Digital)")
  val LIFETIME_ISA = Val("Lifetime ISA")
  val MARRIAGE_ALLOWANCE = Val("Marriage Allowance")
  val NATIONAL_INSURANCE = Val("National Insurance")
  val PAYE = Val("PAYE")
  val PENSIONS = Val("Pensions")
  val PRIVATE_GOVERNMENT = Val("Private Government")
  val RELIEF_AT_SOURCE = Val("Relief at Source")
  val SELF_ASSESSMENT = Val("Self Assessment")
  val STAMP_DUTY = Val("Stamp Duty")
  val TRUSTS = Val("Trusts")
  val VAT_MTD = Val("VAT (Making Tax Digital)")
  val VAT = Val("VAT")

  val OTHER = Val("Other")

}