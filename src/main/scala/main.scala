// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package app

import prelude._, Z._, S._

import logic._

object Main extends SafeApp {
  def run(args: List[String]): IO[Void, ExitStatus] = ???

  def step(D: DynAgents[Task]): StateT[Task, WorldView, Unit] =
    for {
      old     <- StateT.get[Task, WorldView]
      updated <- StateT.liftM(D.update(old))
      changed <- StateT.liftM(D.act(updated))
      _       <- StateT.put[Task, WorldView](changed)
      _       <- StateT.liftM(Task.sleep(10.seconds))
    } yield ()

  def loop(D: DynAgents[Task]): StateT[Task, WorldView, Unit] =
    BindRec[StateT[Task, WorldView, ?]].forever(step(D))

}
