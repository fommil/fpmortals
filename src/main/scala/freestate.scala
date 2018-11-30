// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package freestate

import scalaz._
import Scalaz._

import scala.language.higherKinds

object FreeState {
  sealed abstract class Ast[S, A]
  final case class Get[S]()     extends Ast[S, S]
  final case class Put[S](s: S) extends Ast[S, Unit]

  implicit def liftF[F[_], S](
    implicit I: Ast[S, ?] :<: F
  ): MonadState[Free[F, ?], S] with BindRec[Free[F, ?]] =
    new MonadState[Free[F, ?], S] with BindRec[Free[F, ?]] {
      // should really extend the Free instance, but it's not exposed...
      val delegate                                       = Free.freeMonad[F]
      def point[A](a: =>A): Free[F, A]                   = delegate.point(a)
      def bind[A, B](fa: Free[F, A])(f: A => Free[F, B]) = delegate.bind(fa)(f)
      override def map[A, B](fa: Free[F, A])(f: A => B)  = delegate.map(fa)(f)
      override def tailrecM[A, B](f: A => Free[F, A \/ B])(a: A) =
        delegate.tailrecM(f)(a)

      def get: Free[F, S]          = Free.liftF(I.inj(Get[S]()))
      def put(s: S): Free[F, Unit] = Free.liftF(I.inj(Put[S](s)))

      def init: Free[F, S] = get
    }

}
