// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

import scalaz._, Scalaz._, ioeffect._
import scalaz.Tags.Parallel
import scalaz.Leibniz.===

package parallel {

  import scala.language.higherKinds

  class Work[F[_]] {
    def slow(in: String): F[Unit] = ???

    def many(
      fins: IList[String]
    )(
      implicit F: Monad[F],
      P: Applicative[λ[α => F[α] @@ Parallel]]
    ): F[IList[Unit]] =
      fins.parTraverse(s => slow(s))

    def fixed(fa: F[String], fb: F[String])(
      implicit F: Monad[F],
      P: Applicative[λ[α => F[α] @@ Parallel]]
    ): F[String] =
      (fa |@| fb).parApply { case (a, b) => a + b }
  }
}
