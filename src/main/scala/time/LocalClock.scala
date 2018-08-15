// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package time

trait LocalClock[F[_]] {
  def now: F[Epoch]
}
