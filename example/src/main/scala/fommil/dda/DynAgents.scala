// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda
package logic

import prelude._
import S._
import Z._
import algebra._
import time.Epoch

import scala.language.higherKinds

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
@deriving(Equal, Show)
final case class WorldView(
  backlog: Int,
  agents: Int,
  managed: NonEmptyList[MachineNode],
  alive: MachineNode ==>> Epoch,
  pending: MachineNode ==>> Epoch,
  time: Epoch
)

trait DynAgents[F[_]] {
  def initial: F[WorldView]
  def update(old: WorldView): F[WorldView]
  def act(world: WorldView): F[WorldView]
}
object DynAgents extends DynAgentsBoilerplate

final class DynAgentsModule[F[_]: Applicative](D: Drone[F], M: Machines[F])
    extends DynAgents[F] {

  def initial: F[WorldView] =
    (D.getBacklog |@| D.getAgents |@| M.getManaged |@| M.getAlive |@| M.getTime) {
      case (db, da, mm, ma, mt) => WorldView(db, da, mm, ma, IMap.empty, mt)
    }

  def update(old: WorldView): F[WorldView] =
    initial.map { snap =>
      val changed = symdiff(old.alive, snap.alive)
      val pending = (old.pending
        .difference(changed))
        .filterWithKey(
          (_, started) => snap.time - started < 10.minutes
        )
      snap.copy(pending = pending)
    }

  private def symdiff[A: Order, B](a1: A ==>> B, a2: A ==>> B): A ==>> B =
    a1.union(a2).difference(a1.intersection(a2))

  def act(world: WorldView): F[WorldView] = world match {
    case NeedsAgent(node) =>
      M.start(node) >| world.copy(pending = IMap.singleton(node, world.time))

    case Stale(nodes) =>
      nodes.traverse { node =>
        M.stop(node) >| node
      }.map { stopped =>
        val updates = IMap.fromFoldable(stopped.strengthR(world.time))
        world.copy(pending = world.pending.union(updates))
      }

    case _ => world.pure[F]
  }

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
        case WorldView(backlog, _, _, alive, pending, time) if !alive.isEmpty =>
          alive
            .difference(pending)
            .filterWithKey { (n, started) =>
              (backlog == 0 && (time - started).toMinutes % 60 >= 58) ||
              (time - started >= 5.hours)
            }
            .keys
            .toNel

        case _ => None
      }
  }

}
