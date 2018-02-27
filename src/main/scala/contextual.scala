// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package contextual

import std._
import java.time._

package object datetime {
  implicit class DateTimeStringContext(sc: StringContext) {
    val instant = Prefix(InstantInterpolator, sc)
    val zdt     = Prefix(ZonedDateTimeInterpolator, sc)
  }
}

package datetime {
  object InstantInterpolator extends UnsafeVerifier[Instant] {
    override def attempt(s: String) = Instant.parse(s)
    override def fail               = "not in ISO-8601 format"
  }

  object ZonedDateTimeInterpolator extends UnsafeVerifier[ZonedDateTime] {
    override def attempt(s: String) = ZonedDateTime.parse(s)
    override def fail               = "not in ISO-8601 format"
  }
}

abstract class UnsafeVerifier[A] extends Verifier[A] {
  override def check(s: String) =
    Try(attempt(s)).toEither.left.map(_ => (0, fail))

  def attempt(s: String): A
  def fail: String
}
