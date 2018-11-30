// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package writert

import scalaz.{ Monad, MonadListen, Monoid }
import scalaz.syntax.monad._
import scalaz.syntax.monoid._

import scala.language.higherKinds

final case class WriterT[F[_], W, A](run: F[(W, A)])
object WriterT {
  implicit def monad[F[_]: Monad, W: Monoid] =
    new MonadListen[WriterT[F, W, ?], W] {
      def point[A](a: =>A): WriterT[F, W, A] =
        WriterT((Monoid[W].zero, a).point)
      def bind[A, B](
        fa: WriterT[F, W, A]
      )(f: A => WriterT[F, W, B]): WriterT[F, W, B] =
        WriterT(
          fa.run >>= {
            case (wa, a) =>
              f(a).run.map { case (wb, b) => (wa |+| wb, b) }
          }
        )

      def writer[A](w: W, v: A): WriterT[F, W, A] = WriterT((w -> v).point)
      def listen[A](fa: WriterT[F, W, A]): WriterT[F, W, (A, W)] =
        WriterT(fa.run.map { case (w, a) => (w, (a, w)) })
    }

}
