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

  def update(world: WorldView): FreeS[F, WorldView] = for {
    snap <- initial
    update = world.copy(
      backlog = snap.backlog,
      agents = snap.agents,
      managed = snap.managed,
      alive = snap.alive,
      // ignore unresponsive pending actions
      pending = world.pending.filterNot { case (n, started) => diff(started, snap.time) >= 10 },
      time = snap.time
    )
  } yield update

  // FIXME refactor as a series of steps that are always performed.
  //
  // FIXME: extractors feel very ugly (splits scenario detection from
  //        action, and demands all info be in the WorldView), is
  //        there a more idiomatic way?
  def act(world: WorldView): FreeS[F, WorldView] = world match {
    case NeedsAgent(node) =>
      for {
        _ <- c.start(node)
        update = world.copy(pending = Map(node -> world.time))
      } yield update

    case Stale(nodes) =>
      val update = nodes.foldLeft(world) { (world, n) => world.copy(pending = world.pending + (n -> world.time)) }
      // FIXME: do a world update after each c.stop, so if we exit
      //        early then we don't claim to have moved a bunch of
      //        nodes into the pending list
      nodes.traverse { n => c.stop(n) }.map(_ => update)

    case _ => FreeS.pure(world)
  }

  def diff(from: ZonedDateTime, to: ZonedDateTime) = ChronoUnit.MINUTES.between(from, to)

  // with a backlog, but no agents or pending nodes, start a node
  private object NeedsAgent {
    def unapply(world: WorldView): Option[Node] = world match {
      case WorldView(w, 0, Nel(start, _), alive, pending, _) if w > 0 && alive.isEmpty && pending.isEmpty => Some(start)
      case _ => None
    }
  }

  // when there is no backlog, stop all alive nodes. However, since
  // Google / AWS charge per hour we only shut down machines in their
  // 58th+ minute.
  //
  // Assumes that we are called fairly regularly otherwise we may miss
  // this window (should we take control over calling getTime instead
  // of expecting it from the WorldView we are handed?).
  //
  // Safety net: even if there is a backlog, stop older agents.
  private object Stale {
    def unapply(world: WorldView): Option[Nel[Node]] = world match {
      case WorldView(backlog, _, _, alive, pending, time) if alive.nonEmpty =>
        val stale = (alive -- pending.keys).collect {
          case (n, started) if backlog == 0 && diff(started, time) % 60 >= 58 => n
          case (n, started) if diff(started, time) >= 290                     => n
        }.toList
        Nel.fromList(stale)

      case _ => None
    }
  }

}
