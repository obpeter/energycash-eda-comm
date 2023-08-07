package at.energydash
package mqtt

import config.Config.MqttConfig
import model.EbMsMessage
import mqtt.MqttProtocol._
import mqtt.path.MqttPaths
import utils.ActorContextImplicits

import akka.Done
import akka.actor.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, SupervisorStrategy, ActorRef => TypedActorRef}
import akka.stream.alpakka.mqtt.scaladsl.{MqttFlow, MqttMessageWithAck}
import akka.stream.alpakka.mqtt.{MqttConnectionSettings, MqttMessage, MqttQoS, MqttSubscriptions}
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorAttributes, CompletionStrategy, OverflowStrategy, Supervision}
//import akka.stream.typed.{ActorAttributes, CompletionStrategy, OverflowStrategy, Supervision}
import akka.util.ByteString
import io.circe.generic.auto._
import io.circe.syntax._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import javax.net.ssl.SSLContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait EdaEvent {
  val protocol: String
  val message: EbMsMessage
}

case class MqttBaseTopicProvider(base: String)

object MqttProtocol {

  sealed trait MqttCmd

  case class EdaInboundMessage(protocol: String, message: EbMsMessage) extends EdaEvent

  case class EdaEventReceived(ev: EdaEvent, ack: Option[() => Future[Done]]) extends MqttCmd

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

        val src = mqttStreamSource(baseTopic, connectionSettings(cfg))
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

        context.self ! MQTTConnected

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
            terminatingOk()
        }
      }
    }.onFailure(SupervisorStrategy.restartWithBackoff(minBackoff = 4.seconds, maxBackoff = 1.minute, randomFactor = 0.2))
  }

  private def connected(mqttStream: ActorRef): Behavior[MqttCmd] = {
    Behaviors.setup { implicit context =>
      Behaviors.receiveMessagePartial {
        case ev: EdaEventReceived =>
          context.log.info("EdaEvent received ..............")
          mqttStream ! ev
          Behaviors.same
        case TerminatedWithError(err) =>
          context.log.error("mqtt client connection error", err)
          // stream already failed. just force restart through supervisor
          throw err
        case Terminate =>
          mqttStream ! Terminate
          terminatingOk()
        case TerminatedOk =>
          Behaviors.same
      }
    }
  }

  private def terminatingOk(): Behavior[MqttCmd] =
    Behaviors.receiveMessagePartial {
      case TerminatedOk =>
        Behaviors.stopped
      case TerminatedWithError(_) =>
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }

  private def mqttStreamSource(baseTopic: String, settings: MqttConnectionSettings): RunnableGraph[((ActorRef, Future[Done]), Future[Done])] = {

    val mqttFlow: Flow[MqttMessage, MqttMessageWithAck, Future[Done]] =
      MqttFlow.atLeastOnce(
        settings,
        MqttSubscriptions.empty,
        bufferSize = 8,
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
      .actorRef[EdaEventReceived](compStage, failMatcher, 1000, OverflowStrategy.dropTail)
      .map { edaEv => event2Mqtt(edaEv.ev)}
      .filter(_.isDefined)
      .map(_.get)
      .log("MQTT SERVER", x => {
        println(s"Send MQTT Message to ${x.topic}")
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

  private def event2Mqtt(ev: EdaEvent)(implicit _btp: MqttBaseTopicProvider): Option[MqttMessage] = (ev match {
    // transform some events?
    case ev => moduleEvent2Mqtt(ev)
  }).map(ev => ev.withTopic(s"${_btp.base}/${ev.topic}")) // prepend base topic

  private def moduleEvent2Mqtt(ev: EdaEvent)(implicit _btp: MqttBaseTopicProvider): Option[MqttMessage] =
    eventToMqttMessage(ev)

  private def eventToMqttMessage(event: EdaEvent): Option[MqttMessage] = {
    val value = event.message.asJson.deepDropNullValues.noSpaces
    Some(MqttMessage(s"${edaProtocolModulePath(event.message.receiver, event.protocol)}", ByteString(value)).withRetained(true))
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
