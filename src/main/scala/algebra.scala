// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package algebra

import java.util.UUID
import freestyle._

object Drone {
  sealed trait Response
  case class WorkQueue(items: Int) extends Response
  case class WorkActive(items: Int) extends Response

  @free trait Services[F[_]] {
    def receiveWorkQueue(): FreeS[F, WorkQueue]
    def receiveActiveWork(): FreeS[F, WorkActive]
  }
}

object Container {
  case class Node(id: UUID)
  sealed trait Response
  case class Time(time: String) extends Response
  case class Nodes(nodes: List[Node]) extends Response

  @free trait Services[F[_]] {
    def getTime(): FreeS[F, Time]
    def getAvailable(): FreeS[F, Nodes]
    def getActive(): FreeS[F, Nodes]
    def startAgent(): FreeS[F, Node]
    def stopAgent(node: Node): FreeS[F, Unit]
  }
}

object Audit {
  @free trait Services[F[_]] {
    def store(a: String): FreeS[F, Unit]
  }
}
