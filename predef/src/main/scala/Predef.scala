// Copyright (C) 2017 Sam Halliday
// License: http://www.gnu.org/licenses/gpl.html
package fommil

object Predef {
  // from scala.Predef
  implicit final class ArrowAssoc[A](private val self: A) extends AnyVal {
    @inline def -> [B](y: B): Tuple2[A, B] = Tuple2(self, y)
    def →[B](y: B): Tuple2[A, B] = ->(y)
  }
}
