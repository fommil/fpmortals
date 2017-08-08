// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package algebra

import java.lang.String
import java.time.ZonedDateTime

import scala.Int
import scala.collection.immutable.Map
import scala.language.higherKinds

import cats.data.NonEmptyList
import freestyle._

package drone {
  @free trait Drone {
    def getBacklog: FS[Int]
    def getAgents: FS[Int]
  }
}

package machines {
  final case class Node(id: String)

  @free trait Machines {
    def getTime: FS[ZonedDateTime]
    def getManaged: FS[NonEmptyList[Node]]
    def getAlive: FS[Map[Node, ZonedDateTime]]
    def start(node: Node): FS[Node]
    def stop(node: Node): FS[Node]
  }
}
