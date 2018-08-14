// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package interpreters

import prelude._
import algebra._
import time._

final class MachinesModule[F[_]](
  H: OAuth2JsonHttpClient[F]
) extends Machines[F] {

  def getAlive: F[MachineNode ==>> Epoch]      = ???
  def getManaged: F[NonEmptyList[MachineNode]] = ???
  def getTime: F[Epoch]                        = ???
  def start(node: MachineNode): F[Unit]        = ???
  def stop(node: MachineNode): F[Unit]         = ???

}
