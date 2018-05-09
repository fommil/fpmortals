// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package algebra

import std._, Z._

import org.scalatest._
import org.scalatest.Matchers._

object Demo {
  def todo[F[_]: Monad](M: Machines[F], D: Drone[F]): F[Int] =
    for {
      work  <- D.getBacklog
      alive <- M.getAlive
    } yield (work - alive.size)

  type Ast[a] = Coproduct[Machines.Ast, Drone.Ast, a]
  type Ctx[a] = Free[Ast, a]
  val program: Ctx[Int] = todo[Ctx](Machines.liftF, Drone.liftF)
}

object DummyDrone extends Drone[IO] {
  def getAgents: IO[Int]  = ???
  def getBacklog: IO[Int] = IO(1)
}
object DummyMachines extends Machines[IO] {
  def getAlive: IO[Map[MachineNode, Instant]]   = IO(Map.empty)
  def getManaged: IO[NonEmptyList[MachineNode]] = ???
  def getTime: IO[Instant]                      = ???
  def start(node: MachineNode): IO[Unit]        = ???
  def stop(node: MachineNode): IO[Unit]         = ???
}

class AlgebraSpec extends FlatSpec {

  // https://github.com/scalaz/scalaz/pull/1753
  def or[F[_], G[_], H[_]](fg: F ~> G, hg: H ~> G): Coproduct[F, H, ?] ~> G =
    λ[Coproduct[F, H, ?] ~> G](_.fold(fg, hg))

  "Free Algebra Interpreters" should "combine their powers" in {
    val iD: Drone.Ast ~> IO         = Drone.interpreter(DummyDrone)
    val iM: Machines.Ast ~> IO      = Machines.interpreter(DummyMachines)
    val interpreter: Demo.Ast ~> IO = or(iM, iD)

    Demo.program.foldMap(interpreter).unsafePerformIO().shouldBe(1)
  }
}
