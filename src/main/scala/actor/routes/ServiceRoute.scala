package at.energydash
package actor.routes

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.model.{Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives._
//import akka.http.scaladsl.server.Directives.pathPrefix
import actor.MessageStorage
import actor.MessageStorage.{FindAll, UpdateEcId, UpdateMessage}
import model.EbMsMessage

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.duration.DurationInt

case class EcIdUpdateHttpMessage(conversationId: String, ecId: String)

class ServiceRoute(fileService: FileService, messageStore: ActorRef[MessageStorage.Command[_]])(implicit val system: ActorSystem[_]) {

  import model.JsonImplicit._

  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler: Scheduler = system.scheduler

  val adminRoutes: Route =
    pathPrefix("admin") {
      concat (
        path("upload") {
          post {
            withoutSizeLimit {
              entity(as[Multipart.FormData]) { formData =>
                onSuccess(fileService.handleUpload(formData, messageStore)) { results =>
                  complete(StatusCodes.Created)
                }
              }
            }
          }
        } ~
          path("conversations") {
            get {
              val processFuture = messageStore.ask(ref => FindAll(ref)).mapTo[MessageStorage.MessageAllFound]
              onSuccess(processFuture) { res =>
                complete(res)
              }
            } ~
              post {
                entity(as[EbMsMessage]) { msg =>
                  val processFuture = messageStore.ask(ref => UpdateMessage(msg, ref)).mapTo[MessageStorage.UpdateMessageResult]
                  onSuccess(processFuture) {res => complete(res)}
                }
              } ~
              put {
                entity(as[EcIdUpdateHttpMessage]) { msg =>
                  val updateEcIdFuture = messageStore.ask(ref => UpdateEcId(msg.conversationId, msg.ecId, ref)).mapTo[MessageStorage.UpdateMessageResult]
                  onSuccess(updateEcIdFuture) {res => complete(res)}
                }
              }
          }
      )
    }

}
