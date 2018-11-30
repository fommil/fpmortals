// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package statet

import scalaz.{ Applicative, Bind, Monad, MonadState }
import scalaz.syntax.monad._

import scala.language.higherKinds

sealed abstract class StateT[F[_], S, A] {
  import StateT._

  final def run(initial: S)(implicit F: Monad[F]): F[(S, A)] = this match {
    case Point(f) => f(initial)
    case FlatMap(Point(f), g) =>
      f(initial) >>= { case (s, a) => g(s, a).run(s) }
    case FlatMap(a @ FlatMap(b, g), h) =>
      FlatMap(b, (s: S, x: a.Out) => FlatMap(g(s, x), h)).run(initial)
  }

}
object StateT {
  def apply[F[_], S, A](f: S => F[(S, A)]): StateT[F, S, A] = Point(f)

  def stateT[F[_]: Applicative, S, A](a: A): StateT[F, S, A] =
    a.pure[StateT[F, S, ?]]

  private final case class Point[F[_], S, A](
    run: S => F[(S, A)]
  ) extends StateT[F, S, A]
  private final case class FlatMap[F[_], S, A, B](
    a: StateT[F, S, A],
    f: (S, A) => StateT[F, S, B]
  ) extends StateT[F, S, B] {
    type Out = B
  }

  implicit def monad[F[_]: Applicative, S]: MonadState[StateT[F, S, ?], S] =
    new MonadState[StateT[F, S, ?], S] {
      def point[A](a: =>A): StateT[F, S, A] = Point(s => (s, a).point[F])
      def bind[A, B](
        fa: StateT[F, S, A]
      )(f: A => StateT[F, S, B]): StateT[F, S, B] =
        FlatMap(fa, (_, a: A) => f(a))

      def get: StateT[F, S, S]          = Point(s => (s, s).point[F])
      def put(s: S): StateT[F, S, Unit] = Point(_ => (s, ()).point[F])

      def init = get // 7.2 artefact, removed in 7.3
    }
}
