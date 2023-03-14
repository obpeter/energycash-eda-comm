package at.energydash.actor
package commands

import at.energydash.domain.email.Fetcher.MailMessage

sealed trait Command
trait Response

case class Message[T](value: T) extends Command

case class AckMessage(emailId: String) extends Command
case object Shutdown extends Command

case object Start extends Command

trait EmailCommand extends Command
trait EmailResponse extends Response

case class ErrorResponse(message: String) extends EmailCommand