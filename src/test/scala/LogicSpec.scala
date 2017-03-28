// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package tests

import algebra.audit._
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

object NeedsAgents {
  implicit val drone: Drone.Handler[Id] = new Drone.Handler[Id] {
    def getActiveWork: WorkActive = WorkActive(1)
    def getWorkQueue: WorkQueue = WorkQueue(5)
  }

  implicit val machines: Machines.Handler[Id] = new Machines.Handler[Id] {
    def getAlive: Alive = Alive(Map.empty)
    def getManaged: Managed = Managed(NonEmptyList(node1, Nil))
    def getTime: Time = Time(time1)
    def start(node: Node): Unit = ()
    def stop(node: Node): Unit = ()
  }

  implicit val audit: Audit.Handler[Id] = new Audit.Handler[Id] {
    def store(msg: String): Unit = ???
  }
}

class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial state" in {
    import NeedsAgents._

    new DynAgentsLogic[DynAgents.Op].initial.exec[Id] shouldBe a[State]
  }

}
