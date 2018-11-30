// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package time

import prelude._

import scala.language.higherKinds

trait LocalClock[F[_]] {
  def now: F[Epoch]
}
object LocalClock extends LocalClockBoilerplate

final class LocalClockTask extends LocalClock[Task] {
  def now: Task[Epoch] = Epoch.now.widenError
}
