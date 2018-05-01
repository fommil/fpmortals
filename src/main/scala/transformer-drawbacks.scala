// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package transdrawbacks

import scalaz._, Scalaz._

final case class Problem(bad: Int)
final case class Table(last: Int)

trait Lookup[F[_]] {
  def look: F[Int]
}

trait MonadErrorState[F[_], E, S] {
  def E: MonadError[F, E]
  def S: MonadState[F, S]

  def bind[A, B](fa: F[A])(f: A => F[B]): F[B] = E.bind(fa)(f)
  def map[A, B](fa: F[A])(f: A => B): F[B] = E.map(fa)(f)
  def pure[A](a: => A): F[A] = E.pure(a)
}
object MonadErrorState {
  object ops {
    implicit class MonadErrorStateSyntax[F[_], A, E, S](fa: F[A])(implicit F: MonadErrorState[F, E, S]) {
      def flatMap[B](f: A => F[B]): F[B] = F.bind(fa)(f)
      def map[B](f: A => B): F[B] = F.map(fa)(f)
    }
  }

  implicit def create[F[_], E, S](implicit E0: MonadError[F, E], S0: MonadState[F, S]): MonadErrorState[F, E, S] =
    new MonadErrorState[F, E, S] {
      override def E: MonadError[F, E] = E0
      override def S: MonadState[F, S] = S0
    }
}

import MonadErrorState.ops._

object Logic {

  // implicit Monad, rest explicit. Easier for us, more work for upstream
  def foo1[F[_]: Monad](L: Lookup[F])(
    E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] =
    for {
      old <- S.get
      i   <- L.look
      _ <- if (i === old.last) E.raiseError(Problem(i))
          else ().pure[F]
    } yield i

  // shadow one of the parameters
  def foo2[F[_]](L: Lookup[F])(
    implicit
    E: MonadError[F, Problem],
    S: MonadState[F, Table]
  ): F[Int] = {
    val s = S;
    {
      val S = s;
      for {
        old <- S.get
        i   <- L.look
        _ <- if (i === old.last) E.raiseError(Problem(i))
            else ().pure[F]
      } yield i
    }
  }

  // custom Monad
  def foo3[F[_]](L: Lookup[F])(
    implicit M: MonadErrorState[F, Problem, Table]
  ): F[Int] =
    for {
      old <- M.S.get
      i   <- L.look
      _ <- if (i === old.last) M.E.raiseError(Problem(i))
           else M.pure(())
    } yield i

}
