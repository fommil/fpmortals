// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package time

import prelude._

import scala.language.higherKinds

trait Sleep[F[_]] {
  def sleep(time: FiniteDuration): F[Unit]
}
object Sleep extends SleepBoilerplate

final class SleepTask extends Sleep[Task] {
  def sleep(time: FiniteDuration): Task[Unit] = Task.sleep(time)
}
