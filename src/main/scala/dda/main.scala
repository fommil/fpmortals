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
    else agents(BearerToken("<invalid>", Epoch(0)))
  }.attempt[Void].map {
    case \/-(_) => ExitStatus.ExitNow(0)
    case -\/(_) => ExitStatus.ExitNow(1)
  }

  // performs the OAuth 2.0 dance to obtain refresh tokens
  def auth: Task[Unit] = ???

  // runs the app, requires that refresh tokens are provided
  def agents(bearer: BearerToken): Task[Unit] = {
    type HT[f[_], a] = EitherT[f, JsonClient.Error, a]
    type H[a]        = HT[Task, a]

    type GT[f[_], a] = StateT[f, BearerToken, a]
    type G[a]        = GT[H, a]

    val T: LocalClock[G] =
      LocalClock.liftM(LocalClock.liftM(new LocalClockTask))

    for {
      config   <- readConfig.liftM[HT].liftM[GT]
      blaze    <- BlazeJsonClient(config.blaze).liftM[HT].liftM[GT]
      hblaze   = JsonClient.liftM[H, GT](blaze)
      drone    = new DroneModule(oauth[G](config.drone)(hblaze, T))
      machines = new MachinesModule(oauth[G](config.machines)(hblaze, T))
      agents   = new DynAgentsModule(drone, machines)
      start    <- agents.initial
      _ <- {
        type FT[f[_], a] = StateT[f, WorldView, a]
        type F[a]        = FT[G, a]
        val F: MonadState[F, WorldView] = MonadState[F, WorldView]
        val A: DynAgents[F]             = DynAgents.liftM(agents)
        val S: Sleep[F] =
          Sleep.liftM(Sleep.liftM(Sleep.liftM(new SleepTask)))

        for {
          old     <- F.get
          updated <- A.update(old)
          changed <- A.act(updated)
          _       <- F.put(changed)
          _       <- S.sleep(10.seconds)
        } yield ()
      }.forever[Unit].run(start)
    } yield ()
  }.run(bearer).run.flatMap {
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
    M: MonadError[M, JsonClient.Error],
    S: MonadState[M, BearerToken]
  ): AuthJsonClient[M] =
    new AuthJsonClientModule[M](config.token)(
      H,
      new RefreshModule(config.server)(H, T)(M)
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
