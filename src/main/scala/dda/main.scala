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
import time._

object Main extends SafeApp {

  def run(args: List[String]): IO[Void, ExitStatus] = {
    if (args.contains("--auth")) auth
    else agents
  }.attempt[Void].map {
    case \/-(_) => ExitStatus.ExitNow(0)
    case -\/(_) => ExitStatus.ExitNow(1)
  }

  // performs the OAuth 2.0 dance to obtain refresh tokens
  def auth: Task[Unit] = ???

  // runs the app, requires that refresh tokens are provided
  def agents: Task[Unit] = {
    type GT[f[_], a] = EitherT[f, JsonClient.Error, a]
    type G[a]        = GT[Task, a]
    val T = LocalClock.liftM[Task, GT](new LocalClockTask)

    for {
      config   <- EitherT.rightT(readConfig)
      blaze    <- EitherT.rightT(BlazeJsonClient(config.blaze))
      drone    = new DroneModule(oauth[G](config.drone)(blaze, T))
      machines = new MachinesModule(oauth[G](config.machines)(blaze, T))
      agents   = new DynAgentsModule(drone, machines)
      start    <- agents.initial
      _ <- {
        type FT[f[_], a] = StateT[f, WorldView, a]
        type F[a]        = FT[G, a]
        val F = MonadState[F, WorldView]
        val A = DynAgents.liftM[G, FT](agents)
        val S: Sleep[F] = Sleep.liftM[G, FT](
          Sleep.liftM[Task, GT](new SleepTask)
        )

        for {
          old     <- F.get
          updated <- A.update(old)
          changed <- A.act(updated)
          _       <- F.put(changed)
          _       <- S.sleep(10.seconds)
        } yield ()
      }.forever[Unit].run(start)
    } yield ()
  }.run.flatMap {
    case -\/(_) => Task.fail(new Exception("HTTP server badness"))
    case \/-(_) => Task.now(())
  }

  private def oauth[M[_]](
    config: OAuth2Config
  )(
    H: JsonClient[M],
    T: LocalClock[M]
  )(
    implicit
    M: MonadError[M, JsonClient.Error]
  ): AuthJsonClient[M] =
    new AuthJsonClientModule[M](config.token)(
      H,
      new RefreshModule(config.server)(H, T)
    )

  final case class OAuth2Config(
    token: RefreshToken,
    server: ServerConfig
  )
  final case class Config(
    drone: OAuth2Config,
    machines: OAuth2Config,
    blaze: BlazeClientConfig
  )
  def readConfig: Task[Config] = ???
}
