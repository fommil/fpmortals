// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package time

import prelude._, Z._
import org.scalatest._
import org.scalatest.Matchers._

class EpochSpec extends FlatSpec {

  val time1: Epoch = epoch"2017-03-03T18:07:00Z"
  val time2: Epoch = epoch"2017-03-03T18:59:00Z"

  "Epoch" should "calculate time differences" in {
    (time1 + 52.minutes).shouldBe(time2)

    (time2 - time1).shouldBe(52.minutes)

    (time2 - 52.minutes).shouldBe(time1)
  }
}
