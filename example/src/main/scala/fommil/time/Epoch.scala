// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil

import prelude._, S._, Z._

import java.time.Instant
import java.lang.System

import scala.{ Either, Left, Right, StringContext }
import scala.util.control.NonFatal

import contextual._
import pureconfig.orphans._

package object time {
  implicit class EpochMillisStringContext(sc: StringContext) {
    val epoch: Prefix[Epoch, Context, EpochInterpolator.type] =
      Prefix(EpochInterpolator, sc)
  }
}

package time {
  @xderiving(Order, Arbitrary)
  final case class Epoch(millis: Long) extends AnyVal {
    def +(d: FiniteDuration): Epoch = Epoch(millis + d.toMillis)
    def -(e: FiniteDuration): Epoch = Epoch(millis - e.toMillis)
    def -(e: Epoch): FiniteDuration = (millis - e.millis).millis
  }
  object Epoch {
    def now: IO[Void, Epoch] =
      IO.sync(Epoch(System.currentTimeMillis)) // scalafix:ok

    implicit val show: Show[Epoch] =
      Show.shows(e => Instant.ofEpochMilli(e.millis).toString) // scalafix:ok

    implicit val configReader: ConfigReader[Epoch] =
      ConfigReader[String].emap(
        s =>
          EpochInterpolator.check(s) match {
            case Left((_, err)) => failureReason(err)
            case Right(success) => Right(success)
          }
      )
  }

  object EpochInterpolator extends Verifier[Epoch] {
    def check(s: String): Either[(Int, String), Epoch] =
      try Right(Epoch(Instant.parse(s).toEpochMilli))
      catch { case NonFatal(_) => Left((0, "not in ISO-8601 format")) }
  }
}
