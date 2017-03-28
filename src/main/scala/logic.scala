// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package logic

import algebra._
import cats.data.{
  NonEmptyList => Nel
}
import java.time.temporal.ChronoUnit
import cats.implicits._
import freestyle.implicits._
import freestyle._
import Machines._
import java.time.ZonedDateTime

/**
 * @param backlog how many builds are waiting to be run on the ci
 * @param agents how many agents are fulfilling ci jobs
 * @param managed nodes that are available
 * @param active nodes are currently active, values are when they were
 *               started
 * @param pending nodes that we have recently changed the state of.
 *                These should be considered "unavailable". NOTE: we
 *                have a visibility problem here if we never hear back
 *                when they change state to available / active. Values
 *                are when we requested a change of state.
 * @param time is the most recent clock tick from the service managing
 *             the nodes. Note that this is stale by definition, but
 *             there are best endeavours to refresh it regularly.
 */
case class State(
  backlog: Int,
  agents: Int,
  managed: Nel[Node],
  active: Map[Node, ZonedDateTime],
  pending: Map[Node, ZonedDateTime],
  time: ZonedDateTime
)

class DynamicAgents[F[_]](
  implicit
  d: Drone.Services[F],
  c: Machines.Services[F],
  a: Audit.Services[F]
) {

  def initial: FreeS[F, State] =
    (d.getWorkQueue |@| d.getActiveWork |@| c.getManaged |@| c.getAlive |@| c.getTime).map {
      case (w, a, av, ac, t) => State(w.items, a.items, av.nodes, ac.nodes, Map.empty, t.time)
    }

  def act(state: State): FreeS[F, State] = state match {
    // when there is a backlog, but no agents or pending nodes, start a node
    case State(w, 0, Nel(start, _), active, pending, time) if w > 0 && active.isEmpty && pending.isEmpty =>
      for {
        _ <- c.start(start)
        update = state.copy(pending = Map(start -> time))
      } yield update

    // when there is no pending work, stop all active nodes. However,
    // since Google / AWS charge per hour we only shut down machines
    // in their 58th+ minute. Assumes that we are called fairly
    // regularly (otherwise we may miss this window). Also a safety
    // cap of ~5 hours for any node.
    case State(0, _, managed, active, pending, time) if active.nonEmpty =>
      (active -- pending.keys).toList
        .flatMap {
          case (n, started) =>
            val up = ChronoUnit.MINUTES.between(started, time)
            if (up % 60 >= 58 || up >= 290) Some(n)
            else None
        }.traverse { n => c.stop(n).map(_ => n) }
        .map { nodes =>
          nodes.foldLeft(state) { (state, node) => state.copy(pending = state.pending + (node -> time)) }
        }

    // do nothing...
    case _ => FreeS.pure(state)
  }

}
