package at.energydash
package domain.email.model

case class EmailRequestEDAModel(
                            to: String,
                            subject: String,
                            file: String,
                            )
