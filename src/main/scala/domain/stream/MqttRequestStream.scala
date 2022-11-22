package at.energydash
package domain.stream

import akka.actor.typed.scaladsl.ActorContext
import at.energydash.domain.email.EmailService
import at.energydash.domain.eda.message.MessageHelper
import at.energydash.model.EbMsMessage
import at.energydash.actor.{MessageStorage, PrepareMessageActor}
import akka.{Done, NotUsed}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.alpakka.mqtt.MqttMessage
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.{ByteString, Timeout}
import at.energydash.actor.commands.Command
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.Future

class MqttRequestStream(mailService: ActorRef[EmailService.Command],
                             messageTransformer: ActorRef[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]],
                             messageStore: ActorRef[MessageStorage.Command[MessageStorage.AddMessageResult]])
                            (implicit system: ActorSystem[_], timeout: Timeout){
  import at.energydash.model.JsonImplicit._

  val decodingFlow: Flow[String, Either[MqttMessage, EbMsMessage], NotUsed] =
    Flow.fromFunction(msg => decode[EbMsMessage](msg).left.map(error =>
      MqttMessage("response", ByteString(EmailService.ErrorResponse(error.getMessage).asJson.toString().strip()))
    ))

  val prepareMessageFlow: Flow[EbMsMessage, EbMsMessage, NotUsed] =
    ActorFlow.ask(messageTransformer)((m: EbMsMessage, replyTo: ActorRef[PrepareMessageActor.CommandReply]) =>
      PrepareMessageActor.PrepareMessage(m, replyTo)
    ).collect {
      case PrepareMessageActor.Prepared(message) => message
    }

  val storeMessageFlow: Flow[EbMsMessage, MqttMessage, NotUsed] =
    ActorFlow.ask(messageStore)(MessageStorage.AddMessage).collect {
      case MessageStorage.Added(id) => MqttMessage("response", ByteString(id.asJson.toString()))
    }

  val prepareEmailMessageFlow: Flow[EbMsMessage, EmailService.EmailModel, NotUsed] =
    Flow.fromFunction(data => {
      val attachment = MessageHelper.getEdaMessageByType(data).toByte
      val subject = s"[${data.messageCode} MessageId=${data.messageId}]"
      val to = s"${data.receiver}@gmail.com"
      val from = s"${data.sender}@gmail.com"

      EmailService.EmailModel(toEmail = to,
        subject = subject, attachment = attachment, data = data)
    })

  def run( source: Source[MqttMessage, Future[Done]],
           responseSink: Sink[MqttMessage, Future[Done]],
           errorSink: Sink[MqttMessage, Future[Done]],
         ) = {

    val invalidEventsCommitter: Sink[Either[MqttMessage, _], NotUsed] =
      Flow[Either[MqttMessage, _]]
        .collect { case Left(error) => error }
        .to(errorSink)

    source
      .map(msg => (msg.payload.utf8String))
      .via(decodingFlow.divertTo(invalidEventsCommitter, _.isLeft).collect { case Right(m) => m })
      .via(prepareMessageFlow)
      .via(prepareEmailMessageFlow)
      .via(
        ActorFlow.ask(parallelism = 1)(mailService)((msg: EmailService.EmailModel, replyTo: ActorRef[EmailService.Response]) =>
          EmailService.SendEmailCommand(msg, replyTo)).collect {
          case EmailService.SendEmailResponse(msg) => msg
        }
      )
      .via(storeMessageFlow)
      .to(responseSink)
      .run()
  }
}


object MqttRequestStream {
  def apply(mailService: ActorRef[EmailService.Command],
            messageTransformer: ActorRef[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]],
            messageStore: ActorRef[MessageStorage.Command[MessageStorage.AddMessageResult]])
           (implicit system: ActorSystem[_], timeout: Timeout):MqttRequestStream = {
    new MqttRequestStream(mailService, messageTransformer, messageStore)
  }
}
