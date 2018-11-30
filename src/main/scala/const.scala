// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package const

import scalaz._
import Scalaz._

import scala.language.higherKinds

trait KV[F[_]] {
  def read(key: String): F[Maybe[String]]
}
object KV {
  @inline def apply[F[_]: KV](implicit i: KV[F]): KV[F] = i

  implicit val id: KV[Id] = new KV[Id] {
    private val canned: IMap[String, String] = IMap("foo" -> "FOO")

    def read(key: String): Maybe[String] = {
      println(s"read $key")
      canned.lookup(key).toMaybe
    }
  }

  implicit val const: KV[Const[ISet[String], ?]] =
    new KV[Const[ISet[String], ?]] {
      def read(key: String) = Const(ISet.singleton(key))
    }

}

object Program {

  // TODO: need a more compelling "program" (this is just batch get... do
  // something, maybe with another step like pinging some other algebra when we
  // see things containing a substring)
  def program[F[_]: Applicative: KV](
    keys: IList[String]
  ): F[IList[(String, String)]] =
    keys
      .traverse(k => KV[F].read(k).map(_.strengthL(k).toIList))
      .map(_.join)

  def optimised[F[_]: Applicative: KV](
    keys: IList[String]
  ): F[IList[(String, String)]] = {
    val deduped = program[Const[ISet[String], ?]](keys).getConst
    // println(deduped.toIList)

    // TODO: use a batched version of KV
    //deduped.toIList.traverse(KV[F].read)
    program[F](deduped.toIList)
  }

  def main(args: Array[String]): Unit = {
    val query = IList("foo", "bar", "foo")
    println(program[Id](query))   // [(foo,FOO),(foo,FOO)]
    println(optimised[Id](query)) // [(foo,FOO)] FIXME FIXME FIXME
  }

}
