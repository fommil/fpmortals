// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package http.encoding

import java.lang.String

import scala.{ Long, StringContext, Symbol }
import scala.collection.immutable.{ Nil, Seq }
import scala.Predef.ArrowAssoc
import scala.language.implicitConversions

import shapeless.{ :: => :*:, _ }
import shapeless.labelled._
import java.net.{ URLDecoder, URLEncoder }
import simulacrum.typeclass
import spinoco.protocol.http.Uri
import spinoco.protocol.http.Uri.Query

// FIXME: split into four files: urlencoded, queryencoded and their
//        export-hook generics. Also introduce the instance method
@typeclass trait UrlEncoded[T] {
  def urlEncoded(t: T): String
}

@typeclass trait QueryEncoded[T] {
  def queryEncoded(t: T): Query
}

object UrlEncoded extends UrlEncodedDefaultInstances
trait UrlEncodedDefaultInstances {
  // primitive impls
  implicit val UrlEncodedString: UrlEncoded[String] = new UrlEncoded[String] {
    override def urlEncoded(s: String): String = URLEncoder.encode(s, "UTF-8")
  }
  implicit val UrlEncodedLong: UrlEncoded[Long] = new UrlEncoded[Long] {
    override def urlEncoded(s: Long): String = s.toString
  }

  // useful impls
  import UrlEncoded.ops._
  implicit val UrlEncodedStringySeq: UrlEncoded[Seq[(String, String)]] =
    new UrlEncoded[Seq[(String, String)]] {
      override def urlEncoded(m: Seq[(String, String)]): String =
        m.map {
          case (k, v) => s"${k.urlEncoded}=${v.urlEncoded}"
        }.mkString("&")
    }
  implicit val UrlEncodedUri: UrlEncoded[Uri] = new UrlEncoded[Uri] {
    override def urlEncoded(u: Uri): String = {
      // WORKAROUND: https://github.com/Spinoco/fs2-http/issues/15
      //             (which would also let us remove UrlEncodedStringySeq)
      val scheme = u.scheme.toString
      val host   = u.host.host
      val port   = u.host.port.fold("")(p => s":$p")
      val path   = u.path.stringify
      val query  = u.query.params.toSeq.urlEncoded
      s"$scheme://$host$port$path?$query".urlEncoded
    }
  }

}

object generic
    extends UrlEncodedGenericInstances
    with UrlEncodedDefaultInstances

trait UrlEncodedGenericInstances {
  this: UrlEncodedDefaultInstances =>
  import UrlEncoded.ops._

  // generic impl
  implicit val UrlEncodedHNil: UrlEncoded[HNil] = new UrlEncoded[HNil] {
    override def urlEncoded(h: HNil): String = ""
  }
  implicit def UrlEncodedHList[Key <: Symbol, Value, Remaining <: HList](
    implicit
    k: Witness.Aux[Key],
    h: UrlEncoded[Value],
    t: UrlEncoded[Remaining]
  ): UrlEncoded[FieldType[Key, Value] :*: Remaining] =
    new UrlEncoded[FieldType[Key, Value] :*: Remaining] {
      override def urlEncoded(
        hlist: FieldType[Key, Value] :*: Remaining
      ): String = {
        val rest = {
          val rest = t.urlEncoded(hlist.tail)
          if (rest.isEmpty) "" else s"&$rest"
        }
        val key   = k.value.name.urlEncoded
        val value = h.urlEncoded(hlist.head)
        s"$key=$value$rest"
      }
    }
  implicit def UrlEncodedGeneric[T, Repr](
    implicit
    g: LabelledGeneric.Aux[T, Repr],
    u: UrlEncoded[Repr]
  ): UrlEncoded[T] = new UrlEncoded[T] {
    override def urlEncoded(t: T): String = u.urlEncoded(g.to(t))
  }

}

object QueryEncoded {
  // generic impl
  implicit val QueryEncodedHNil: QueryEncoded[HNil] = new QueryEncoded[HNil] {
    override def queryEncoded(h: HNil): Query = Query(Nil)
  }
  implicit def QueryEncodedHList[Key <: Symbol, Value, Remaining <: HList](
    implicit
    key: Witness.Aux[Key],
    h: UrlEncoded[Value],
    t: QueryEncoded[Remaining]
  ): QueryEncoded[FieldType[Key, Value] :*: Remaining] =
    new QueryEncoded[FieldType[Key, Value] :*: Remaining] {
      override def queryEncoded(
        hlist: FieldType[Key, Value] :*: Remaining
      ): Query = {
        val first = {
          val decodedKey = key.value.name
          val decodedValue =
            URLDecoder.decode(h.urlEncoded(hlist.head), "UTF-8")
          decodedKey -> decodedValue
        }

        val rest = t.queryEncoded(hlist.tail)
        Query(first :: rest.params)
      }
    }
  implicit def QueryEncodedGeneric[T, Repr](
    implicit
    g: LabelledGeneric.Aux[T, Repr],
    u: QueryEncoded[Repr]
  ): QueryEncoded[T] = new QueryEncoded[T] {
    override def queryEncoded(t: T): Query = u.queryEncoded(g.to(t))
  }
}
