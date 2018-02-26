// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.encoding

import java.lang.String

import scala.{ Long, StringContext, Symbol }
import scala.language.implicitConversions

import shapeless.{ :: => :*:, _ }
import shapeless.labelled._
import java.net.URLEncoder

import scalaz.{ Contravariant, Scalaz, Traverse }, Scalaz._
import simulacrum._
import eu.timepit.refined.api.Refined

/**
 * Converts entities into `application/x-www-form-urlencoded`
 */
@typeclass
trait UrlEncodedWriter[A] {
  def toUrlEncoded(a: A): String
}
object UrlEncodedWriter {
  import ops._

  implicit val contravariant: Contravariant[UrlEncodedWriter] =
    new Contravariant[UrlEncodedWriter] {
      def contramap[A, B](
        fa: UrlEncodedWriter[A]
      )(f: B => A): UrlEncodedWriter[B] = { b: B =>
        fa.toUrlEncoded(f(b))
      }
    }

  implicit val string: UrlEncodedWriter[String] = { s =>
    URLEncoder.encode(s, "UTF-8")
  }
  implicit val long: UrlEncodedWriter[Long] = { n =>
    n.toString
  }

  implicit def kvs[F[_]: Traverse]: UrlEncodedWriter[F[(String, String)]] = {
    m =>
      m.map {
        case (k, v) => s"${k.toUrlEncoded}=${v.toUrlEncoded}"
      }.intercalate("&")
  }

  // this could be generalised to all Contravariant typeclasses...
  // Covariant would require a Validate.Plain[A, B]
  implicit def refined[A: UrlEncodedWriter, B]: UrlEncodedWriter[A Refined B] =
    UrlEncodedWriter[A].contramap(_.value)

}

trait DerivedUrlEncodedWriter[T] extends UrlEncodedWriter[T]
object DerivedUrlEncodedWriter {
  import UrlEncodedWriter.ops._

  def gen[T, Repr](
    implicit
    G: LabelledGeneric.Aux[T, Repr],
    R: Cached[Strict[DerivedUrlEncodedWriter[Repr]]]
  ): DerivedUrlEncodedWriter[T] = { t =>
    R.value.value.toUrlEncoded(G.to(t))
  }

  implicit val hnil: DerivedUrlEncodedWriter[HNil] = { _ =>
    ""
  }
  implicit def hcons[Key <: Symbol, Value, Remaining <: HList](
    implicit Key: Witness.Aux[Key],
    LV: Lazy[UrlEncodedWriter[Value]],
    DR: DerivedUrlEncodedWriter[Remaining]
  ): DerivedUrlEncodedWriter[FieldType[Key, Value] :*: Remaining] = {
    case head :*: tail =>
      val rest = {
        val rest = DR.toUrlEncoded(tail)
        if (rest.isEmpty) "" else s"&$rest"
      }
      val key   = Key.value.name.toUrlEncoded
      val value = LV.value.toUrlEncoded(head)
      s"$key=$value$rest"
  }
}
