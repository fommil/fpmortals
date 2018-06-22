// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

import scalaz._, Scalaz._, ioeffect._
import scalaz.Tags.Parallel
import scalaz.Leibniz.===

package parallel {
  class Work[F[_]] {
    import Par._
    import syntax.ParScream._

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

  object Par {
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
}

package scalaz.syntax {
  object ParScream {
    implicit final class ApplicativeBuilderOps2[M[_], A, B](
      private val self: ApplicativeBuilder[M, A, B]
    ) extends AnyVal {
      type P[a] = M[a] @@ Parallel

      def parApply[C](f: (A, B) => C)(implicit ap: Apply[P]): M[C] =
        Parallel.unwrap(ap.apply2(Parallel(self.a), Parallel(self.b))(f))

      def parTupled(implicit ap: Apply[P]): M[(A, B)] = parApply(Tuple2.apply)

    }
  }
}
