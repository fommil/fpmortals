// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package http.encoding

import scala.Symbol
import scala.collection.immutable.Nil
import scala.Predef.ArrowAssoc
import scala.language.implicitConversions

import export._
import shapeless.{ :: => :*:, _ }
import shapeless.labelled._
import java.net.URLDecoder
import spinoco.protocol.http.Uri.Query

import simulacrum._

@typeclass
trait QueryEncoded[A] {
  def queryEncoded(a: A): Query
}

object QueryEncoded extends QueryEncodedLowPriority {
  def instance[A](f: A => Query): QueryEncoded[A] = new QueryEncoded[A] {
    override def queryEncoded(a: A): Query = f(a)
  }
}

// https://github.com/milessabin/export-hook/issues/28
@java.lang.SuppressWarnings(
  scala.Array(
    "org.wartremover.warts.Null",
    "org.wartremover.warts.ExplicitImplicitTypes"
  )
)
@imports[QueryEncoded]
trait QueryEncodedLowPriority

trait DerivedQueryEncoded[T] extends QueryEncoded[T]

// https://github.com/milessabin/export-hook/issues/28
@java.lang.SuppressWarnings(
  scala.Array(
    "org.wartremover.warts.Null",
    "org.wartremover.warts.ExplicitImplicitTypes"
  )
)
@exports object DerivedQueryEncoded {
  private def instance[A](f: A => Query): DerivedQueryEncoded[A] =
    new DerivedQueryEncoded[A] {
      override def queryEncoded(a: A): Query = f(a)
    }

  implicit def gen[T, Repr](
    implicit
    G: LabelledGeneric.Aux[T, Repr],
    CR: Cached[Strict[DerivedQueryEncoded[Repr]]]
  ): DerivedQueryEncoded[T] = instance { t =>
    CR.value.value.queryEncoded(G.to(t))
  }

  implicit val hnil: DerivedQueryEncoded[HNil] = instance { _ =>
    Query(Nil)
  }
  implicit def hcons[Key <: Symbol, Value, Remaining <: HList](
    implicit Key: Witness.Aux[Key],
    LV: Lazy[UrlEncoded[Value]],
    DR: DerivedQueryEncoded[Remaining]
  ): DerivedQueryEncoded[FieldType[Key, Value] :*: Remaining] =
    instance {
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
