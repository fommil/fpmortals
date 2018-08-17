// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil.prelude

import scala.{ None, Option }

import org.scalactic.source.Position
import org.scalatest.FlatSpec
import org.scalatest.words.ResultOfStringPassedToVerb
import org.scalatest.exceptions.TestFailedException

abstract class Test extends FlatSpec with scalaz.ioeffect.RTS {

  def readConfig[C: ConfigReader]: Task[C] = pureconfig.orphans.readConfig[C]

  implicit final class TestSyntax[A](private val self: A) {
    // side effecting, but that's the way tests are in scala...
    def shouldBe(
      that: A
    )(implicit E: Equal[A], S: Show[A], P: Position): Unit =
      if (!E.equal(self, that)) {
        val msg = z"$that was not equal to $self"
        throw new TestFailedException(
          _ => Option(msg),
          None,
          P
        ) // scalafix:ok
      }
  }

  implicit final class VerkSyntax(v: ResultOfStringPassedToVerb) {
    // scalatest is all very unsafe... the test could specify "in" and would
    // never run the code. *sigh*
    def inTask[A](ta: Task[A]): Unit = v.in {
      val _ = unsafePerformIO(ta)
    }
  }

}
object Test {
  def unimplemented: Nothing = scala.sys.error("unimplemented")
}
