package at.energydash
package mqtt

import config.Config.MqttConfig
import model.EbMsMessage
import mqtt.MqttProtocol._
import mqtt.path.MqttPaths
import utils.ActorContextImplicits

import akka.Done
import akka.actor.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{Behavior, SupervisorStrategy, ActorRef => TypedActorRef}
import akka.stream.alpakka.mqtt.scaladsl.{MqttFlow, MqttMessageWithAck}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorAttributes, CompletionStrategy, OverflowStrategy, Supervision}
import akka.util.ByteString
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.Logger

import javax.net.ssl.SSLContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait EdaEvent {
  val protocol: String
  val message: EbMsMessage
}

case class CommandMessage (
  val tenant: String,
  val command: String,
  val payload: Json,
)

case class MqttBaseTopicProvider(base: String)

object MqttProtocol {

  sealed trait MqttCmd

  sealed trait MqttMessageCmd extends MqttCmd {
    val ack: Option[() => Future[Done]]
  }

  case class EdaInboundMessage(protocol: String, message: EbMsMessage) extends EdaEvent

  case class EdaEventReceived(ev: EdaEvent, ack: Option[() => Future[Done]]) extends MqttMessageCmd

  case class EdaMessageCommand(command: CommandMessage, ack: Option[() => Future[Done]]) extends MqttMessageCmd

  case object MQTTConnected extends MqttCmd

  case object StreamCompleteInmediate extends MqttCmd

  case class TerminatedWithError(err: Throwable) extends MqttCmd

  case class CamFinished(id: String) extends MqttCmd

  case object TerminatedOk extends MqttCmd

  case object Terminate extends MqttCmd

}
object MqttSystem extends ActorContextImplicits with MqttPaths {
  import model.JsonImplicit._
  def apply(cfg: MqttConfig): Behavior[MqttCmd] = {
    val baseTopic = cfg.base_name.getOrElse("eda2mqtt")
    Behaviors.supervise[MqttCmd] {
      Behaviors.setup { implicit context =>

        context.setLoggerName(MqttSystem.getClass)
        context.log.info(s"starting MQTT client...")

//        val act = context.messageAdapter[EdaEvent](ev => EdaEventReceived(ev, None))

        val src = mqttStreamSource(baseTopic, connectionSettings(cfg))(context.log)
        val ((mqttStream, subscribed), sinkResult) = src.run()
        // add mqtt stream event handlers
        sinkResult.recover {
          case e: Throwable =>
            context.self ! TerminatedWithError(e)
            throw e
        }
        subscribed.map { _ =>
          context.self ! MQTTConnected
        }

        Behaviors.receiveMessagePartial[MqttCmd] {
          case MQTTConnected =>
            context.log.info("mqtt client successfully connected")
            connected(mqttStream)
          case TerminatedWithError(err) =>
            context.log.error("mqtt client connection error", err)
            // stream already failed. just force restart through supervisor
            throw err
          case Terminate =>
            mqttStream ! Terminate
            terminatingOk(context)
        }
      }
    }.onFailure(SupervisorStrategy.restartWithBackoff(minBackoff = 4.seconds, maxBackoff = 1.minute, randomFactor = 0.2))
  }

  private def connected(mqttStream: ActorRef): Behavior[MqttCmd] = {
    Behaviors.setup { implicit context =>
      Behaviors.receiveMessagePartial {
        case ev: EdaEventReceived =>
          mqttStream ! ev
          Behaviors.same
        case cmd: EdaMessageCommand =>
          mqttStream ! cmd
          Behaviors.same
        case TerminatedWithError(err) =>
          context.log.error("mqtt client connection error", err)
          // stream already failed. just force restart through supervisor
          throw err
        case Terminate =>
          mqttStream ! Terminate
          terminatingOk(context)
        case TerminatedOk =>
          Behaviors.same
      }
    }
  }

  private def terminatingOk(context: ActorContext[MqttCmd]): Behavior[MqttCmd] =
    Behaviors.receiveMessagePartial {
      case TerminatedOk =>
        Behaviors.stopped
      case TerminatedWithError(err) =>
        context.log.error("mqtt client connection error", err)
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }

