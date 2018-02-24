// Copyright: 2017 - 2018 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.encoding

import java.lang.String

import scala.{ Long, StringContext, Symbol }
import scala.language.implicitConversions

import shapeless.{ :: => :*:, _ }
import shapeless.labelled._
import java.net.URLEncoder
import http.client.Url

import scalaz.{ Contravariant, Traverse }
import simulacrum._

@typeclass
trait UrlEncoded[A] {
  def urlEncoded(a: A): String
}

object UrlEncoded {
  import ops._

  implicit val contravariant: Contravariant[UrlEncoded] =
    new Contravariant[UrlEncoded] {
      def contramap[A, B](fa: UrlEncoded[A])(f: B => A): UrlEncoded[B] = {
        b: B =>
          fa.urlEncoded(f(b))
      }
    }

  implicit val string: UrlEncoded[String] = { s =>
    URLEncoder.encode(s, "UTF-8")
  }
  implicit val long: UrlEncoded[Long] = { n =>
    n.toString
  }

  implicit def kvs[F[_]: Traverse]: UrlEncoded[F[(String, String)]] = { m =>
    import scalaz.Scalaz._
    m.map {
      case (k, v) => s"${k.urlEncoded}=${v.urlEncoded}"
    }.intercalate("&")
  }

  // this is not the same as creating a URL... this is about including a URL
  // as URL encoded parameter value. So we should expect lots of escaping.
  implicit val uri: UrlEncoded[Url] = (_.encoded.urlEncoded)

}

trait DerivedUrlEncoded[T] extends UrlEncoded[T]
object DerivedUrlEncoded {
  import UrlEncoded.ops._

  def gen[T, Repr](
    implicit
    G: LabelledGeneric.Aux[T, Repr],
    R: Cached[Strict[DerivedUrlEncoded[Repr]]]
  ): DerivedUrlEncoded[T] = { t =>
    R.value.value.urlEncoded(G.to(t))
  }

  implicit val hnil: DerivedUrlEncoded[HNil] = { _ =>
    ""
  }
  implicit def hcons[Key <: Symbol, Value, Remaining <: HList](
    implicit Key: Witness.Aux[Key],
    LV: Lazy[UrlEncoded[Value]],
    DR: DerivedUrlEncoded[Remaining]
  ): DerivedUrlEncoded[FieldType[Key, Value] :*: Remaining] = {
    case head :*: tail =>
      val rest = {
        val rest = DR.urlEncoded(tail)
        if (rest.isEmpty) "" else s"&$rest"
      }
      val key   = Key.value.name.urlEncoded
      val value = LV.value.urlEncoded(head)
      s"$key=$value$rest"
  }
}
