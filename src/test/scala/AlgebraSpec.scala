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

trait BatchMachines[F[_]] {
  def start(nodes: NonEmptyList[MachineNode]): F[Unit]
}
object BatchMachines {
  sealed abstract class Ast[A]
  final case class Start(nodes: NonEmptyList[MachineNode]) extends Ast[Unit]
  def liftF[F[_]](implicit I: Ast :<: F): BatchMachines[Free[F, ?]] =
    new BatchMachines[Free[F, ?]] {
      def start(nodes: NonEmptyList[MachineNode]) = Free.liftF(I(Start(nodes)))
    }
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

  it should "support monkey patching" in {
    type S = Set[MachineNode]
    val M = Mocker.stubAny[Machines.Ast, State[S, ?]] {
      case Machines.Stop(node) => State.modify[S](_ + node)
    }

    val interceptor = λ[Machines.Ast ~> Machines.Ast] {
      case Machines.Stop(MachineNode("#c0ffee")) =>
        Machines.Stop(MachineNode("#tea"))
      case other => other
    }

    Machines
      .liftF[Machines.Ast]
      .stop(MachineNode("#c0ffee"))
      .mapSuspension(interceptor)
      .foldMap(M)
      .run(Set.empty)
      .shouldBe((Set(MachineNode("#tea")), ()))
  }

  it should "support scary optimisations" in {
    fail
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

  // an even less safe variant, but allows more than one return type. The
  // partially applied trick doesn't seem to work.
  def stubAny[F[_], G[_]](pf: PartialFunction[F[_], G[_]]): F ~> G =
    new (F ~> G) {
      override def apply[α](fa: F[α]): G[α] =
        // not even nearly safe, but we'll catch mistakes when the test runs
        pf.asInstanceOf[PartialFunction[F[α], G[α]]](fa)
    }
}
