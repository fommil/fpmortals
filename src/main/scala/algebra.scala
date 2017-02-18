// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package algebra

import java.util.UUID

object Timing {
  sealed trait Ops[A]
  case class GetTime() extends Ops[String]
}

object Drone {
  sealed trait Ops[A]
  case class ReceiveWorkQueue() extends Ops[Int] // will be push
  case class ReceiveActiveWork() extends Ops[Int] // will be push
}

object Container {
  sealed trait Ops[A]
  case class GetTime() extends Ops[String]
  case class GetNodes() extends Ops[List[String]]
  case class StartAgent() extends Ops[UUID]
  case class StopAgent(uuid: UUID) extends Ops[Unit]

  case class ReceiveKillEvent() extends Ops[UUID] // will be push
}

object Audit {
  case class Auditable()

  sealed trait Ops[A]
  case class StoreEvent(e: Auditable) extends Ops[Unit]
}

