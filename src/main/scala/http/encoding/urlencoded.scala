// Copyright: 2017 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.encoding

import java.lang.String

import scala.{ Long, StringContext, Symbol }
import scala.collection.immutable.Seq
import scala.language.implicitConversions

import export._
import shapeless.{ :: => :*:, _ }
import shapeless.labelled._
import java.net.URLEncoder
import spinoco.protocol.http.Uri

import simulacrum._

@typeclass
trait UrlEncoded[A] {
  def urlEncoded(a: A): String
}

object UrlEncoded extends UrlEncodedLowPriority {
  import ops._
  def instance[A](f: A => String): UrlEncoded[A] = new UrlEncoded[A] {
    override def urlEncoded(a: A): String = f(a)
  }

  implicit val UrlEncodedString: UrlEncoded[String] = instance { s =>
    URLEncoder.encode(s, "UTF-8")
  }
  implicit val UrlEncodedLong: UrlEncoded[Long] = instance { n =>
    n.toString
  }

  implicit val UrlEncodedStringySeq: UrlEncoded[Seq[(String, String)]] =
    instance { m =>
      m.map {
        case (k, v) => s"${k.urlEncoded}=${v.urlEncoded}"
      }.mkString("&")
    }
  implicit val UrlEncodedUri: UrlEncoded[Uri] = instance { u =>
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

// https://github.com/milessabin/export-hook/issues/28
@java.lang.SuppressWarnings(
  scala.Array(
    "org.wartremover.warts.Null",
    "org.wartremover.warts.ExplicitImplicitTypes"
  )
)
@imports[UrlEncoded]
trait UrlEncodedLowPriority

trait DerivedUrlEncoded[T] extends UrlEncoded[T]
// https://github.com/milessabin/export-hook/issues/28
@java.lang.SuppressWarnings(
  scala.Array(
    "org.wartremover.warts.Null",
    "org.wartremover.warts.ExplicitImplicitTypes"
  )
)
@exports object DerivedUrlEncoded {
  import UrlEncoded.ops._
  private def instance[A](f: A => String): DerivedUrlEncoded[A] =
    new DerivedUrlEncoded[A] {
      override def urlEncoded(a: A): String = f(a)
    }

  implicit def gen[T, Repr](
    implicit
    G: LabelledGeneric.Aux[T, Repr],
    R: Cached[Strict[DerivedUrlEncoded[Repr]]]
  ): DerivedUrlEncoded[T] = instance { t =>
    R.value.value.urlEncoded(G.to(t))
  }

  implicit val hnil: DerivedUrlEncoded[HNil] = instance { _ =>
    ""
  }
  implicit def hcons[Key <: Symbol, Value, Remaining <: HList](
    implicit Key: Witness.Aux[Key],
    LV: Lazy[UrlEncoded[Value]],
    DR: DerivedUrlEncoded[Remaining]
  ): DerivedUrlEncoded[FieldType[Key, Value] :*: Remaining] =
    instance {
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
