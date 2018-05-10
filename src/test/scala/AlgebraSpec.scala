// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package algebra

import prelude._, Z._

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

object Interceptor extends (Demo.Ast ~> Demo.Ast) {
  def apply[A](fa: Demo.Ast[A]): Demo.Ast[A] =
    Coproduct(
      fa.run match {
        case -\/(Machines.Stop(MachineNode("#c0ffee"))) =>
          -\/(Stop("#tea"))
        case other => other
      }
    )
}

object Monitor extends (Demo.Ast ~> Demo.Ast) {
  var backlog: Int = 0
  def apply[A](fa: Demo.Ast[A]): Demo.Ast[A] = Coproduct(
    fa.run match {
      case msg @ \/-(Drone.GetBacklog()) =>
        backlog += 1
        msg
      case other => other
    }
  )
}

class AlgebraSpec extends FlatSpec {

  // https://github.com/scalaz/scalaz/pull/1753
  def or[F[_], G[_], H[_]](fg: F ~> G, hg: H ~> G): Coproduct[F, H, ?] ~> G =
    λ[Coproduct[F, H, ?] ~> G](_.fold(fg, hg))

  "Free Algebra Interpreters" should "combine their powers" in {
    val iD: Drone.Ast ~> IO         = Drone.interpreter(DummyDrone)
    val iM: Machines.Ast ~> IO      = Machines.interpreter(DummyMachines)
    val interpreter: Demo.Ast ~> IO = or(iM, iD)

    Demo.program
      .foldMap(interpreter)
      .unsafePerformIO()
      .shouldBe(1)
  }

  it should "support monitoring" in {
    val iD: Drone.Ast ~> IO         = Drone.interpreter(DummyDrone)
    val iM: Machines.Ast ~> IO      = Machines.interpreter(DummyMachines)
    val interpreter: Demo.Ast ~> IO = or(iM, iD)

    Monitor.backlog = 0

    Demo.program
      .mapSuspension(Monitor)
      .foldMap(interpreter)
      .unsafePerformIO()
      .shouldBe(1)

    Monitor.backlog.shouldBe(1)
  }

  // FIXME: write a decent test here
  it should "support interception" in {
    val iD: Drone.Ast ~> IO         = Drone.interpreter(DummyDrone)
    val iM: Machines.Ast ~> IO      = Machines.interpreter(DummyMachines)
    val interpreter: Demo.Ast ~> IO = or(iM, iD)

    Demo.program
      .mapSuspension(Interceptor)
      .foldMap(interpreter)
      .unsafePerformIO()
      .shouldBe(1)
  }

  it should "allow smocking" in {
    import Mocker._

    val D: Drone.Ast ~> Id = stub[Int] {
      case Drone.GetBacklog() => 1
    }
    val M: Machines.Ast ~> Id = stub[Map[MachineNode, Instant]] {
      case Machines.GetAlive() => Map.empty
    }

    Demo.program
      .foldMap(or(M, D))
      .shouldBe(1)
  }

}

// inspired by https://github.com/djspiewak/smock
object Mocker {
  import scala.PartialFunction
  final class Stub[A] {
    def apply[F[_], G[_]](pf: PartialFunction[F[A], G[A]]): F ~> G =
      new (F ~> G) {
        override def apply[α](fa: F[α]): G[α] =
          // safe because F and G share a type parameter
          pf.asInstanceOf[PartialFunction[F[α], G[α]]](fa)
      }
  }
  def stub[A]: Stub[A] = new Stub[A]
}
