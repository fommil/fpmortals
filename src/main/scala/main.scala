// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package app

import prelude._, Z._, S._

import logic._
import interpreters._

object Main extends SafeApp {

  def run(args: List[String]): IO[Void, ExitStatus] = {
    type F[a] = StateT[Task, WorldView, a]
    val F: MonadState[F, WorldView] = MonadState[F, WorldView]

    val AgentsTask: DynAgents[Task] = new DynAgentsModule(
      new DroneModule[Task],
      new MachinesModule[Task]
    )
    val Agents: DynAgents[F] = DynAgents.liftIO(AgentsTask)

    for {
      start <- AgentsTask.initial
      _ <- {
        for {
          old     <- F.get
          updated <- Agents.update(old)
          changed <- Agents.act(updated)
          _       <- F.put(changed)
          _       <- StateT.liftM(Task.sleep(10.seconds))
        } yield ()
      }.forever[Unit].run(start)
    } yield ()
  }.attempt[Void].map {
    case \/-(_) => ExitStatus.ExitNow(0)
    case -\/(_) => ExitStatus.ExitNow(1)
  }

}
