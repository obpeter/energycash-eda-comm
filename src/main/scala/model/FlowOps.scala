package at.energydash
package model

import akka.stream.scaladsl.Flow
import akka.stream.{Graph, SinkShape}

object FlowOps {
  implicit class FlowEitherOps[A, L, R, Mat](flow: Flow[A, Either[L, R], Mat]) {

    def divertLeft(to: Graph[SinkShape[Either[L, R]], Mat]): Flow[A, R, Mat] =
      flow.via {
        Flow[Either[L, R]]
          .divertTo(to, _.isLeft)
          .collect { case Right(element) ⇒ element }
      }
  }
}