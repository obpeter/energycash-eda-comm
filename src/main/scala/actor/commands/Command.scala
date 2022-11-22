package at.energydash
package actor.commands

import domain.email.Fetcher.MailMessage

sealed trait Command
sealed trait Response

case class Message[T](value: T) extends Command

case class AckMessage(emailId: String) extends Command
case object Shutdown extends Command

case object Start extends Command

trait EmailCommand extends Command
trait EmailResponse extends Response

case class FetchEmailResponse(mails: List[MailMessage]) extends EmailCommand
case class ErrorResponse(message: String) extends EmailCommand