// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package tests

import algebra.drone._
import algebra.machines._
import cats._
import cats.data.NonEmptyList
import freestyle._
import freestyle.implicits._
import java.time.ZonedDateTime
import java.util.UUID
import logic._
import logic.coproductk._
import org.scalatest._
import org.scalatest.Matchers._

object Data {
  val node1 = Node(UUID.fromString("1243d1af-828f-4ba3-9fc0-a19d86852b5a"))
  val node2 = Node(UUID.fromString("550c4943-229e-47b0-b6be-3d686c5f013f"))

  val time1 = ZonedDateTime.parse("2017-03-28T19:07:31.863+01:00[Europe/London]")
}
import Data._

final case class StaticInterpreters(state: State) {
  implicit val drone: Drone.Handler[Id] = new Drone.Handler[Id] {
    def getBacklog: Backlog = Backlog(state.backlog)
    def getAgents: Agents = Agents(state.agents)
  }

  implicit val machines: Machines.Handler[Id] = new Machines.Handler[Id] {
    def getAlive: Alive = Alive(state.alive)
    def getManaged: Managed = Managed(state.managed)
    def getTime: Time = Time(state.time)
    def start(node: Node): Unit = ()
    def stop(node: Node): Unit = ()
  }
}

class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial state" in {
    val state = State(5, 0, NonEmptyList(node1, Nil), Map.empty, Map.empty, time1)

    val interpreters = StaticInterpreters(state)
    import interpreters._

    DynAgentsLogic[DynAgents.Op].initial.exec[Id] shouldBe state
  }

}
