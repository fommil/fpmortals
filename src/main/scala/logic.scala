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

/**
 * @param backlog how many builds are waiting to be run on the ci
 * @param agents how many agents are fulfilling ci jobs
 * @param managed nodes that are available
 * @param alive nodes are currently alive, values are when they were
 *               started
 * @param pending nodes that we have recently changed the world of.
 *                These should be considered "unavailable". NOTE: we
 *                have a visibility problem here if we never hear back
 *                when they change world to available / alive. Values
 *                are when we requested a change of world.
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

  def initial: FreeS[F, WorldView] =
    (d.getBacklog |@| d.getAgents |@| c.getManaged |@| c.getAlive |@| c.getTime).map {
      case (w, a, av, ac, t) => WorldView(w.items, a.items, av.nodes, ac.nodes, Map.empty, t.time)
    }

  private def diff(from: ZonedDateTime, to: ZonedDateTime) = ChronoUnit.MINUTES.between(from, to)

  // Detects nodes that should be shutdown.
  // This is a very ugly way to do this.
  object Stale {
    def unapply(world: WorldView): Option[Nel[Node]] = world match {
      case WorldView(backlog, _, managed, alive, pending, time) if alive.nonEmpty =>
        val stale = (alive -- pending.keys).collect {
          case (n, started) if backlog == 0 && diff(started, time) % 60 >= 58 => n
          case (n, started) if diff(started, time) >= 290                     => n
        }.toList
        Nel.fromList(stale)

      case _ => None
    }
  }

  // written as exclusive actions. It would be instructive to refactor
  // as a series of steps that are always performed.
  def act(world: WorldView): FreeS[F, WorldView] = world match {
    // when there is a backlog, but no agents or pending nodes, start a node
    case WorldView(w, 0, Nel(start, _), alive, pending, time) if w > 0 && alive.isEmpty && pending.isEmpty =>
      // annoying that this can't be in the for-comp
      val update = world.copy(pending = Map(start -> time))
      for {
        _ <- c.start(start)
      } yield update

    // when there is no backlog, stop all alive nodes. However, since
    // Google / AWS charge per hour we only shut down machines in
    // their 58th+ minute. Assumes that we are called fairly regularly
    // otherwise we may miss this window (should we take control over
    // calling getTime instead of expecting it in the World?).
    //
    // Even if there is a backlog, stop older agents: a safety net and
    // will probably be removed when I trust the app.
    case world @ Stale(stale) =>
      val update = stale.foldLeft(world) { (world, n) => world.copy(pending = world.pending + (n -> world.time)) }
      // this is gnarly, I'd rather we did the world update before
      // each c.stop, so if we exit early then we don't claim to have
      // moved a bunch of nodes into the pending list
      stale.traverse { n => c.stop(n) }.map(_ => update)

    // TODO: remove pending actions that never went anywhere

    // do nothing...
    case _ => FreeS.pure(world)
  }

}
