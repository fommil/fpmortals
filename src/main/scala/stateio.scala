// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package stateio

import scalaz._
import Scalaz._
import ioeffect._
import Isomorphism._

import scala.language.higherKinds

// https://github.com/estatico/scala-newtype/issues/34
// import io.estatico.newtype.macros.newtype

object FastState extends SafeApp {
  def program[F[_]](implicit F: MonadState[F, Int]): F[String] =
    for {
      orig   <- F.get
      update = orig + 10
      _      <- F.put(update)
    } yield update.toString

  def app: Task[Unit] =
    for {
      stateMonad <- StateTask.create(10)
      output     <- program(stateMonad).io
      _          <- console.putStrLn(output).widenError[Throwable]
    } yield ()

  def run(@unused args: List[String]): IO[Void, ExitStatus] =
    app.attempt.map(_ => ExitStatus.ExitNow(0))
}

final class StateTask[A](val io: Task[A]) extends AnyVal
object StateTask {

  def create[S](initial: S): Task[MonadState[StateTask, S]] =
    for {
      ref <- IORef(initial)
    } yield
      new MonadState[StateTask, S] with IsomorphismMonad[StateTask, Task] {
        override val G: Monad[Task] = Monad[Task]
        override val iso: StateTask <~> Task =
          new IsoFunctorTemplate[StateTask, Task] {
            def to[X](ga: StateTask[X]) = ga.io
            def from[X](fa: Task[X])    = new StateTask(fa)
          }

        override def get: StateTask[S] = new StateTask(ref.read)
        override def put(s: S): StateTask[Unit] =
          new StateTask(ref.write(s))

        override def init = get
      }
}
