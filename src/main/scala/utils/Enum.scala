package at.energydash
package utils

object Enum {

  object ResponseStatus extends Enumeration {

    val NotFound = Value(404)

    val DuplicateRequest = Value(205)
    val Success          = Value(200)
    val Created          = Value(201)
    val BadRequest       = Value(400)
  }
}