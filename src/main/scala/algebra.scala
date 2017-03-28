// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package algebra

import java.time.ZonedDateTime
import java.util.UUID

import cats.data.NonEmptyList
import freestyle._

object Drone {
  // responses form a sealed family to make it easier to switch
  // between procedural / streaming uses of the API.

  sealed trait Response
  case class WorkQueue(items: Int) extends Response
  case class WorkActive(items: Int) extends Response

  @free trait Services[F[_]] {
    def getWorkQueue: FreeS[F, WorkQueue]
    def getActiveWork: FreeS[F, WorkActive]
  }
}

object Machines {
  case class Node(id: UUID)
  sealed trait Response
  case class Time(time: ZonedDateTime) extends Response
  case class Managed(nodes: NonEmptyList[Node]) extends Response
  case class Alive(nodes: Map[Node, ZonedDateTime]) extends Response

  @free trait Services[F[_]] {
    def getTime: FreeS[F, Time]
    def getManaged: FreeS[F, Managed]
    def getAlive: FreeS[F, Alive]
    def start(node: Node): FreeS[F, Node]
    def stop(node: Node): FreeS[F, Unit]
  }
}

object Audit {
  @free trait Services[F[_]] {
    def store(a: String): FreeS[F, Unit]
  }
}
