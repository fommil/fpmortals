// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package tests

import std._, S._

import contextual.datetime._
import org.scalatest._
import org.scalatest.Matchers._

import algebra._
import logic._

object Data {
  val node1: MachineNode                 = MachineNode("1243d1af-828f-4ba3-9fc0-a19d86852b5a")
  val node2: MachineNode                 = MachineNode("550c4943-229e-47b0-b6be-3d686c5f013f")
  val managed: NonEmptyList[MachineNode] = NonEmptyList(node1, node2)

  val time1: Instant = instant"2017-03-03T18:07:00Z"
  val time2: Instant = instant"2017-03-03T18:59:00Z" // +52 mins
  val time3: Instant = instant"2017-03-03T19:06:00Z" // +59 mins
  val time4: Instant = instant"2017-03-03T23:07:00Z" // +5 hours

  val needsAgents: WorldView =
    WorldView(5, 0, managed, Map.empty, Map.empty, time1)

  val hungry: World =
    World(5, 0, managed, Map.empty, Set.empty, Set.empty, time1)

}
import Data._

// fully describes the world, as viewed by WorldView
final case class World(
  backlog: Int,
  agents: Int,
  managed: NonEmptyList[MachineNode],
  alive: Map[MachineNode, Instant],
  started: Set[MachineNode],
  stopped: Set[MachineNode],
  time: Instant
)

object StateHandlers {
  type F[a] = State[World, a]
  import State.{ get, modify }

  // could also increment the time on every call
  implicit val drone: Drone[F] = new Drone[F] {
    def getBacklog: F[Int] = get.map(_.backlog)
    def getAgents: F[Int]  = get.map(_.agents)
  }

  implicit val machines: Machines[F] = new Machines[F] {
    def getAlive: F[Map[MachineNode, Instant]]   = get.map(_.alive)
    def getManaged: F[NonEmptyList[MachineNode]] = get.map(_.managed)
    def getTime: F[Instant]                      = get.map(_.time)

    // will rewrite to use lenses later...
    def start(node: MachineNode): F[Unit] =
      modify(w => w.copy(started = w.started + node))
    def stop(node: MachineNode): F[Unit] =
      modify(w => w.copy(stopped = w.stopped + node))
  }

  val program: DynAgents[F] = new DynAgents[F]
}

object ConstHandlers {
  type F[a] = Const[String, a]

  implicit val drone: Drone[F] = new Drone[F] {
    def getBacklog: F[Int] = Const("backlog")
    def getAgents: F[Int]  = Const("agents")
  }

  implicit val machines: Machines[F] = new Machines[F] {
    def getAlive: F[Map[MachineNode, Instant]]   = Const("alive")
    def getManaged: F[NonEmptyList[MachineNode]] = Const("managed")
    def getTime: F[Instant]                      = Const("time")
    def start(node: MachineNode): F[Unit]        = Const("start")
    def stop(node: MachineNode): F[Unit]         = Const("stop")
  }

  val program: DynAgents[F] = new DynAgents[F]

}

final class LogicSpec extends FlatSpec {
  import StateHandlers.program.{ act, initial, update }

  "Business Logic" should "generate an initial world view" in {
    val world1          = hungry
    val (world2, view2) = initial.run(world1)

    world2.shouldBe(world1)
    view2.shouldBe(needsAgents)
  }

  it should "request agents when needed" in {
    val world1          = hungry
    val view1           = needsAgents
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1.copy(pending = Map(node1 -> time1)))

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(Set(node1))
  }

  it should "not request agents when pending" in {
    val world1          = hungry
    val view1           = needsAgents.copy(pending = Map(node1 -> time1))
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1)

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "don't shut down agents if nodes are too young" in {
    val world1          = hungry
    val view1           = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time2)
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1)

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "shut down agents when there is no backlog and nodes will shortly incur new costs" in {
    val world1          = hungry
    val view1           = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time3)
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1.copy(pending = Map(node1 -> time3)))

    world2.stopped.shouldBe(Set(node1))
    world2.started.shouldBe(world1.started)
  }

  it should "not shut down agents if there are pending actions" in {
    val world1 = hungry
    val view1 =
      WorldView(0, 1, managed, Map(node1 -> time1), Map(node1 -> time3), time3)

    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1)

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "shut down agents when there is no backlog if they are too old" in {
    val world1          = hungry
    val view1           = WorldView(0, 1, managed, Map(node1 -> time1), Map.empty, time4)
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1.copy(pending = Map(node1 -> time4)))

    world2.stopped.shouldBe(Set(node1))
    world2.started.shouldBe(world1.started)
  }

  it should "shut down agents, even if they are potentially doing work, if they are too old" in {
    val old           = WorldView(1, 1, managed, Map(node1 -> time1), Map.empty, time4)
    val (world, view) = act(old).run(hungry)

    view.shouldBe(old.copy(pending = Map(node1 -> time4)))

    world.stopped.shouldBe(Set(node1))
    world.started.size.shouldBe(0)
  }

  it should "remove changed nodes from pending" in {
    val world1 =
      World(0, 0, managed, Map(node1 -> time3), Set.empty, Set.empty, time3)

    val view1 = WorldView(0, 0, managed, Map.empty, Map(node1 -> time2), time2)

    val (world2, view2) = update(view1).run(world1)

    view2.shouldBe(
      view1.copy(alive = world1.alive, pending = Map.empty, time = time3)
    )

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world2.started)
  }

  it should "ignore unresponsive pending actions during update" in {
    val view1  = WorldView(0, 0, managed, Map.empty, Map(node1 -> time1), time1)
    val world1 = World(0, 0, managed, Map.empty, Set(node1), Set.empty, time2)

    val (world2, view2) = update(view1).run(world1)

    view2.shouldBe(view1.copy(pending = Map.empty, time = time2))

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "call the expected methods" in {
    import ConstHandlers._

    val world1 = WorldView(1,
                           1,
                           managed,
                           Map(node1 -> time1, node2 -> time1),
                           Map.empty,
                           time4)

    program.act(world1).getConst.shouldBe("stopstop")
  }

  it should "monitor stopped nodes" in {
    val world1 = hungry
    val view1 = WorldView(1,
                          1,
                          managed,
                          Map(node1 -> time1, node2 -> time1),
                          Map.empty,
                          time4)

    val monitored       = new Monitored(StateHandlers.program)
    val (world2, view2) = monitored.act(view1).run(world1)

    world2.stopped.shouldBe(Set(node1, node2))

    val expected = view1.copy(pending = Map(node1 -> time4, node2 -> time4))
    view2.shouldBe(expected -> Set(node1, node2))
  }

}
