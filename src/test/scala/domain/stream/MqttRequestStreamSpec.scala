package at.energydash
package domain.stream

import actor.PrepareMessageActor.{PrepareMessage, Prepared}
import actor.TenantProvider.DistributeMail
import actor.{EmailCommand, MessageStorage, PrepareMessageActor}
import domain.email.EmailService
import model.EbMsMessage

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.{ActorSystem => OldActorSystem}
import akka.stream.Materializer
import akka.stream.alpakka.mqtt.MqttMessage
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future, Promise}
import scala.util.Success

class MqttRequestStreamSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  implicit var timeou: Timeout = Timeout(15.seconds)

  val testKit: ActorTestKit = ActorTestKit()

  implicit val system: ActorSystem[Nothing] = testKit.system
  implicit val classicSystem: OldActorSystem =  system.classicSystem
  implicit val materialized: Materializer = Materializer(system)
  implicit val ec = system.executionContext

  def mqttPut(elements: List[MqttMessage]) : Source[MqttMessage, Future[Done]] = {
    val inputPromise = Promise[Source[MqttMessage, NotUsed]]()
    val futureSource = inputPromise.complete(Success(Source(elements))).future
    Source.futureSource(futureSource).mapMaterializedValue(_ => Future(Done))
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()

  "MQTT Stream" should {
    "Mqtt Send Wrong Json Message" in {
      val mqttSource = mqttPut(List(MqttMessage("topic1", ByteString("""{"someField":"value"}"""))))

      val mailerProbe = testKit.createTestProbe[EmailCommand]()
      val transformerProbe = testKit.createTestProbe[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]]()
      val messageStoreProbe = testKit.createTestProbe[MessageStorage.Command[MessageStorage.AddMessageResult]]()
//      val transformerProbe = testKit.expectEffectType[SpawnedAnonymous[String]]
      val mqttReqestStream = MqttRequestStream(mailerProbe.ref, transformerProbe.ref, messageStoreProbe.ref)

      val errorSinkProbe = TestSink.probe[MqttMessage]

      val (errMsg, errSink) = errorSinkProbe.preMaterialize()
      mqttReqestStream.run(mqttSource, Sink.ignore, errSink)

      errMsg.requestNext().payload.utf8String should startWith("Missing required field")
    }

    "Mqtt Send CP_LIST Request Json Message" in {
      import model.JsonImplicit._
      val jsonObject =
        """{
          |"messageCode":"ANFORDERUNG_ECP",
          |"messageId":"rctest202210161905235386216409991",
          |"conversationId":"ectest202210161905235380027488852",
          |"sender":"rctest",
          |"receiver":"ectest",
          |"requestId": "IWRN74PW",
          |"timeline":{"from":1678402800000,"to":1678489200000},
          |"meterList":[{"meteringPoint":"AT00300000000RC100130000000952832"}]
          |}""".stripMargin
      val ebMsMessage = decode[EbMsMessage](jsonObject) match {
        case Right(m) => m
      }
      val mqttSource = mqttPut(List(MqttMessage("topic1", ByteString(jsonObject)), MqttMessage("topic1", ByteString(jsonObject))))

      val mailerProbe = testKit.createTestProbe[EmailCommand]()
      val transformerProbe = testKit.createTestProbe[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]]()
      val messageStoreProbe = testKit.createTestProbe[MessageStorage.Command[MessageStorage.AddMessageResult]]()
      val mqttReqestStream = MqttRequestStream(mailerProbe.ref, transformerProbe.ref, messageStoreProbe.ref)

      val errorSinkProbe = TestSink.probe[MqttMessage]
      val responseSinkProbe = TestSink.probe[MqttMessage]

//      val (errMsg, errSink) = errorSinkProbe.preMaterialize()
      val (respMsg, respSink) = responseSinkProbe.preMaterialize()

      val res = Future {
        mqttReqestStream.run(mqttSource, respSink, Sink.ignore)
        respMsg.request(2).requestNext()
      }

      for( a <- 1 to 2) {
        val transformMsg = transformerProbe.expectMessageType[PrepareMessage]
        transformMsg.replyTo ! Prepared(transformMsg.message)

        val mailMsg = mailerProbe.expectMessageType[DistributeMail]
        Thread.sleep(2000)
        mailMsg.replyTo ! EmailService.SendEmailResponse(mailMsg.mail.data)

        val storeMsg = messageStoreProbe.expectMessageType[MessageStorage.AddMessage]
        storeMsg.replyTo ! MessageStorage.Added(storeMsg.message)

        val resp = Await.result(res, Duration.Inf)
        val payload = resp.payload.utf8String
        decode[EbMsMessage](payload) match {
          case Right(m) => m shouldBe ebMsMessage
          case _ => fail()
        }
        resp.topic shouldBe "eda/response/rctest/protocol/ec_podlist"
        println(resp.payload.utf8String)
      }
//      val res1 = Future {
//        respMsg.requestNext()
//      }
    }
    "Mqtt Send CP_LIST Request Error Json Message" in {
      import model.JsonImplicit._
      val jsonObject =
        """{
          |"messageCode":"ANFORDERUNG_ECP",
          |"messageId":"rctest202210161905235386216409991",
          |"conversationId":"ectest202210161905235380027488852",
          |"sender":"rctest",
          |"receiver":"ectest",
          |"requestId": "IWRN74PW",
          |"timeline":{"from":1678402800000,"to":1678489200000},
          |"meterList":[{"meteringPoint":"AT00300000000RC100130000000952832"}]
          |}""".stripMargin
      val ebMsMessage = decode[EbMsMessage](jsonObject) match {
        case Right(m) => m
      }
      val mqttSource = mqttPut(List(MqttMessage("topic1", ByteString(jsonObject)), MqttMessage("topic1", ByteString(jsonObject))))

      val mailerProbe = testKit.createTestProbe[EmailCommand]()
      val transformerProbe = testKit.createTestProbe[PrepareMessageActor.Command[PrepareMessageActor.PrepareMessageResult]]()
      val messageStoreProbe = testKit.createTestProbe[MessageStorage.Command[MessageStorage.AddMessageResult]]()
      val mqttReqestStream = MqttRequestStream(mailerProbe.ref, transformerProbe.ref, messageStoreProbe.ref)

      val errorSinkProbe = TestSink.probe[MqttMessage]
      val responseSinkProbe = TestSink.probe[MqttMessage]

      //      val (errMsg, errSink) = errorSinkProbe.preMaterialize()
      val (respMsg, respSink) = responseSinkProbe.preMaterialize()

      val res = Future {
        mqttReqestStream.run(mqttSource, respSink, Sink.ignore)
        respMsg.request(2).requestNext()
      }

      for (a <- 1 to 2) {
        val transformMsg = transformerProbe.expectMessageType[PrepareMessage]
        transformMsg.replyTo ! Prepared(transformMsg.message)

        val mailMsg = mailerProbe.expectMessageType[DistributeMail]
        Thread.sleep(2000)
        mailMsg.replyTo ! EmailService.SendErrorResponse("tenant1", "Error Message")

        messageStoreProbe.expectNoMessage(100.milli)

        val resp = Await.result(res, Duration.Inf)
        val payload = resp.payload.utf8String
        decode[EmailService.SendErrorResponse](payload) match {
          case Right(m) => m shouldBe EmailService.SendErrorResponse("tenant1", "Error Message")
          case _ => fail()
        }
        resp.topic shouldBe "eda/response/tenant1/protocol/error"
      }
    }
  }
}
