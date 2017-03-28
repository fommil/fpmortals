// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package tests

import algebra._
import cats._
import freestyle._
import freestyle.implicits._
import logic._
import org.scalatest._
import org.scalatest.Matchers._

object IdInterpreters {

  implicit val drone: Drone.Services.Handler[Id] = new Drone.Services.Handler[Id] {
    import algebra.Drone._
    def getActiveWork: WorkActive = ???
    def getWorkQueue: WorkQueue = ???
  }

  implicit val machines: Machines.Services.Handler[Id] = new Machines.Services.Handler[Id] {
    import algebra.Machines._
    def getAlive: Alive = ???
    def getManaged: Managed = ???
    def getTime: Time = ???
    def start(node: Node): Node = ???
    def stop(node: Node): Unit = ???
  }

  implicit val audit: Audit.Services.Handler[Id] = new Audit.Services.Handler[Id] {
    //import Audit._
    def store(msg: String): Unit = ???
  }

  val impl = new DynamicAgents[Modules.Services.Op]
}

class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial state" in {
    import IdInterpreters._

    impl.initial.exec[Id] shouldBe a[State]
  }

}
