// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.encoding

import std._

import java.net.URLDecoder

import shapeless._
import shapeless.labelled._
import simulacrum._

import http.client.UrlQuery

@typeclass trait UrlQueryWriter[A] {
  def toUrlQuery(a: A): UrlQuery
}
trait DerivedUrlQueryWriter[T] extends UrlQueryWriter[T]
object DerivedUrlQueryWriter {
  def gen[T, Repr](
    implicit
    G: LabelledGeneric.Aux[T, Repr],
    CR: Cached[Strict[DerivedUrlQueryWriter[Repr]]]
  ): DerivedUrlQueryWriter[T] = { t =>
    CR.value.value.toUrlQuery(G.to(t))
  }

  implicit val hnil: DerivedUrlQueryWriter[HNil] = { _ =>
    UrlQuery(Nil)
  }
  implicit def hcons[Key <: Symbol, A, Remaining <: HList](
    implicit Key: Witness.Aux[Key],
    LV: Lazy[UrlEncodedWriter[A]],
    DR: DerivedUrlQueryWriter[Remaining]
  ): DerivedUrlQueryWriter[FieldType[Key, A] :: Remaining] = {
    case head :: tail =>
      val first = {
        val decodedKey = Key.value.name
        val decodedValue =
          URLDecoder.decode(LV.value.toUrlEncoded(head), "UTF-8")
        decodedKey -> decodedValue
      }

      val rest = DR.toUrlQuery(tail)
      UrlQuery(first :: rest.params)
  }
}
