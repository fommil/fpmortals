// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package parallel

import scalaz._, Tags.Parallel, Scalaz._, ioeffect._

class Work[F[_]] {
  type ParF[a] = F[a] @@ Parallel
  def par[A](fa: F[A]): ParF[A] = Parallel(fa)

  def slow(in: String): F[Unit] = ???

  def many(
    fins: IList[String]
  )(
    implicit F: Monad[F],
    P: Applicative[ParF]
  ): F[IList[Unit]] =
    Parallel.unwrap(fins.traverse(s => par(slow(s))))
  //   def traverse[G[_]:Applicative,A,B](fa: F[A])(f: A => G[B]): G[F[B]] =

}
