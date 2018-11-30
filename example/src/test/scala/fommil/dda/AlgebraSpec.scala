// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda
package algebra

import prelude._
import Z._
import S._
import Coproduct.{leftc, rightc}
import scalaz.ioeffect.RTS
import fommil.time.Epoch
import Test.unimplemented

import scala.language.higherKinds

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

object DummyDrone extends Drone[Task] {
  def getAgents: Task[Int]  = unimplemented
  def getBacklog: Task[Int] = Task(1)
}
object DummyMachines extends Machines[Task] {
  def getAlive: Task[MachineNode ==>> Epoch]      = Task(IMap.empty)
  def getManaged: Task[NonEmptyList[MachineNode]] = unimplemented
  def getTime: Task[Epoch]                        = unimplemented
  def start(node: MachineNode): Task[Unit]        = unimplemented
  def stop(node: MachineNode): Task[Unit]         = unimplemented
}

trait Batch[F[_]] {
  def start(nodes: NonEmptyList[MachineNode]): F[Unit]
}
object Batch {
  sealed abstract class Ast[A]
  final case class Start(nodes: NonEmptyList[MachineNode]) extends Ast[Unit]
  def liftF[F[_]](implicit I: Ast :<: F): Batch[Free[F, ?]] =
    new Batch[Free[F, ?]] {
      def start(nodes: NonEmptyList[MachineNode]): Free[F, Unit] =
        Free.liftF(I.inj(Start(nodes)))
    }
  def liftA[F[_]](implicit I: Ast :<: F): Batch[FreeAp[F, ?]] =
    new Batch[FreeAp[F, ?]] {
      def start(nodes: NonEmptyList[MachineNode]): FreeAp[F, Unit] =
        FreeAp.lift(I.inj(Start(nodes)))
    }
}

object Monkeys {
  @deriving(Equal, Show)
  final case class S(
    singles: IList[MachineNode],
    batches: IList[NonEmptyList[MachineNode]]
  ) {
    def addSingle(node: MachineNode): S =
      S(node :: singles, batches)
    def addBatch(nodes: NonEmptyList[MachineNode]): S =
      S(singles, nodes :: batches)
  }
}

final class AlgebraSpec extends Test with RTS {

  // https://github.com/scalaz/scalaz/pull/1753
  def or[F[_], G[_], H[_]](fg: F ~> G, hg: H ~> G): Coproduct[F, H, ?] ~> G =
    λ[Coproduct[F, H, ?] ~> G](_.fold(fg, hg))

  "Free Algebra Interpreters".should("combine their powers").in {
    val iD: Drone.Ast ~> Task         = Drone.interpreter(DummyDrone)
    val iM: Machines.Ast ~> Task      = Machines.interpreter(DummyMachines)
    val interpreter: Demo.Ast ~> Task = or(iM, iD)

    unsafePerformIO(
      Demo.program
        .foldMap(interpreter)
    ).shouldBe(1)
  }

  it.should("support monitoring").in {
    val iD: Drone.Ast ~> Task         = Drone.interpreter(DummyDrone)
    val iM: Machines.Ast ~> Task      = Machines.interpreter(DummyMachines)
    val interpreter: Demo.Ast ~> Task = or(iM, iD)

    var count = 0 // scalafix:ok
    val Monitor = λ[Demo.Ast ~> Demo.Ast](
      _.run match {
        case \/-(m @ Drone.GetBacklog()) =>
          count += 1
          Coproduct.rightc(m)
        case other =>
          Coproduct(other)
      }
    )

    unsafePerformIO(
      Demo.program
        .mapSuspension(Monitor)
        .foldMap(interpreter)
    ).shouldBe(1)

    count.shouldBe(1)
  }

  it.should("allow smocking").in {
    import Mocker._

    val D: Drone.Ast ~> Id = stub[Int] {
      case Drone.GetBacklog() => 1
    }
    val M: Machines.Ast ~> Id = stub[MachineNode ==>> Epoch] {
      case Machines.GetAlive() => IMap.empty
    }

    Demo.program
      .foldMap(or(M, D))
      .shouldBe(1)
  }

  it.should("support monkey patching part 1").in {
    type S = ISet[MachineNode]
    val M = Mocker.stubAny[Machines.Ast, State[S, ?]] {
      case Machines.Stop(node) => State.modify[S](_.insert(node))
    }

    val monkey = λ[Machines.Ast ~> Free[Machines.Ast, ?]] {
      case Machines.Stop(MachineNode("#c0ffee")) => Free.pure(())
      case other                                 => Free.liftF(other)
    }

    Machines
      .liftF[Machines.Ast]
      .stop(MachineNode("#c0ffee"))
      .foldMap(monkey)
      .foldMap(M)
      .exec(ISet.empty)
      .shouldBe(ISet.empty)
  }

