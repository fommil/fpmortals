// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package tests

import java.time.ZonedDateTime

import scala.Int
import scala.collection.immutable.Map

import scala.Predef.ArrowAssoc

import scalaz._
import Scalaz._
import org.scalatest._
import org.scalatest.Matchers._

import algebra._
import logic._

object Data {
  val node1   = MachineNode("1243d1af-828f-4ba3-9fc0-a19d86852b5a")
  val node2   = MachineNode("550c4943-229e-47b0-b6be-3d686c5f013f")
  val managed = NonEmptyList(node1, node2)

  import ZonedDateTime.parse
  val time1 = parse("2017-03-03T18:07:00.000+01:00[Europe/London]")
  val time2 = parse("2017-03-03T18:59:00.000+01:00[Europe/London]") // +52 mins
  val time3 = parse("2017-03-03T19:06:00.000+01:00[Europe/London]") // +59 mins
  val time4 = parse("2017-03-03T23:07:00.000+01:00[Europe/London]") // +5 hours

  val needsAgents = WorldView(5, 0, managed, Map.empty, Map.empty, time1)

}
import Data._

final class StaticHandlers(state: WorldView) {
  var started, stopped: Int = 0

  implicit val drone: Drone[Id] = new Drone[Id] {
    def getBacklog: Int = state.backlog
    def getAgents: Int  = state.agents
  }

  implicit val machines: Machines[Id] = new Machines[Id] {
    def getAlive: Map[MachineNode, ZonedDateTime] = state.alive
    def getManaged: NonEmptyList[MachineNode]     = state.managed
    def getTime: ZonedDateTime                    = state.time
    def start(node: MachineNode): MachineNode     = { started += 1; node }
    def stop(node: MachineNode): MachineNode      = { stopped += 1; node }
  }

  val program = new DynAgents[Id]
}

final class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial world view" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    program.initial shouldBe needsAgents
  }

  it should "request agents when needed" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    val expected = needsAgents.copy(
      pending = Map(node1 -> time1)
    )

    program.act(needsAgents) shouldBe expected

    handlers.stopped shouldBe 0
    handlers.started shouldBe 1
  }

  it should "not request agents when pending" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    val pending = needsAgents.copy(
      pending = Map(node1 -> time1)
    )

    program.act(pending) shouldBe pending

    handlers.stopped shouldBe 0
    handlers.started shouldBe 0
  }

  it should "don't shut down agents if nodes are too young" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    val world = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time2)

    program.act(world) shouldBe world

    handlers.stopped shouldBe 0
    handlers.started shouldBe 0
  }

  it should "shut down agents when there is no backlog and nodes will shortly incur new costs" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    val world    = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time3)
    val expected = world.copy(pending = Map(node1 -> time3))

    program.act(world) shouldBe expected

    handlers.stopped shouldBe 1
    handlers.started shouldBe 0
  }

  it should "not shut down agents if there are pending actions" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    val world =
      WorldView(0, 1, managed, Map(node1 -> time1), Map(node1 -> time3), time3)

    program.act(world) shouldBe world

    handlers.stopped shouldBe 0
    handlers.started shouldBe 0
  }

  it should "shut down agents when there is no backlog if they are too old" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    val world    = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time4)
    val expected = world.copy(pending = Map(node1 -> time4))

    program.act(world) shouldBe expected

    handlers.stopped shouldBe 1
    handlers.started shouldBe 0
  }

  it should "shut down agents, even if they are potentially doing work, if they are too old" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._

    val world    = WorldView(1, 1, managed, Map(node1 -> time1), Map.empty, time4)
    val expected = world.copy(pending = Map(node1 -> time4))

    program.act(world) shouldBe expected

    handlers.stopped shouldBe 1
    handlers.started shouldBe 0
  }

  it should "remove changed nodes from pending" in {
    val world    = WorldView(0, 0, managed, Map(node1 -> time3), Map.empty, time3)
    val handlers = new StaticHandlers(world)
    import handlers._

    val initial =
      world.copy(alive = Map.empty, pending = Map(node1 -> time2), time = time2)
    program.update(initial) shouldBe world // i.e. pending is gone

    handlers.stopped shouldBe 0
    handlers.started shouldBe 0
  }

  it should "ignore unresponsive pending actions during update" in {
    val world    = WorldView(0, 0, managed, Map.empty, Map(node1 -> time1), time2)
    val handlers = new StaticHandlers(world)
    import handlers._

    val initial  = world.copy(time = time1)
    val expected = world.copy(pending = Map.empty)

    program.update(initial) shouldBe expected

    handlers.stopped shouldBe 0
    handlers.started shouldBe 0
  }
}
