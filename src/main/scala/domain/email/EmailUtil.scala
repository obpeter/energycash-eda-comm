package at.energydash
package domain.email

import at.energydash.domain.util.Enum.ResponseStatus
import spray.json.JsValue

object EmailUtil {

  case class EmailHttpResponse(
                                status: ResponseStatus.Value,
                                description: String,
                                code: Option[Int]     = None,
                                data: Option[JsValue] = None)

}