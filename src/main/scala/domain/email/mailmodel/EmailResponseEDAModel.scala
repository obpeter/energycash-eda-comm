package at.energydash
package domain.email.mailmodel

trait EmailResponseEDAModel

final case class EmailSuccessResponseDto(
                                          message: String
                                        ) extends EmailResponseEDAModel

final case class EmailFailedResponseDto(
                                         message: String
                                       ) extends EmailResponseEDAModel
