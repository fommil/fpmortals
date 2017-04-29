// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package algebra

import java.time.ZonedDateTime
import java.util.UUID

import cats.data.NonEmptyList
import freestyle._

object drone {
  // responses form a sealed family to make it easier to switch
  // between procedural / streaming uses of the API.

  sealed trait DroneResp
  case class Backlog(items: Int) extends DroneResp
  case class Agents(items: Int) extends DroneResp

  @free trait Drone {
    def getBacklog: FS[Backlog]
    def getAgents: FS[Agents]
  }
}

object machines {
  case class Node(id: UUID)

  sealed trait MachinesResp
  case class Time(time: ZonedDateTime) extends MachinesResp
  case class Managed(nodes: NonEmptyList[Node]) extends MachinesResp
  case class Alive(nodes: Map[Node, ZonedDateTime]) extends MachinesResp

  @free trait Machines {
    def getTime: FS[Time]
    def getManaged: FS[Managed]
    def getAlive: FS[Alive]
    def start(node: Node): FS[Unit]
    def stop(node: Node): FS[Unit]
  }
}
