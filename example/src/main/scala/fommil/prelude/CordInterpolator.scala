// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil.prelude

import scala.StringContext.treatEscapes
import Z._

import scala.language.implicitConversions

/** Cord is very inefficient in Scalaz 7.2, fixed in 7.3 */
final class CordInterpolator(private val sc: StringContext) extends AnyVal {
  // scalafix:off
  def str(args: String*): String               = sc.s(args: _*)
  def z(args: CordInterpolator.Cords*): String = cord(args: _*).toString
  def cord(args: CordInterpolator.Cords*): Cord = {
    val strings = sc.parts.foldRight(IList.empty[Cord])(
      (s, acc) => Cord(treatEscapes(s)) :: acc
    )
    val cords = args.foldRight(IList.empty[Cord])((a, acc) => a.cord :: acc)
    strings.interleave(cords).fold
  }
  // scalafix:on
}
object CordInterpolator {
  // the interpolator takes Cords (not Cord) which allows us to restrict the
  // implicit materialisation of Cord (from Show instances) to this usecase.
  final class Cords private (val cord: Cord) extends AnyVal
  object Cords {
    // scalafix:off
    implicit def trivial(c: Cord): Cords   = new Cords(c)
    implicit def mat[A: Show](a: A): Cords = new Cords(Show[A].show(a))
    // scalafix:on
  }
}
