// Copyright: 2017 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package logic

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import scala.{ Int, None, Option }
import scala.collection.immutable.{ Map, Set }
import scala.concurrent.duration._
import scala.language.higherKinds
import scala.Predef.ArrowAssoc

import scalaz._
import Scalaz._

import algebra._

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
  managed: NonEmptyList[MachineNode],
  alive: Map[MachineNode, ZonedDateTime],
  pending: Map[MachineNode, ZonedDateTime],
  time: ZonedDateTime
)

final class DynAgents[F[_]: Applicative](implicit d: Drone[F], m: Machines[F]) {

  def initial: F[WorldView] =
    (d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime) {
      case (db, da, mm, ma, mt) => WorldView(db, da, mm, ma, Map.empty, mt)
    }

  def update(old: WorldView): F[WorldView] =
    initial.map { snap =>
      val changed = symdiff(old.alive.keySet, snap.alive.keySet)
      val pending = (old.pending -- changed).filterNot {
        case (_, started) => timediff(started, snap.time) >= 10.minutes
      }
      snap.copy(pending = pending)
    }

  private def symdiff[T](a: Set[T], b: Set[T]): Set[T] =
    (a union b) -- (a intersect b)

  def act(world: WorldView): F[WorldView] = world match {
    case NeedsAgent(node) =>
      m.start(node) >| world.copy(pending = Map(node -> world.time))

    case Stale(nodes) =>
      nodes.traverse { node =>
        m.stop(node) >| node
      }.map { stopped =>
        val updates = stopped.map(_ -> world.time).toList.toMap
        world.copy(pending = world.pending ++ updates)
      }

    case _ => world.pure[F]
  }

  private def timediff(from: ZonedDateTime, to: ZonedDateTime): FiniteDuration =
    ChronoUnit.MINUTES.between(from, to).minutes

  // with a backlog, but no agents or pending nodes, start a node
  private object NeedsAgent {
    def unapply(world: WorldView): Option[MachineNode] = world match {
      case WorldView(backlog, 0, managed, alive, pending, _)
          if backlog > 0 && alive.isEmpty && pending.isEmpty =>
        Option(managed.head)
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
    def unapply(world: WorldView): Option[NonEmptyList[MachineNode]] =
      world match {
        case WorldView(backlog, _, _, alive, pending, time) if alive.nonEmpty =>
          (alive -- pending.keys).collect {
            case (n, started)
                if backlog == 0 && timediff(started, time).toMinutes % 60 >= 58 =>
              n
            case (n, started) if timediff(started, time) >= 5.hours => n
          }.toList.toNel

        case _ => None
      }
  }

}
