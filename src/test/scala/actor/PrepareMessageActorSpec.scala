package at.energydash
package actor

import model.EbMsMessage
import model.enums.EbMsMessageType

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers.{equal, _}

import java.io.File
import scala.reflect.io.Directory

class PrepareMessageActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterAll {

  "Prepare Message" should {
    "Generate MessageID and RequestID" in {
      val probe = createTestProbe[PrepareMessageActor.PrepareMessageResult]()
      val storage = spawn(PrepareMessageActor())
      val message = EbMsMessage(
        None,
        "AT3241234124312432143",
        "AT100130",
        "RC003000",
        EbMsMessageType.ENERGY_FILE_RESPONSE,
        None,
        None,
        None,
        None,
        None,
        None,
        None
      )

      storage ! PrepareMessageActor.PrepareMessage(message, probe.ref)
      val prepared = probe.expectMessageType[PrepareMessageActor.Prepared]
      prepared.message.messageId.get should endWith ("0000000001")

      storage ! PrepareMessageActor.PrepareMessage(message, probe.ref)
      val prepared1 = probe.expectMessageType[PrepareMessageActor.Prepared]
      prepared1.message.messageId.get should endWith("0000000002")
    }
  }

  override protected def afterAll(): Unit = {
    val dir = new Directory(new File(system.settings.config.getString("akka.persistence.journal.leveldb.dir")))
    dir.deleteRecursively()
  }

}
