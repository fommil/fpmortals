// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package interpreters

import prelude._
import algebra._

final class DroneModule[F[_]](
  H: OAuth2JsonHttpClient[F]
) extends Drone[F] {
  def getAgents: F[Int]  = ???
  def getBacklog: F[Int] = ???
}
