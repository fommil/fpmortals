// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package const

import scalaz._, Scalaz._

trait KV[F[_]] {
  def get(key: String): F[Option[String]]
  def put(key: String, a: String): F[Unit]
}
object KV {
  @inline def apply[F[_]: KV](implicit i: KV[F]): KV[F] = i
}

object Program {

   def program[F[_]: Apply: KV](): F[List[String]] =
    (F.get("Cats"), F.get("Dogs"), F.put("Mice", "42"), F.get("Cats"))
      .mapN((f, s, _, t) => List(f, s, t).flatten)

  def main(args: Array[String]): Unit = {

  }

}
