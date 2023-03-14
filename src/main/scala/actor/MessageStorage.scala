package at.energydash
package actor

import model.EbMsMessage

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}

object MessageStorage {
  sealed trait Command[Reply <: CommandReply] {
    def replyTo: ActorRef[Reply]
  }

  sealed trait Event
  sealed trait CommandReply

  sealed trait AddMessageResult extends CommandReply
  case class Added(msg: EbMsMessage) extends AddMessageResult
  final case class MessageAdded(id: String, messageId: String, message: EbMsMessage)
  extends Event
    with CborSerializable

  final case class AddMessage(message: EbMsMessage, replyTo: ActorRef[AddMessageResult])
  extends Command[AddMessageResult]

  sealed trait FindMessageResult extends CommandReply
  case class MessageFound(conversation: StoredConversation) extends FindMessageResult
  case class MessageNotFound(id: String) extends FindMessageResult
  final case class FindById(id: String, replyTo: ActorRef[FindMessageResult])
  extends Command[FindMessageResult]

  // state definition
  final case class Storage(conversations: Map[String, StoredConversation] = Map.empty) {
    def applyEvent(event: Event): Storage = event match {
      case MessageAdded(id, messageId, message) =>
        copy(conversations = conversations.updated(id, conversations.get(id) match {
          case Some(conversation) => StoredConversation(id, conversation.messages + (messageId -> message))
          case None => StoredConversation(id, Map(messageId -> message))
        }))
    }

    def applyCommand(context: ActorContext[Command[_]], cmd: Command[_]): ReplyEffect[Event, Storage] = cmd match {
      case AddMessage(message, replyTo) =>
        val event = MessageAdded(message.conversationId, message.messageId.get, message)
        Effect.persist(event).thenReply(replyTo)(_ => Added(event.message))
      case FindById(id, replyTo) if conversations.contains(id) =>
        Effect.reply(replyTo)(MessageFound(conversations(id)))
      case FindById(id, replyTo) if !conversations.contains(id) =>
        Effect.reply(replyTo)(MessageNotFound(id))
    }
  }

  case class StoredConversation(conversationId: String, messages: Map[String, EbMsMessage])

  def apply(): Behavior[Command[_]] = Behaviors.setup { context =>
    EventSourcedBehavior.withEnforcedReplies[Command[_], Event, Storage](
      persistenceId = PersistenceId.ofUniqueId("messagestorage"),
      emptyState = Storage(),
      commandHandler = (state, cmd) => state.applyCommand(context, cmd),
      eventHandler = (state, evt) => state.applyEvent(evt))
  }
}
