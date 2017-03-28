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
  val managed = NonEmptyList(node1, List(node2))

  val time1 = ZonedDateTime.parse("2017-03-03T18:07:00.000+01:00[Europe/London]")
  val time2 = ZonedDateTime.parse("2017-03-03T18:59:00.000+01:00[Europe/London]") // +52 mins
  val time3 = ZonedDateTime.parse("2017-03-03T19:06:00.000+01:00[Europe/London]") // +59 mins
  val time4 = ZonedDateTime.parse("2017-03-03T23:07:00.000+01:00[Europe/London]") // +5 hours

  val needsAgents = WorldView(5, 0, managed, Map.empty, Map.empty, time1)

}
import Data._

final case class StaticInterpreters(state: WorldView) {
  var started, stopped: Int = 0

  implicit val drone: Drone.Handler[Id] = new Drone.Handler[Id] {
    def getBacklog: Backlog = Backlog(state.backlog)
    def getAgents: Agents = Agents(state.agents)
  }

  implicit val machines: Machines.Handler[Id] = new Machines.Handler[Id] {
    def getAlive: Alive = Alive(state.alive)
    def getManaged: Managed = Managed(state.managed)
    def getTime: Time = Time(state.time)
    def start(node: Node): Unit = started += 1
    def stop(node: Node): Unit = stopped += 1
  }

  val program = DynAgentsLogic[DynAgents.Op]
}

class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial world view" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    program.initial.exec[Id] shouldBe needsAgents
  }

  it should "request agents when needed" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    val expected = needsAgents.copy(
      pending = Map(node1 -> time1)
    )

    program.act(needsAgents).exec[Id] shouldBe expected

    interpreters.stopped shouldBe 0
    interpreters.started shouldBe 1
  }

  it should "not request agents when pending" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    val pending = needsAgents.copy(
      pending = Map(node1 -> time1)
    )

    program.act(pending).exec[Id] shouldBe pending

    interpreters.stopped shouldBe 0
    interpreters.started shouldBe 0
  }

  it should "don't shut down agents if nodes are too young" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    val world = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time2)

    program.act(world).exec[Id] shouldBe world

    interpreters.stopped shouldBe 0
    interpreters.started shouldBe 0
  }

  it should "shut down agents when there is no backlog and nodes will shortly incur new costs" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    val world = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time3)
    val expected = world.copy(pending = Map(node1 -> time3))

    program.act(world).exec[Id] shouldBe expected

    interpreters.stopped shouldBe 1
    interpreters.started shouldBe 0
  }

  it should "not shut down agents if there are pending actions" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    val world = WorldView(0, 1, managed, Map(node1 -> time1), Map(node1 -> time3), time3)

    program.act(world).exec[Id] shouldBe world

    interpreters.stopped shouldBe 0
    interpreters.started shouldBe 0
  }

  it should "shut down agents when there is no backlog if they are too old" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    val world = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time4)
    val expected = world.copy(pending = Map(node1 -> time4))

    program.act(world).exec[Id] shouldBe expected

    interpreters.stopped shouldBe 1
    interpreters.started shouldBe 0
  }

  it should "shut down agents, even if they are potentially doing work, if they are too old" in {
    val interpreters = StaticInterpreters(needsAgents)
    import interpreters._

    val world = WorldView(1, 1, managed, Map(node1 -> time1), Map.empty, time4)
    val expected = world.copy(pending = Map(node1 -> time4))

    program.act(world).exec[Id] shouldBe expected

    interpreters.stopped shouldBe 1
    interpreters.started shouldBe 0
  }

}
