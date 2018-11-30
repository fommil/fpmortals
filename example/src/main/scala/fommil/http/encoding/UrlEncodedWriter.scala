// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.encoding

import prelude._
import Z._
import S._
import java.net.URLEncoder

import magnolia._
import simulacrum._

import scala.language.experimental.macros

/**
 * Converts entities into `application/x-www-form-urlencoded`
 */
@typeclass trait UrlEncodedWriter[A] {
  def toUrlEncoded(a: A): String Refined UrlEncoded
}
object UrlEncodedWriter extends UrlEncodedWriter1 {
  import ops._

  // WORKAROUND: no SAM here https://github.com/scala/bug/issues/10814
  def instance[T](f: T => String Refined UrlEncoded): UrlEncodedWriter[T] =
    new UrlEncodedWriter[T] {
      override def toUrlEncoded(t: T): String Refined UrlEncoded = f(t)
    }

  implicit val contravariant: Contravariant[UrlEncodedWriter] =
    new Contravariant[UrlEncodedWriter] {
      def contramap[A, B](
        fa: UrlEncodedWriter[A]
      )(f: B => A): UrlEncodedWriter[B] = { b: B =>
        fa.toUrlEncoded(f(b))
      }
    }

  implicit val encoded: UrlEncodedWriter[String Refined UrlEncoded] = instance(
    identity
  )

  implicit val string: UrlEncodedWriter[String] =
    instance(s => Refined.unsafeApply(URLEncoder.encode(s, "UTF-8"))) // scalafix:ok
  implicit val long: UrlEncodedWriter[Long] =
    instance(s => Refined.unsafeApply(s.toString)) // scalafix:ok

  implicit def ilist[K: UrlEncodedWriter, V: UrlEncodedWriter]
    : UrlEncodedWriter[IList[(K, V)]] = instance { m =>
    val raw = m.map {
      case (k, v) => k.toUrlEncoded.value + "=" + v.toUrlEncoded.value
    }.intercalate("&")
    Refined.unsafeApply(raw) // by deduction
  }

  implicit def imap[K: UrlEncodedWriter, V: UrlEncodedWriter]
    : UrlEncodedWriter[K ==>> V] = ilist[K, V].contramap(_.toList.toIList)

}
private[encoding] sealed abstract class UrlEncodedWriter1 {
  implicit def refined[A: UrlEncodedWriter, B]: UrlEncodedWriter[A Refined B] =
    UrlEncodedWriter[A].contramap(_.value)
}

object UrlEncodedWriterMagnolia {
  type Typeclass[a] = UrlEncodedWriter[a]

  def combine[A](ctx: CaseClass[UrlEncodedWriter, A]): UrlEncodedWriter[A] =
    a => Refined.unsafeApply(
      ctx.parameters.map { p =>
        p.label + "=" + p.typeclass.toUrlEncoded(p.dereference(a))
      }.toList.intercalate("&")
    )

  def gen[A]: UrlEncodedWriter[A] = macro Magnolia.gen[A]
}
