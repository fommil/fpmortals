// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package http.encoding

import shapeless.{:: => #:, _}
import shapeless.labelled._
import java.net.{URLEncoder, URLDecoder}
import simulacrum.typeclass
import spinoco.protocol.http.Uri
import spinoco.protocol.http.Uri.Query

@typeclass trait UrlEncoded[T] {
  def urlEncoded(t: T): String
}

@typeclass trait QueryEncoded[T] {
  def queryEncoded(t: T): Query
}

object UrlEncoded {
  // primitive impls
  implicit object UrlEncodedString extends UrlEncoded[String] {
    override def urlEncoded(s: String): String = URLEncoder.encode(s, "UTF-8")
  }
  implicit object UrlEncodedLong extends UrlEncoded[Long] {
    override def urlEncoded(s: Long): String = s.toString
  }

  // useful impls
  implicit object UrlEncodedStringySeq extends UrlEncoded[Seq[(String, String)]] {
    override def urlEncoded(m: Seq[(String, String)]): String = {
      m.map {
        case (k, v) => s"${UrlEncodedString.urlEncoded(k)}=${UrlEncodedString.urlEncoded(v)}"
      }.mkString("&")
    }
  }
  implicit object UrlEncodedUri extends UrlEncoded[Uri] {
    override def urlEncoded(u: Uri): String = {
      val scheme = u.scheme.toString
      val host = s"${u.host.host}"
      val port = u.host.port.fold("")(p => s":$p")
      val path = u.path.stringify
      val query = UrlEncodedStringySeq.urlEncoded(u.query.params)
      val uri = s"$scheme://$host$port$path?$query"
      UrlEncodedString.urlEncoded(uri)
    }
  }

  // generic impl
  implicit object UrlEncodedHNil extends UrlEncoded[HNil] {
    override def urlEncoded(h: HNil): String = ""
  }
  implicit def UrlEncodedHList[Key <: Symbol, Value, Remaining <: HList](
    implicit
    k: Witness.Aux[Key],
    h: UrlEncoded[Value],
    t: UrlEncoded[Remaining]
  ): UrlEncoded[FieldType[Key, Value] #: Remaining] =
    new UrlEncoded[FieldType[Key, Value] #: Remaining] {
      override def urlEncoded(hlist: FieldType[Key, Value] #: Remaining): String = {
        val rest = {
          val rest = t.urlEncoded(hlist.tail)
          if (rest.isEmpty) "" else s"&$rest"
        }
        val key = UrlEncodedString.urlEncoded(k.value.name)
        s"${key}=${h.urlEncoded(hlist.head)}$rest"
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
  // primitive impls
  implicit def QueryEncodedTuples[K, V](
    implicit
    k: UrlEncoded[K],
    v: UrlEncoded[V]
  ): QueryEncoded[Seq[(K, V)]] = new QueryEncoded[Seq[(K, V)]] {
    override def queryEncoded(s: Seq[(K, V)]): Query = {
      // in general we only have access to the url-encoded version
      val entries = s.map {
        case (key, value) =>
          val decodedKey = URLDecoder.decode(k.urlEncoded(key), "UTF-8")
          val decodedValue = URLDecoder.decode(v.urlEncoded(value), "UTF-8")
          decodedKey -> decodedValue
      }
      Query(entries.toList)
    }
  }

  // generic impl
  implicit object QueryEncodedHNil extends QueryEncoded[HNil] {
    override def queryEncoded(h: HNil): Query = Query(Nil)
  }
  implicit def QueryEncodedHList[Key <: Symbol, Value, Remaining <: HList](
    implicit
    key: Witness.Aux[Key],
    h: UrlEncoded[Value],
    t: QueryEncoded[Remaining]
  ): QueryEncoded[FieldType[Key, Value] #: Remaining] =
    new QueryEncoded[FieldType[Key, Value] #: Remaining] {
      override def queryEncoded(hlist: FieldType[Key, Value] #: Remaining): Query = {
        val first = {
          val decodedKey = key.value.name
          val decodedValue = URLDecoder.decode(h.urlEncoded(hlist.head), "UTF-8")
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