  private def mqttStreamSource(baseTopic: String, settings: MqttConnectionSettings)(implicit logger: Logger): RunnableGraph[((ActorRef, Future[Done]), Future[Done])] = {
    val mqttFlow: Flow[MqttMessageWithAck, MqttMessageWithAck, Future[Done]] =
      MqttFlow.atLeastOnceWithAck(
        settings,
        MqttSubscriptions.empty,
        bufferSize = 16,
        MqttQoS.AtLeastOnce
      )
    val alwaysStop: Supervision.Decider = _ => Supervision.Stop
    val stratAlwaysStop = ActorAttributes.supervisionStrategy(alwaysStop)

    implicit val baseTopicProv: MqttBaseTopicProvider = MqttBaseTopicProvider(baseTopic)

    val compStage: PartialFunction[Any, CompletionStrategy] = {
      case Terminate => CompletionStrategy.immediately
    }
    val failMatcher: PartialFunction[Any, Throwable] = {
      case Terminate => new Exception("termination request")
    }

    Source
      .actorRef[MqttMessageCmd](compStage, failMatcher, 2000, OverflowStrategy.dropTail)
      .map { edaEv =>
        event2Mqtt(edaEv) match {
          case Some(e) if edaEv.ack.isDefined =>
            Some(e.withAck(edaEv.ack.get))
          case Some(e) =>
            Some(e.withoutAck())
          case _ => None
        }
      }
      .filter(_.isDefined)
      .map(_.get)
      .log("MQTT SERVER", x => {
        logger.info(s"Send MQTT Message to ${x.message.topic}")
      })
      .viaMat(mqttFlow)(Keep.both)
      .toMat(Sink.ignore)(Keep.both)
      .withAttributes(stratAlwaysStop)
  }

  private def connectionSettings(cfg: MqttConfig): MqttConnectionSettings = {
    Option(MqttConnectionSettings(
      s"${if (cfg.ssl) "ssl" else "tcp"}://${cfg.host}:${cfg.port}",
      s"eda2mqtt-client-${System.currentTimeMillis()}",
      new MemoryPersistence()
    ).withAutomaticReconnect(false)
      .withCleanSession(false)).map { v =>
      if (cfg.ssl) v.withSocketFactory(SSLContext.getDefault.getSocketFactory) else v
    }.get
  }

  private def event2Mqtt(ev: MqttMessageCmd)(implicit _btp: MqttBaseTopicProvider): Option[MqttMessage] = (ev match {
    // transform some events?
    case ev => moduleEvent2Mqtt(ev)
  }).map(ev => ev.withTopic(s"${_btp.base}/${ev.topic}")) // prepend base topic

  private def moduleEvent2Mqtt(ev: MqttMessageCmd)(implicit _btp: MqttBaseTopicProvider): Option[MqttMessage] = {
    ev match {
      case EdaEventReceived(ev, _) => eventToMqttMessage(ev)
      case EdaMessageCommand(cmd, _) => commandToMqttMessage(cmd)
    }
  }

  private def eventToMqttMessage(event: EdaEvent): Option[MqttMessage] = {
    val value = event.message.asJson.deepDropNullValues.noSpaces
    Some(MqttMessage(s"${edaProtocolModulePath(event.message.receiver, event.protocol)}", ByteString(value)).withRetained(false))
  }

  private def commandToMqttMessage(command: CommandMessage): Option[MqttMessage] = {
    val value = command.payload.deepDropNullValues.noSpaces
    Some(MqttMessage(s"${edaCommandModulePath(command.tenant, command.command)}", ByteString(value)).withRetained(false))
  }

  private implicit class MqttMessageAckExt(msg: MqttMessage) {
    def withAck(f: () => Future[Done]): MqttMessageWithAck = {
      new MqttMessageWithAck {
        override val message: MqttMessage = msg

        override def ack(): Future[Done] = f()
      }
    }

    def withoutAck(): MqttMessageWithAck = {
      new MqttMessageWithAck {
        override val message: MqttMessage = msg

        override def ack(): Future[Done] = Future.successful(Done)
      }
    }
  }
}
