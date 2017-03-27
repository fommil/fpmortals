// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package logic

import algebra._
import cats.implicits._
import freestyle.implicits._
import freestyle._
import Machines._

/**
 * @param backlog how many builds are waiting to be run on the ci
 * @param agents how many agents are fulfilling ci jobs
 * @param managed nodes that are available
 * @param active nodes are currently active
 * @param pending nodes that we have recently changed the state of.
 *                These should be considered "unavailable". NOTE: we
 *                have a visibility problem here if we never hear back
 *                when they change state to available / active.
 */
case class State(
  backlog: Int,
  agents: Int,
  managed: Set[Node],
  active: Set[Node],
  pending: Set[Node]
)

class DynamicAgents[F[_]](
  implicit
  d: Drone.Services[F],
  c: Machines.Services[F],
  a: Audit.Services[F]
) {

  def initial: FreeS[F, State] =
    (d.getWorkQueue |@| d.getActiveWork |@| c.getManaged |@| c.getActive).map {
      case (w, a, av, ac) => State(w.items, a.items, av.nodes, ac.nodes, Set.empty)
    }

  def act(state: State) = state match {
    // when there is pending work, but no agents or pending nodes, start a node
    case State(w, 0, managed, active, pending) if w > 0 && active.isEmpty && pending.isEmpty =>
      // note there is a timing issue here, we might want to give some
      // grace time for a pending node to become an agent.
      val starting = managed.head
      for { started <- c.start(starting) } yield (started, state.copy(pending = Set(starting)))

    // when there is no pending work, stop all active nodes
    case State(0, _, managed, active, pending) if active.nonEmpty =>
      // TODO: how do I gather the results into a single Free and fold
      // over the States? (i.e. add each node to the pending part of
      // the State)
      (active -- pending).map { n => n -> c.stop(n) }

    case _ =>
    // do nothing...
    // TODO how do I represent that?
  }

}
