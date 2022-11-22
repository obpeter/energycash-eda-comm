package at.energydash
package domain.email.model

trait EmailResponseEDAModel

final case class EmailSuccessResponseDto(
                                          message: String
                                        ) extends EmailResponseEDAModel

final case class EmailFailedResponseDto(
                                         message: String
                                       ) extends EmailResponseEDAModel
