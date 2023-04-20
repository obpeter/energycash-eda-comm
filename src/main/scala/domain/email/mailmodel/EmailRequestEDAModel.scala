package at.energydash
package domain.email.mailmodel

case class EmailRequestEDAModel(
                            to: String,
                            subject: String,
                            file: String,
                            )
