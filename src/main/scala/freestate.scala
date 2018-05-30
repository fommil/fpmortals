// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package freestate

import scalaz._, Scalaz._

object FreeState {
  sealed abstract class Ast[A] {
    def widen: Ast[A] = this
  }
  final case class Get[S]()     extends Ast[S]
  final case class Put[S](s: S) extends Ast[Unit]

  def liftF[F[_], S](implicit I: Ast :<: F): MonadState[Free[F, ?], S] =
    new MonadState[Free[F, ?], S] {
      def point[A](a: =>A): Free[F, A] = Free.pure(a)
      def bind[A, B](fa: Free[F, A])(
        f: A => Free[F, B]
      ): Free[F, B] = fa.flatMap(f)

      def get: Free[F, S] =
        Free.liftF(I.inj(Get[S]().widen))
      def put(s: S): Free[F, Unit] =
        Free.liftF(I.inj(Put[S](s).widen))

      def init: Free[F, S] = get
    }

}
