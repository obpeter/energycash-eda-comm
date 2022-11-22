package at.energydash

import akka.actor.typed.ActorSystem
import at.energydash.actor.SupervisorActor
import at.energydash.actor.commands._

object EdaMailConnector extends App {
  val supervisor = ActorSystem(SupervisorActor(), "supervisor")
  supervisor ! Start
}
