package at.energydash

import actor.SupervisorActor
import actor.commands._

import akka.actor.typed.ActorSystem

object EdaMailConnector extends App {
  val supervisor = ActorSystem(SupervisorActor(), "supervisor")
  supervisor ! Start
}
