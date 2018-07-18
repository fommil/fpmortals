// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package logic

import prelude._, Z._, S._

import algebra._
import time.Epoch

final class Monitored[U[_]: Functor](program: DynAgents[U]) {
  type F[a] = Const[Set[MachineNode], a]

  val D: Drone[F] = new Drone[F] {
    def getBacklog: F[Int] = Const(Set.empty)
    def getAgents: F[Int]  = Const(Set.empty)
  }

  val M: Machines[F] = new Machines[F] {
    def getAlive: F[Map[MachineNode, Epoch]]     = Const(Set.empty)
    def getManaged: F[NonEmptyList[MachineNode]] = Const(Set.empty)
    def getTime: F[Epoch]                        = Const(Set.empty)
    def start(node: MachineNode): F[Unit]        = Const(Set.empty)
    def stop(node: MachineNode): F[Unit]         = Const(Set(node))
  }

  val monitor: DynAgents[F] = new DynAgents[F](D, M)

  def act(world: WorldView): U[(WorldView, Set[MachineNode])] = {
    val stopped = monitor.act(world).getConst
    program.act(world).strengthR(stopped)
  }

}
