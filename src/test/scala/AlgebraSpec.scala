// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package algebra

import prelude._, Z._, S._
import Coproduct.{ leftc, rightc }

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
  def noop(): F[Unit]
}
object BatchMachines {
  sealed abstract class Ast[A]
  final case class Start(nodes: NonEmptyList[MachineNode]) extends Ast[Unit]
  final case class Noop()                                  extends Ast[Unit]
  def liftF[F[_]](implicit I: Ast :<: F): BatchMachines[Free[F, ?]] =
    new BatchMachines[Free[F, ?]] {
      def start(nodes: NonEmptyList[MachineNode]) = Free.liftF(I(Start(nodes)))
      def noop()                                  = Free.liftF(I(Noop()))
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

  it should "support monkey patching part 1" in {
    type S = Set[MachineNode]
    val M = Mocker.stubAny[Machines.Ast, State[S, ?]] {
      case Machines.Stop(node) => State.modify[S](_ + node)
    }

    val monkey = λ[Machines.Ast ~> Machines.Ast] {
      case Machines.Stop(MachineNode("#c0ffee")) =>
        Machines.Stop(MachineNode("#tea"))
      case other => other
    }

    Machines
      .liftF[Machines.Ast]
      .stop(MachineNode("#c0ffee"))
      .mapSuspension(monkey)
      .foldMap(M)
      .exec(Set.empty)
      .shouldBe(Set(MachineNode("#tea")))
  }

  it should "support monkey patching part 2" in {
    case class S(
      singles: IList[MachineNode],
      batches: IList[NonEmptyList[MachineNode]]
    ) {
      def addSingle(node: MachineNode): S =
        S(node :: singles, batches)
      def addBatch(nodes: NonEmptyList[MachineNode]): S =
        S(singles, nodes :: batches)
    }

    type T[a] = State[S, a]

    type Orig[a] = Coproduct[Machines.Ast, Drone.Ast, a]

    val M: Machines.Ast ~> T = Mocker.stub[Unit] {
      case Machines.Start(node) => State.modify[S](_.addSingle(node))
    }
    val D: Drone.Ast ~> T = Mocker.stub[Int] {
      case Drone.GetBacklog() => 2.pure[T]
    }
    val B: BatchMachines.Ast ~> T = Mocker.stub[Unit] {
      case BatchMachines.Start(nodes) => State.modify[S](_.addBatch(nodes))
      case BatchMachines.Noop()       => ().pure[T]
    }

    def program[F[_]: Monad](M: Machines[F], D: Drone[F]): F[Unit] =
      for {
        todo <- D.getBacklog
        _    <- (1 |-> todo).traverse(id => M.start(MachineNode(id.shows)))
      } yield ()

    program(Machines.liftF[Orig], Drone.liftF[Orig])
      .foldMap(or(M, D))
      .exec(S(IList.empty, IList.empty))
      .shouldBe(S(IList(MachineNode("2"), MachineNode("1")), IList.empty))

    type Waiting      = IList[MachineNode]
    type Extension[a] = State[Waiting, BatchMachines.Ast[a]]
    type Patched[a]   = Coproduct[Extension, Orig, a]

    // it might be possible to do this without noop, using flatMapSuspension but
    // it is beyond both my, and Lord Tagless's, fp-fu. It boils down to needing
    // a way to distribute Free or to .sequence State, neither is possible.
    def monkey(max: Int) = λ[Orig ~> Patched](
      _.run match {
        case -\/(Machines.Start(node)) =>
          val extension: Extension[Unit] = State { waiting =>
            if (waiting.length >= max)
              IList.empty -> BatchMachines.Start(
                NonEmptyList.nel(node, waiting)
              )
            else
              (node :: waiting) -> BatchMachines.Noop()
          }
          leftc(extension)

        case other => rightc(Coproduct(other))
      }
    )

    type PatchedTarget[a] = State[Waiting, T[a]]
    import Hoister.stateOut

    val interpreter: Patched ~> PatchedTarget = or(
      Hoister.state(B): Extension ~> PatchedTarget,
      or(M, D).andThen[PatchedTarget](stateOut)
    )

    // we need the target to have a Monad, and nested State does not have a Monad
    type Target[a] = State[(Waiting, S), a]
    def unite = λ[PatchedTarget ~> Target](StateUtils.united(_))

    program(Machines.liftF[Orig], Drone.liftF[Orig])
      .foldMap(monkey(1).andThen(interpreter).andThen(unite))
      .exec((IList.empty, S(IList.empty, IList.empty)))
      .shouldBe(
        (
          IList.empty, // no Waiting
          S(
            IList.empty, // no singles
            IList(NonEmptyList(MachineNode("2"), MachineNode("1")))
          )
        )
      )

  }

}

// contributed upstream https://github.com/scalaz/scalaz/pull/1765
object Hoister {
  def nt[F[_], G[_], H[_]: Functor](in: F ~> G) =
    λ[λ[α => H[F[α]]] ~> λ[α => H[G[α]]]](_.map(in))

  def state[F[_], G[_], S](in: F ~> G) = nt[F, G, State[S, ?]](in)

  def stateOut[F[_], S] = λ[F ~> λ[α => State[S, F[α]]]] { fa =>
    State.state(fa)
  }
}

// contributed upstream https://github.com/scalaz/scalaz/pull/1766
object StateUtils {
  def united[S1, S2, A](s: State[S1, State[S2, A]]): State[(S1, S2), A] =
    State(
      ss => {
        val (s1, s2) = ss
        val (ns1, g) = s.run(s1)
        val (ns2, a) = g(s2)
        ((ns1, ns2), a)
      }
    )
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
