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
  @free trait Services[F[_]] {
    def getTime(): FreeS[F, String]
    def getNodes(): FreeS[F, List[UUID]]
    def startAgent(): FreeS[F, UUID]
    def stopAgent(uuid: UUID): FreeS[F, Unit]
  }
}

object Audit {
  @free trait Services[F[_]] {
    def store(a: String): FreeS[F, Unit]
  }
}
