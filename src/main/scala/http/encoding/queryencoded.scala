// Copyright: 2017 - 2018 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.encoding

import scala.Symbol
import scala.collection.immutable.Nil
import scala.Predef.ArrowAssoc
import scala.language.implicitConversions

import shapeless.{ :: => :*:, _ }
import shapeless.labelled._
import java.net.URLDecoder
import http.client.Url.Query

import simulacrum._

@typeclass
trait QueryEncoded[A] {
  def queryEncoded(a: A): Query
}

trait DerivedQueryEncoded[T] extends QueryEncoded[T]
object DerivedQueryEncoded {
  def gen[T, Repr](
    implicit
    G: LabelledGeneric.Aux[T, Repr],
    CR: Cached[Strict[DerivedQueryEncoded[Repr]]]
  ): DerivedQueryEncoded[T] = { t =>
    CR.value.value.queryEncoded(G.to(t))
  }

  implicit val hnil: DerivedQueryEncoded[HNil] = { _ =>
    Query(Nil)
  }
  implicit def hcons[Key <: Symbol, Value, Remaining <: HList](
    implicit Key: Witness.Aux[Key],
    LV: Lazy[UrlEncoded[Value]],
    DR: DerivedQueryEncoded[Remaining]
  ): DerivedQueryEncoded[FieldType[Key, Value] :*: Remaining] = {
    case head :*: tail =>
      val first = {
        val decodedKey = Key.value.name
        val decodedValue =
          URLDecoder.decode(LV.value.urlEncoded(head), "UTF-8")
        decodedKey -> decodedValue
      }

      val rest = DR.queryEncoded(tail)
      Query(first :: rest.params)
  }
}
