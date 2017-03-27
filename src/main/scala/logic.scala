// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package logic

import algebra._
import cats.implicits._
import freestyle.implicits._
import freestyle._
import Container._

class DynamicAgents[F[_]](
  implicit
  d: Drone.Services[F],
  c: Container.Services[F],
  a: Audit.Services[F]
) {

  def doStuff(): FreeS[F, Unit] = {
    (d.receiveWorkQueue() |@| d.receiveActiveWork() |@| c.getAvailable()).map {
      case (w, a, Nodes(Nil)) if w.items + a.items > 0 =>
        for { started <- c.startAgent() } yield started
      case (w, a, available) =>
        available.nodes.map(c.stopAgent)
    }
  }

}
