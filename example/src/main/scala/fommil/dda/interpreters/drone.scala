// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda
package interpreters

import prelude._
import algebra._
import http._

final class DroneModule[F[_]](
  @unused H: OAuth2JsonClient[F]
) extends Drone[F] {
  def getAgents: F[Int]  = ???
  def getBacklog: F[Int] = ???
}
