// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package indexed

import scalaz._, Scalaz._

// based on https://youtu.be/JPVagd9W4Lo?t=928
import Cache._

trait Cache[M[_], K, V] {
  type F[in, out, a] = IndexedStateT[M, in, out, a]

  def read[S <: Ready](k: K): F[S, S, Maybe[V]]

  def lock: F[Ready, Locked, Unit]
  def update(k: K, v: V): F[Locked, Updated, Unit]
  def commit: F[Updated, Ready, Unit]
}
object Cache {
  sealed class Ready
  sealed class Locked extends Ready
  final class Updated extends Locked
}

object Main {

  def wibbleise[M[_]: Monad](C: Cache[M, Int, String]) =
    for {
      _ <- C.lock
      a1 <- C.read(13)
      a2 = a1.cata(_ + "'", "wibble")
      _ <- C.update(13, a2)
      _ <- C.commit
    } yield a2
}
