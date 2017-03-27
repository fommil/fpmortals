// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package logic

import algebra._
import cats.syntax.traverse._
import cats.instances.list._
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

    val ddd = for {
      work <- d.receiveWorkQueue()
      active <- d.receiveActiveWork()
      available <- c.getAvailable()
    } yield (work, active, available)

    ddd map {
      case (w, a, Nodes(Nil)) if w.items + a.items > 0 =>
        for {
          uid <- c.startAgent()
        } yield {}
      case (w, a, available) =>
        available.nodes.traverseU(c.stopAgent)
    }
  }

}
