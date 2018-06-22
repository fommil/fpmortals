// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package parallel

import scalaz._, Scalaz._, ioeffect._
import scalaz.Tags.Parallel
import scalaz.Leibniz.===

class Work[F[_]] {
  import Par._

  def slow(in: String): F[Unit] = ???

  def many(
    fins: IList[String]
  )(
    implicit F: Monad[F],
    P: Applicative[λ[α => F[α] @@ Parallel]]
  ): F[IList[Unit]] =
    fins.parTraverse(s => slow(s))

}

object Par {

  // TODO: parApply2

  implicit final class ParTraverseOps[F[_], A](private val fa: F[A])
      extends AnyVal {
    final def parTraverse[G[_], B](f: A => G[B])(
      implicit
      F: Traverse[F],
      G: Applicative[λ[α => G[α] @@ Parallel]]
    ): G[F[B]] = {
      type ParG[C] = G[C] @@ Parallel
      Parallel.unwrap(F.traverse(fa)(a => Parallel(f(a)): ParG[B]))
    }
  }

}
