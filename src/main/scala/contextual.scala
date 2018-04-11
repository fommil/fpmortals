// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package contextual

import fommil.std._
import java.time._

package object datetime {
  implicit class DateTimeStringContext(sc: StringContext) {
    val instant: Prefix[Instant, Context, InstantInterpolator.type] =
      Prefix(InstantInterpolator, sc)
    val zdt: Prefix[ZonedDateTime, Context, ZonedDateTimeInterpolator.type] =
      Prefix(ZonedDateTimeInterpolator, sc)
  }
}

package datetime {
  object InstantInterpolator extends UnsafeVerifier[Instant] {
    override def attempt(s: String): Instant = Instant.parse(s)
    override def fail: String                = "not in ISO-8601 format"
  }

  object ZonedDateTimeInterpolator extends UnsafeVerifier[ZonedDateTime] {
    override def attempt(s: String): ZonedDateTime = ZonedDateTime.parse(s)
    override def fail: String                      = "not in ISO-8601 format"
  }
}

abstract class UnsafeVerifier[A] extends Verifier[A] {
  override def check(s: String): Either[(Int, String), A] =
    Try(attempt(s)).toEither.left.map(_ => (0, fail))

  def attempt(s: String): A
  def fail: String
}
