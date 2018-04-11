// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package continuations

import scalaz.Monad

final case class ContT[F[_], R, O, A](_run: (A => F[O]) => F[R]) {
  def run(amo: A => F[O]): F[R] = _run(amo)
}
object ContT {
  implicit def monad[F[_], R]: Monad[ContT[F, R, R, ?]] =
    new Monad[ContT[F, R, R, ?]] {
      def point[A](a: =>A): ContT[F, R, R, A] = ContT(_(a))
      def bind[A, B](
        fa: ContT[F, R, R, A]
      )(f: A => ContT[F, R, R, B]): ContT[F, R, R, B] =
        ContT(b => fa.run(a => f(a).run(b)))
    }
}

object Directives {

  // inspired by
  // https://gist.github.com/iravid/7c4b3d0bbd5a9de058bd7a5534073b4d

}
