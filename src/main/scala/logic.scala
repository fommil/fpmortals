// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package logic

import algebra._
import cats.free._

class DynamicAgents[F[_]](
  implicit
  d: Drone.Services[F],
  c: Container.Services[F],
  a: Audit.Services[F]
) {

  def doStuff(): Free[F, Unit] = {
    for {
      work <- d.receiveWorkQueue()
      active <- d.receiveActiveWork()
      time <- c.getTime()
      nodes <- c.getNodes()

      // this is what I'd like to write, but the syntax is not correct
      if ((work.length + active > 0) && nodes.isEmpty)
        c.startAgent
      } else if (nodes.nonEmpty) {
        agents.foreach(c.stopAgent)
      }

    } yield ()
  }

}