  // this is a pretty advanced, crazy, idea that was cut from the book. because
  // it is so crazy.
  it.should("support monkey patching part 2").in {
    import Monkeys.S
    type T[a] = State[S, a]

    type Orig[a] = Coproduct[Machines.Ast, Drone.Ast, a]

    val M: Machines.Ast ~> T = Mocker.stub[Unit] {
      case Machines.Start(node) => State.modify[S](_.addSingle(node))
    }
    val D: Drone.Ast ~> T = Mocker.stub[Int] {
      case Drone.GetBacklog() => 2.pure[T]
    }
    val B: Batch.Ast ~> T = Mocker.stub[Unit] {
      case Batch.Start(nodes) => State.modify[S](_.addBatch(nodes))
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
    type Extension[a] = Coproduct[Batch.Ast, Orig, a]
    type Patched[a]   = StateT[Free[Extension, ?], Waiting, a]

    def monkey(max: Int) = new (Orig ~> Patched) {
      def apply[α](fa: Orig[α]): Patched[α] = fa.run match {
        case -\/(Machines.Start(node)) =>
          StateT { waiting =>
            if (waiting.length >= max) {
              val start = Batch.Start(NonEmptyList.nel(node, waiting))
              Free
                .liftF[Extension, Unit](leftc(start))
                .strengthL(IList.empty)
            } else
              Free
                .pure[Extension, Unit](())
                .strengthL(node :: waiting)
          }

        case _ =>
          Free
            .liftF[Extension, α](rightc(fa))
            .liftM[StateT[?[_], Waiting, ?]]
      }
    }

    program(Machines.liftF[Orig], Drone.liftF[Orig])
      .foldMap(monkey(1))
      .run(IList.empty)
      .foldMap(or(B, or(M, D)))
      .run(S(IList.empty, IList.empty))
      .shouldBe(
        (
          S(
            IList.empty, // no singles
            IList(NonEmptyList(MachineNode("2"), MachineNode("1")))
          ),
          (
            IList.empty, // no Waiting
            ()
          )
        )
      )

  }

  it.should("batch calls without any crazy hacks").in {
    type Orig[a] = Coproduct[Machines.Ast, Drone.Ast, a]

    // pretend this is the DynAgents.act method...
    def act[F[_]: Applicative](M: Machines[F], @unused D: Drone[F])(
      todo: Int
    ): F[Unit] =
      (1 |-> todo).traverse(id => M.start(MachineNode(id.shows))).void

    val freeap = act(Machines.liftA[Orig], Drone.liftA[Orig])(2)

    val gather = λ[Orig ~> λ[α => IList[MachineNode]]] {
      case Coproduct(-\/(Machines.Start(node))) => IList.single(node)
      case _                                    => IList.empty
    }

    type Extended[a] = Coproduct[Batch.Ast, Orig, a]
    def batch(nodes: IList[MachineNode]): FreeAp[Extended, Unit] =
      nodes.toNel match {
        case None        => FreeAp.pure(())
        case Some(nodes) => FreeAp.lift(Coproduct.leftc(Batch.Start(nodes)))
      }

    val nostart = λ[Orig ~> FreeAp[Extended, ?]] {
      case Coproduct(-\/(Machines.Start(_))) => FreeAp.pure(())
      case other                             => FreeAp.lift(Coproduct.rightc(other))
    }

    def optimise[A](orig: FreeAp[Orig, A]): FreeAp[Extended, A] =
      (batch(orig.analyze(gather)) *> orig.foldMap(nostart))

    import Monkeys.S
    type T[a] = State[S, a]
    val M: Machines.Ast ~> T = Mocker.stub[Unit] {
      case Machines.Start(node) => State.modify[S](_.addSingle(node))
    }
    val D: Drone.Ast ~> T = Mocker.stub[Int] {
      case Drone.GetBacklog() => 2.pure[T]
    }
    val B: Batch.Ast ~> T = Mocker.stub[Unit] {
      case Batch.Start(nodes) => State.modify[S](_.addBatch(nodes))
    }

    optimise(freeap)
      .foldMap(or(B, or(M, D)))
      .run(S(IList.empty, IList.empty))
      ._1
      .shouldBe(
        S(
          IList.empty, // no singles
          IList(NonEmptyList(MachineNode("2"), MachineNode("1")))
        )
      )

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
          pf.asInstanceOf[PartialFunction[F[α], G[α]]](fa) // scalafix:ok
      }
  }
  def stub[A]: Stub[A] = new Stub[A]

  // an even less safe variant, but allows more than one return type. The
  // partially applied trick doesn't seem to work.
  def stubAny[F[_], G[_]](pf: PartialFunction[F[_], G[_]]): F ~> G =
    new (F ~> G) {
      override def apply[α](fa: F[α]): G[α] =
        // not even nearly safe, but we'll catch mistakes when the test runs
        pf.asInstanceOf[PartialFunction[F[α], G[α]]](fa) // scalafix:ok
    }
}
