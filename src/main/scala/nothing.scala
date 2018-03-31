// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package nothing

import scalaz._

// trying to show when invariance beats covariance for local type inference
final case class Foos(v: List[String])
final case class Bars(v: IList[String])

object Main {
  Foos(List.empty[Nothing])

  Bars(IList.empty)

}
