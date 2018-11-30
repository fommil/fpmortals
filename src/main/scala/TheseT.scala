// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package theset

import scalaz.{ \&/, Functor, Monad, Semigroup }
import scalaz.syntax.monad._
import scalaz.syntax.semigroup._
import scalaz.syntax.these._
import \&/.{ Both, That, This }

import scala.language.higherKinds

final case class TheseT[F[_], A, B](run: F[A \&/ B])
object TheseT {
  implicit def monad[F[_]: Monad, A: Semigroup]: Monad[TheseT[F, A, ?]] =
    new Monad[TheseT[F, A, ?]] {
      def bind[B, C](fa: TheseT[F, A, B])(f: B => TheseT[F, A, C]) =
        TheseT(fa.run >>= {
          case This(a) => a.wrapThis[C].point[F]
          case That(b) => f(b).run
          case Both(a, b) =>
            f(b).run.map {
              case This(a_)     => (a |+| a_).wrapThis[C]
              case That(c_)     => Both(a, c_)
              case Both(a_, c_) => Both(a |+| a_, c_)
            }
        })

      def point[B](b: =>B) = TheseT(b.wrapThat.point[F])
    }

}
