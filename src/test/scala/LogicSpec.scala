// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package tests

import algebra.audit._
import algebra.drone._
import algebra.machines._
import cats._
import freestyle._
import freestyle.implicits._
import logic._
import logic.coproductk._
import org.scalatest._
import org.scalatest.Matchers._

object IdInterpreters {

  implicit val drone: Drone.Handler[Id] = new Drone.Handler[Id] {
    def getActiveWork: WorkActive = ???
    def getWorkQueue: WorkQueue = ???
  }

  implicit val machines: Machines.Handler[Id] = new Machines.Handler[Id] {
    def getAlive: Alive = ???
    def getManaged: Managed = ???
    def getTime: Time = ???
    def start(node: Node): Node = ???
    def stop(node: Node): Unit = ???
  }

  implicit val audit: Audit.Handler[Id] = new Audit.Handler[Id] {
    def store(msg: String): Unit = ???
  }

  val impl = new DynAgentsLogic[DynAgents.Op]
}

class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial state" in {
    import IdInterpreters._

    impl.initial.exec[Id] shouldBe a[State]
  }

}
