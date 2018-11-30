// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package time

import prelude._
import Z._

import scala.language.higherKinds

private[time] abstract class LocalClockBoilerplate {
  this: LocalClock.type =>

  def liftM[F[_]: Monad, G[_[_], _]: MonadTrans](
    f: LocalClock[F]
  ): LocalClock[G[F, ?]] =
    new LocalClock[G[F, ?]] {
      def now: G[F, Epoch] = f.now.liftM[G]
    }

}

private[time] abstract class SleepBoilerplate {
  this: Sleep.type =>

  def liftM[F[_]: Monad, G[_[_], _]: MonadTrans](
    f: Sleep[F]
  ): Sleep[G[F, ?]] =
    new Sleep[G[F, ?]] {
      def sleep(time: FiniteDuration): G[F, Unit] = f.sleep(time).liftM[G]
    }

}
