// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package indexed

import scalaz._, Scalaz._

// based on https://youtu.be/JPVagd9W4Lo?t=928
import Cache._

trait Cache[M[_]] {
  type F[in, out, a] = IndexedStateT[M, in, out, a]

  def read[S <: Status](k: Int): F[S, S, Maybe[String]]

  def lock: F[Ready, Locked, Unit]
  def update(k: Int, v: String): F[Locked, Updated, Unit]
  def commit: F[Updated, Ready, Unit]
}
object Cache {
  sealed abstract class Status
  final case class Ready()                          extends Status
  final case class Locked(on: ISet[Int])            extends Status
  final case class Updated(values: Int ==>> String) extends Status
}

object Main {

  def wibbleise[M[_]: Monad](C: Cache[M]) =
    for {
      _  <- C.lock
      a1 <- C.read(13)
      a2 = a1.cata(_ + "'", "wibble")
      _  <- C.update(13, a2)
      _  <- C.commit
    } yield a2
}
