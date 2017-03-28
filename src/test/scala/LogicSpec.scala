// Copyright (C) 2017 Sam Halliday
// License: http://www.gnu.org/licenses/gpl.html

import org.scalatest._
import org.scalatest.Matchers._

import cats._
import cats.free._

import algebra._
import logic._

object IdInterpreters {

  implicit def drone: Drone.Services.Op ~> Id = new (Drone.Services.Op ~> Id) {
    def apply[A](fa: Drone.Services.Op[A]): Id[A] = fa match {
      case _ => ???
    }
  }

  implicit def machines: Machines.Services.Op ~> Id = new (Machines.Services.Op ~> Id) {
    def apply[A](fa: Machines.Services.Op[A]): Id[A] = fa match {
      case _ => ???
    }
  }

  implicit def audit: Audit.Services.Op ~> Id = new (Audit.Services.Op ~> Id) {
    def apply[A](fa: Audit.Services.Op[A]): Id[A] = fa match {
      case _ => ???
    }
  }

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
