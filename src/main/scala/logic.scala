// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package logic

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import algebra.drone._
import algebra.machines._

import cats.data.{NonEmptyList => Nel}
import cats.implicits._
import freestyle._
import freestyle.implicits._
import freestyle.effects.state

/**
 * @param backlog how many builds are waiting to be run on the ci
 * @param agents how many agents are fulfilling ci jobs
 * @param managed nodes that are available
 * @param alive nodes are currently alive, values are when they were
 *               started
 * @param pending nodes that we have recently changed the state of.
 *                These should be considered "unavailable". NOTE: we
 *                have a visibility problem here if we never hear back
 *                when they change state to available / alive. Values
 *                are when we requested a change of state.
 * @param time is the most recent clock tick from the service managing
 *             the nodes. Note that this is stale by definition, but
 *             there are best endeavours to refresh it regularly.
 */
final case class WorldView(
  backlog: Int,
  agents: Int,
  managed: Nel[Node],
  alive: Map[Node, ZonedDateTime],
  pending: Map[Node, ZonedDateTime],
  time: ZonedDateTime
)

// a bit of boilerplate to combine the algebras
object coproductk {
  @module trait DynAgents[F[_]] {
    val d: Drone[F]
    val c: Machines[F]
  }
}
import coproductk._

final case class DynAgentsLogic[F[_]](
  implicit
  m: DynAgents[F]
) {
  import m._

  val st = state[WorldView]

  def initial(implicit s: st.StateM[F]): FreeS[F, Unit] =
    (d.getBacklog |@| d.getAgents |@| c.getManaged |@| c.getAlive |@| c.getTime).map {
      case (w, a, av, ac, t) =>
        s.set(
          WorldView(w.items, a.items, av.nodes, ac.nodes, Map.empty, t.time)
        )
    }

  def act(implicit s: st.StateM[F]): FreeS[F, Unit] =
    for {
      state <- s.get
      _ <- state match {
        // when there is a backlog, but no agents or pending nodes, start a node
        case WorldView(w, 0, Nel(start, _), alive, pending, time) if w > 0 && alive.isEmpty && pending.isEmpty =>
          for {
            _ <- c.start(start)
            _ <- s.modify(_.copy(pending = Map(start -> time)))
          } yield ()

        // when there is no pending work, stop all alive nodes. However,
        // since Google / AWS charge per hour we only shut down machines
        // in their 58th+ minute. Assumes that we are called fairly
        // regularly (otherwise we may miss this window). Also a safety
        // cap of ~5 hours for any node.
        case WorldView(0, _, managed, alive, pending, time) if alive.nonEmpty =>
          (alive -- pending.keys).toList
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
    } yield ()
}
