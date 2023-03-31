package at.energydash
package actor.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import at.energydash.services.FileService

class ServiceRoute(fileService: FileService)(implicit val system: ActorSystem[_]) {

  val adminRoutes: Route =
    pathPrefix("admin") {
      concat (
        path("upload") {
          post {
            withoutSizeLimit {
              entity(as[Multipart.FormData]) { formData =>
                onSuccess(fileService.handleUpload(formData)) { results =>
                  complete(StatusCodes.Created)
                }
              }
            }
          }
        }

      )
    }

}
