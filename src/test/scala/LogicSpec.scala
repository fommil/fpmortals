// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package tests

import algebra._
import cats._
import freestyle.implicits._
import logic._
import org.scalatest._
import org.scalatest.Matchers._

object IdInterpreters {

  implicit val drone: Drone.Services.Handler[Id] = new Drone.Services.Handler[Id] {
    import algebra.Drone._
    def getActiveWork: Id[WorkActive] = ???
    def getWorkQueue: Id[WorkQueue] = ???
  }

  implicit val machines: Machines.Services.Handler[Id] = new Machines.Services.Handler[Id] {
    import algebra.Machines._
    def getAlive: Id[Alive] = ???
    def getManaged: Id[Managed] = ???
    def getTime: Id[Time] = ???
    def start(node: Node): Id[Node] = ???
    def stop(node: Node): Id[Unit] = ???
  }

  implicit val audit: Audit.Services.Handler[Id] = new Audit.Services.Handler[Id] {
    //import Audit._
    def store(msg: String): Id[Unit] = ???
  }

  // WTF? Why does this take Op instead of Id?
  val impl = new DynamicAgents[Modules.Services.Op]
}

class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial state" in {
    //import IdInterpreters._

    //    impl.initial

  }

}
