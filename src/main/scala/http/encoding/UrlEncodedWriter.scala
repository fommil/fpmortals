// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.encoding

import prelude._, Z._, S._

import shapeless._
import shapeless.labelled._
import simulacrum._

/**
 * Converts entities into `application/x-www-form-urlencoded`
 */
@typeclass trait UrlEncodedWriter[A] {
  def toUrlEncoded(a: A): String Refined UrlEncoded
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

  implicit val string: UrlEncodedWriter[String] = (s => UrlEncoded(s))
  implicit val long: UrlEncodedWriter[Long]     = string.contramap(_.shows)

  implicit def kvs[F[_]: Traverse]: UrlEncodedWriter[F[(String, String)]] = {
    m =>
      val raw = m.map {
        case (k, v) => s"${k.toUrlEncoded}=${v.toUrlEncoded}"
      }.intercalate("&")
      Refined.unsafeApply(raw) // by deduction
  }

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
    Refined.unsafeApply("")
  }
  implicit def hcons[Key <: Symbol, A, Remaining <: HList](
    implicit Key: Witness.Aux[Key],
    LV: Lazy[UrlEncodedWriter[A]],
    DR: DerivedUrlEncodedWriter[Remaining]
  ): DerivedUrlEncodedWriter[FieldType[Key, A] :: Remaining] = {
    case head :: tail =>
      val rest = {
        val rest = DR.toUrlEncoded(tail)
        if (rest.value.isEmpty) "" else s"&$rest"
      }
      val key   = Key.value.name.toUrlEncoded
      val value = LV.value.toUrlEncoded(head)
      Refined.unsafeApply(s"$key=$value$rest")
  }
}
