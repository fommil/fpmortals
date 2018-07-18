// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil

import fommil.prelude._
import java.time.Instant
import scala.concurrent.duration._
import contextual._

package object time {
  implicit class EpochMillisStringContext(sc: StringContext) {
    val epoch: Prefix[Epoch, Context, EpochInterpolator.type] =
      Prefix(EpochInterpolator, sc)
  }
}

package time {
  final case class Epoch(millis: Long) extends AnyVal {
    def +(d: FiniteDuration): Epoch    = Epoch(millis + d.toMillis)
    def diff(e: Epoch): FiniteDuration = (e.millis - millis).millis
  }

  object EpochInterpolator extends UnsafeVerifier[Epoch] {
    override def attempt(s: String): Epoch =
      Epoch(Instant.parse(s).toEpochMilli)
    override def fail: String = "not in ISO-8601 format"
  }
}

abstract class UnsafeVerifier[A] extends Verifier[A] {
  override def check(s: String): Either[(Int, String), A] =
    Try(attempt(s)).toEither.left.map(_ => (0, fail))

  def attempt(s: String): A
  def fail: String
}
