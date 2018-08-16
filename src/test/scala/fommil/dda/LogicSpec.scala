// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda

import prelude._, Z._

import fommil.time._
import org.scalatest._
import org.scalatest.Matchers._

import algebra._
import logic._

object Data {
  val node1: MachineNode                 = MachineNode("1243d1af-828f-4ba3-9fc0-a19d86852b5a")
  val node2: MachineNode                 = MachineNode("550c4943-229e-47b0-b6be-3d686c5f013f")
  val managed: NonEmptyList[MachineNode] = NonEmptyList(node1, node2)

  val time1: Epoch = epoch"2017-03-03T18:07:00Z"
  val time2: Epoch = epoch"2017-03-03T18:59:00Z" // +52 mins
  val time3: Epoch = epoch"2017-03-03T19:06:00Z" // +59 mins
  val time4: Epoch = epoch"2017-03-03T23:07:00Z" // +5 hours

  val needsAgents: WorldView =
    WorldView(5, 0, managed, IMap.empty, IMap.empty, time1)

  val hungry: World =
    World(5, 0, managed, IMap.empty, ISet.empty, ISet.empty, time1)

}
import Data._

// fully describes the world, as viewed by WorldView
final case class World(
  backlog: Int,
  agents: Int,
  managed: NonEmptyList[MachineNode],
  alive: MachineNode ==>> Epoch,
  started: ISet[MachineNode],
  stopped: ISet[MachineNode],
  time: Epoch
)

object StateImpl {
  type F[a] = State[World, a]
  import State.{ get, modify }

  // could also increment the time on every call
  private val D: Drone[F] = new Drone[F] {
    def getBacklog: F[Int] = get.map(_.backlog)
    def getAgents: F[Int]  = get.map(_.agents)
  }

  private val M: Machines[F] = new Machines[F] {
    def getAlive: F[MachineNode ==>> Epoch]      = get.map(_.alive)
    def getManaged: F[NonEmptyList[MachineNode]] = get.map(_.managed)
    def getTime: F[Epoch]                        = get.map(_.time)

    // will rewrite to use lenses later...
    def start(node: MachineNode): F[Unit] =
      modify(w => w.copy(started = w.started.insert(node)))
    def stop(node: MachineNode): F[Unit] =
      modify(w => w.copy(stopped = w.stopped.insert(node)))
  }

  val program: DynAgents[F] = new DynAgentsModule[F](D, M)
}

object ConstImpl {
  type F[a] = Const[String, a]

  private val D: Drone[F] = new Drone[F] {
    def getBacklog: F[Int] = Const("backlog")
    def getAgents: F[Int]  = Const("agents")
  }

  private val M: Machines[F] = new Machines[F] {
    def getAlive: F[MachineNode ==>> Epoch]      = Const("alive")
    def getManaged: F[NonEmptyList[MachineNode]] = Const("managed")
    def getTime: F[Epoch]                        = Const("time")
    def start(node: MachineNode): F[Unit]        = Const("start")
    def stop(node: MachineNode): F[Unit]         = Const("stop")
  }

  val program: DynAgents[F] = new DynAgentsModule[F](D, M)

}

final class LogicSpec extends FlatSpec {
  import StateImpl.program.{ act, initial, update }

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

    view2.shouldBe(view1.copy(pending = IMap.singleton(node1, time1)))

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(ISet.singleton(node1))
  }

  it should "not request agents when pending" in {
    val world1          = hungry
    val view1           = needsAgents.copy(pending = IMap.singleton(node1, time1))
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1)

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "don't shut down agents if nodes are too young" in {
    val world1 = hungry
    val view1 =
      WorldView(0, 1, managed, IMap.singleton(node1, time1), IMap.empty, time2)
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1)

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "shut down agents when there is no backlog and nodes will shortly incur new costs" in {
    val world1 = hungry
    val view1 =
      WorldView(0, 1, managed, IMap.singleton(node1, time1), IMap.empty, time3)
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1.copy(pending = IMap.singleton(node1, time3)))

    world2.stopped.shouldBe(ISet.singleton(node1))
    world2.started.shouldBe(world1.started)
  }

  it should "not shut down agents if there are pending actions" in {
    val world1 = hungry
    val view1 =
      WorldView(
        0,
        1,
        managed,
        IMap.singleton(node1, time1),
        IMap.singleton(node1, time3),
        time3
      )

    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1)

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "shut down agents when there is no backlog if they are too old" in {
    val world1 = hungry
    val view1 =
      WorldView(0, 1, managed, IMap.singleton(node1, time1), IMap.empty, time4)
    val (world2, view2) = act(view1).run(world1)

    view2.shouldBe(view1.copy(pending = IMap.singleton(node1, time4)))

    world2.stopped.shouldBe(ISet.singleton(node1))
    world2.started.shouldBe(world1.started)
  }

  it should "shut down agents, even if they are potentially doing work, if they are too old" in {
    val old =
      WorldView(1, 1, managed, IMap.singleton(node1, time1), IMap.empty, time4)
    val (world, view) = act(old).run(hungry)

    view.shouldBe(old.copy(pending = IMap.singleton(node1, time4)))

    world.stopped.shouldBe(ISet.singleton(node1))
    world.started.size.shouldBe(0)
  }

  it should "remove changed nodes from pending" in {
    val world1 =
      World(
        0,
        0,
        managed,
        IMap.singleton(node1, time3),
        ISet.empty,
        ISet.empty,
        time3
      )

    val view1 =
      WorldView(0, 0, managed, IMap.empty, IMap.singleton(node1, time2), time2)

    val (world2, view2) = update(view1).run(world1)

    view2.shouldBe(
      view1.copy(alive = world1.alive, pending = IMap.empty, time = time3)
    )

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world2.started)
  }

  it should "ignore unresponsive pending actions during update" in {
    val view1 =
      WorldView(0, 0, managed, IMap.empty, IMap.singleton(node1, time1), time1)
    val world1 =
      World(0, 0, managed, IMap.empty, ISet.singleton(node1), ISet.empty, time2)

    val (world2, view2) = update(view1).run(world1)

    view2.shouldBe(view1.copy(pending = IMap.empty, time = time2))

    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(world1.started)
  }

  it should "call the expected methods" in {
    import ConstImpl._

    val world1 = WorldView(
      1,
      1,
      managed,
      IMap.singleton(node1, time1).insert(node2, time1),
      IMap.empty,
      time4
    )

    program.act(world1).getConst.shouldBe("stopstop")
  }

  it should "monitor stopped nodes" in {
    val world1 = hungry
    val view1 = WorldView(
      1,
      1,
      managed,
      IMap.singleton(node1, time1).insert(node2, time1),
      IMap.empty,
      time4
    )

    val monitored       = new Monitored(StateImpl.program)
    val (world2, view2) = monitored.act(view1).run(world1)

    world2.stopped.shouldBe(ISet.singleton(node1).insert(node2))

    val expected =
      view1.copy(pending = IMap.singleton(node1, time4).insert(node2, time4))
    view2.shouldBe(expected -> ISet.singleton(node1).insert(node2))
  }

}
