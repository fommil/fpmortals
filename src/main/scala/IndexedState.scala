// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package indexed

import scalaz._, Scalaz._

// based on https://youtu.be/JPVagd9W4Lo?t=928
import Cache._

trait Cache[M[_], K, V] {
  type F[in, out, a] = IndexedStateT[M, in, out, a]

  // FIXME: types

  def view[I <: Ready, O <: Ready](k: K): F[I, O, Maybe[V]]
  def lock[I <: Ready, O <: Locked]: F[I, O, Unit]
  def update[I <: Locked, O <: Updated](k: K, v: V): F[I, O, Unit]
  def commit[I <: Locked, O <: Ready]: F[I, O, Unit]
}
// can we use tags instead?
object Cache {
  // I'm not exactly happy that we need to use the scala language this way...
  sealed class Ready
  class Locked extends Ready
  class Updated extends Locked
}

object Main {

  def foo[M[_]: Monad](C: Cache[M, Int, String]) =
    for {
      _ <- C.lock
      a1 <- C.view(13)
      a2 = a1.cata(_ + "'", "wibble")
      _ <- C.update(13, a2)
      _ <- C.commit
    } yield a2
}
