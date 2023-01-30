package at.energydash
package actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import at.energydash.actor.MessageStorage.MessageFound
import at.energydash.model.EbMsMessage
import at.energydash.model.enums.EbMsMessageType
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.File
import scala.reflect.io.Directory

class MessageStorageSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterAll {
  "The Message Storage" should {
    "Add messages" in {
      val probe = createTestProbe[MessageStorage.AddMessageResult]()
      val storage = spawn(MessageStorage())
      val message = EbMsMessage(
        Some("AT101212120012121212"),
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
        None)

      storage ! MessageStorage.AddMessage(message, probe.ref)
      probe.expectMessageType[MessageStorage.Added]
    }

    "Find messages by id" in {
      val probe = createTestProbe[MessageStorage.AddMessageResult]()
      val storage = spawn(MessageStorage())
      val message = EbMsMessage(
        Some("AT9999999999999999990"),
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
        None)

      storage ! MessageStorage.AddMessage(message, probe.ref)
      val added = probe.expectMessageType[MessageStorage.Added]

      val lookupProbe = createTestProbe[MessageStorage.FindMessageResult]()
      storage ! MessageStorage.FindById(added.msg.conversationId, lookupProbe.ref)
      val found = lookupProbe.expectMessageType[MessageFound]
      found.conversation.conversationId shouldBe "AT3241234124312432143"
      found.conversation.messages.size shouldBe 2
    }
  }

  override protected def afterAll(): Unit = {
    val dir = new Directory(new File(system.settings.config.getString("akka.persistence.journal.leveldb.dir")))
    println(dir)
    dir.deleteRecursively()
  }
}
