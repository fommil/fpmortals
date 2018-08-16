// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil.prelude

import org.scalactic.source.Position
import org.scalatest.FlatSpec

abstract class Test extends FlatSpec {

  implicit final class TestSyntax[A](private val self: A) {
    // side effecting, but that's the way tests are in scala...
    def shouldBe(
      that: A
    )(implicit E: Equal[A], S: Show[A], P: Position): Unit =
      if (!E.equal(self, that)) {
        fail(S.shows(that) + " was not equal to " + S.shows(self))
      }
  }

}
object Test {
  def unimplemented: Nothing = scala.sys.error("unimplemented")
}
