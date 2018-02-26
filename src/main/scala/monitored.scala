// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package logic

import java.time.ZonedDateTime

import scala.{ Int, Unit }
import scala.collection.immutable.{ Map, Set }

import scalaz._, Scalaz._

import algebra._

final class Monitored[U[_]: Functor](program: DynAgents[U]) {
  type F[a] = Const[Set[MachineNode], a]

  implicit val drone: Drone[F] = new Drone[F] {
    def getBacklog: F[Int] = Const(Set.empty)
    def getAgents: F[Int]  = Const(Set.empty)
  }

  implicit val machines: Machines[F] = new Machines[F] {
    def getAlive: F[Map[MachineNode, ZonedDateTime]] = Const(Set.empty)
    def getManaged: F[NonEmptyList[MachineNode]]     = Const(Set.empty)
    def getTime: F[ZonedDateTime]                    = Const(Set.empty)
    def start(node: MachineNode): F[Unit]            = Const(Set.empty)
    def stop(node: MachineNode): F[Unit]             = Const(Set(node))
  }

  val monitor = new DynAgents[F]

  def act(world: WorldView): U[(WorldView, Set[MachineNode])] = {
    val stopped = monitor.act(world).getConst
    program.act(world).strengthR(stopped)
  }

}
