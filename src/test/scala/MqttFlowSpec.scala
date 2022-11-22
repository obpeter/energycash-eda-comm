package at.energydash

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttClientSession, ActorMqttServerSession, Mqtt}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source, SourceQueueWithComplete, Tcp}
import akka.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import akka.stream._
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.{AnyFlatSpec, AnyFlatSpecLike}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._



class MqttFlowSpec extends TestKit(ActorSystem("MqttFlowSpec"))
  with AnyFlatSpecLike
//  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {
  private implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  private implicit val mat: Materializer = ActorMaterializer()
  private implicit val dispatcherExecutionContext: ExecutionContext = system.dispatcher

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "mqtt client flow" should
    "establish a bidirectional connection and subscribe to a topic" in assertAllStagesStopped {
      val clientId = "source-spec/flow"
      val topic = "source-spec/topic1"

      //#create-streaming-flow
      val settings = MqttSessionSettings()
      val session = ActorMqttClientSession(settings)

      val connection = Tcp().outgoingConnection("localhost", 1883)

      val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]], NotUsed] =
        Mqtt
          .clientSessionFlow(session, ByteString("1"))
          .join(connection)
      //#create-streaming-flow

      //#run-streaming-flow
      val (commands: SourceQueueWithComplete[Command[Nothing]], events: Future[Publish]) =
        Source
          .queue(1, OverflowStrategy.fail)
          .via(mqttFlow)
          .collect {
            case Right(Event(p: Publish, _)) => p
          }
          .toMat(Sink.head)(Keep.both)
          .run()

      commands.offer(Command(Connect(clientId, ConnectFlags.CleanSession)))
      commands.offer(Command(Subscribe(topic)))
      session ! Command(
        Publish(ControlPacketFlags.RETAIN | ControlPacketFlags.QoSAtLeastOnceDelivery, topic, ByteString("ohi"))
      )
      //#run-streaming-flow

      events.futureValue match {
//        case Publish(_, `topic`, _, bytes) => bytes should ByteString("ohi")
        case e => fail("Unexpected event: " + e)
      }

      //#run-streaming-flow

      // for shutting down properly
      commands.complete()
      commands.watchCompletion().foreach(_ => session.shutdown())
      //#run-streaming-flow
    }
}