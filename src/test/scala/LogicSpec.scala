// Copyright (C) 2017 Sam Halliday
// License: http://www.gnu.org/licenses/gpl.html

import org.scalatest._
import org.scalatest.Matchers._

import cats._
import cats.free._

import algebra._
import logic._

import freestyle.implicits._

object IdInterpreters {

  implicit def drone = new Drone.Services.Handler[Id] { }

  implicit def machines = new Machines.Services.Handler[Id] { }

  implicit def audit = new Audit.Services.Handler[Id] {  }

//  import Modules.Services._
//  import Modules.DynamicDroneDeps._

//  import Drone.Services._
//  import Machines.Services._
//  import Audit.Services._

  val impl = new DynamicAgents[Id]
}

class LogicSpec extends FlatSpec {

  "Business Logic" should "generate an initial state" in {
    import IdInterpreters._


//    impl.initial

  }

}
