// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda
package logic

import prelude._
import Z._
import algebra._
import time.Epoch

import scala.language.higherKinds

final class Monitored[U[_]: Functor](program: DynAgents[U]) {
  type F[a] = Const[ISet[MachineNode], a]

  val D: Drone[F] = new Drone[F] {
    def getBacklog: F[Int] = Const(ISet.empty)
    def getAgents: F[Int]  = Const(ISet.empty)
  }

  val M: Machines[F] = new Machines[F] {
    def getAlive: F[MachineNode ==>> Epoch]      = Const(ISet.empty)
    def getManaged: F[NonEmptyList[MachineNode]] = Const(ISet.empty)
    def getTime: F[Epoch]                        = Const(ISet.empty)
    def start(node: MachineNode): F[Unit]        = Const(ISet.empty)
    def stop(node: MachineNode): F[Unit]         = Const(ISet.singleton(node))
  }

  val monitor: DynAgents[F] = new DynAgentsModule[F](D, M)

  def act(world: WorldView): U[(WorldView, ISet[MachineNode])] = {
    val stopped = monitor.act(world).getConst
    program.act(world).strengthR(stopped)
  }

}
