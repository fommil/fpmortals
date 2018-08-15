// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda

import prelude._, Z._

import scala.collection.immutable.List

import org.http4s.client.blaze.BlazeClientConfig

import logic._
import interpreters._
import http._
import http.interpreters._
import http.oauth2._
import DynAgents.liftTask

object Main extends SafeApp {

  def readToken: Task[RefreshToken] = ???

  def run(args: List[String]): IO[Void, ExitStatus] = {
    type F[a] = StateT[Task, WorldView, a]
    val F: MonadState[F, WorldView] = MonadState[F, WorldView]

    for {
      token  <- readToken
      client <- BlazeJsonClient(BlazeClientConfig.defaultConfig)
      oauth  = new AuthJsonClientModule[Task](token)(client)
      agents = new DynAgentsModule[Task](
        new DroneModule(oauth),
        new MachinesModule(oauth)
      )
      start <- agents.initial
      _ <- {
        for {
          old     <- F.get
          updated <- liftTask[F](agents).update(old)
          changed <- liftTask[F](agents).act(updated)
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
