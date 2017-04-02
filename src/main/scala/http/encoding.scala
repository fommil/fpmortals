// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package http.encoding

import shapeless.{:: => #:, _}
import shapeless.labelled._
import java.net.URLEncoder
import simulacrum.typeclass
import spinoco.protocol.http.Uri

@typeclass trait UrlEncoded[T] {
  def urlEncoded(t: T): String
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
  implicit object UrlEncodedStringyMap extends UrlEncoded[Map[String, String]] {
    override def urlEncoded(m: Map[String, String]): String = {
      m.map {
        case (k, v) => s"$k=${UrlEncodedString.urlEncoded(v)}"
      }.mkString("&")
    }
  }
  implicit object UrlEncodedUri extends UrlEncoded[Uri] {
    override def urlEncoded(u: Uri): String = {
      val scheme = u.scheme.toString
      val host = s"${u.host.host}"
      val port = u.host.port.fold("")(p => s":$p")
      val path = u.path.stringify
      val query = UrlEncodedStringyMap.urlEncoded(u.query.params.toMap)
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
    key: Witness.Aux[Key],
    h: UrlEncoded[Value],
    t: UrlEncoded[Remaining]
  ): UrlEncoded[FieldType[Key, Value] #: Remaining] =
    new UrlEncoded[FieldType[Key, Value] #: Remaining] {
      override def urlEncoded(hlist: FieldType[Key, Value] #: Remaining): String = {
        val rest = {
          val rest = t.urlEncoded(hlist.tail)
          if (rest.isEmpty) "" else s"&$rest"
        }
        s"${key.value.name}=${h.urlEncoded(hlist.head)}$rest"
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
