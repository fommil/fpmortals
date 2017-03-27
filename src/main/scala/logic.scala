// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package logic

import algebra._
import cats.free._
import cats.syntax.traverse._
import cats.instances.list._
import cats.syntax.functor._

class DynamicAgents[F[_]](
  implicit
  d: Drone.Services[F],
  c: Container.Services[F],
  a: Audit.Services[F]
) {

  def doStuff(): Free[F, Unit] = {

    val ddd = for {
      work <- d.receiveWorkQueue()
      active <- d.receiveActiveWork()
      nodes <- c.getNodes()
    } yield (work, active, nodes)

    ddd flatMap {
      case (w, a, Nil) if w.items + a.items > 0 =>
        for {
          uid <- c.startAgent()
        } yield {}
      case (w, a, n) =>
        n.traverseU(c.stopAgent).void
    }
  }

}
