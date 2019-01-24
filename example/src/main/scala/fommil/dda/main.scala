// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda

import prelude._
import Z._
import com.github.pshirshov.izumi.distage.planning.extensions.GraphDumpBootstrapModule
import distage._
import fommil.dda.algebra.{Drone, Machines}

import scala.collection.immutable.List
import pureconfig.orphans.readConfig
import scalaz.ioeffect.console._
import logic._
import interpreters._
import http._
import http.interpreters._
import http.oauth2._
import http.oauth2.interpreters._
import scalaz.Monad
import scalaz.ioeffect.IO
import shapeless.{:+:, CNil}
import time._

trait ErrorTransformer

object Main extends SafeApp {

  def run(args: List[String]): IO[Void, ExitStatus] = {
    if (args.contains("--machines")) auth("machines")
    else agentsDI(BearerToken("<invalid>", Epoch(0)))
  }.attempt[Void].map {
    case \/-(_) => ExitStatus.ExitNow(0)
    case -\/(err) =>
      java.lang.System.err.println(err)
      ExitStatus.ExitNow(1)
  }

  // performs the OAuth 2.0 dance to obtain refresh tokens
  def auth(name: String): Task[Unit] = {
    type HT[f[_], a] = EitherT[f, JsonClient.Error, a]
    type H[a]        = HT[Task, a]

    for {
      config    <- readConfig[ServerConfig](name + ".server").liftM[HT]
      ui        <- BlazeUserInteraction().liftM[HT]
      auth      = new AuthModule(config)(ui)
      codetoken <- auth.authenticate.liftM[HT]
      clock     = LocalClock.liftM[Task, HT](new LocalClockTask)
      client    <- BlazeJsonClient[H].liftM[HT]
      access    = new AccessModule(config)(client, clock)
      token     <- access.access(codetoken)
      _         <- putStrLn(z"got token: ${token._1}").toTask.liftM[HT]
    } yield ()
  }.run.swallowError

  type JErr = JsonClient.Error :+: Throwable :+: CNil
  type JIO[A] = IO[JErr, A]

  def unliftMonadError[F[_], E <: Cpr: Inj[?, E1]: Sel[?, E1], E1](implicit me: MonadError[F, E]): MonadError[F, E1] = new MonadError[F, E1] {
    override def raiseError[A](e: E1): F[A] = me.raiseError[A](Cpr[E].apply(e))
    override def handleError[A](fa: F[A])(f: E1 => F[A]): F[A] = me.handleError(fa) {
      c => c.select[E1].fold(me.raiseError[A](c))(f)
    }
    override def point[A](a: => A): F[A] = me.point(a)
    override def bind[A, B](fa: F[A])(f: A => F[B]): F[B] = me.bind(fa)(f)
  }

  def unliftMonadIO[E <: Cpr: Inj[?, E1], E1](implicit mio: MonadIO[IO[E, ?], E]): MonadIO[IO[E, ?], E1] = new MonadIO[IO[E, ?], E1] {
    override def liftIO[A](io: IO[E1, A])(implicit M: Monad[IO[E, ?]]): IO[E, A] = io.liftErr
  }

  def refState[E, A](a: A): Task[MonadState[IO[E, ?], A]] = IORef(a).map { ref =>
    new MonadState[IO[E, ?], A] {
      override def put(s: A): IO[E, Unit] = ref.write[E](s)
      override def get: IO[E, A] = ref.read[E]
      override def bind[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] = fa.flatMap(f)
      override def point[A](a: => A): IO[E, A] = IO.point(a)
      override def init: IO[E, A] = get
    }
  }

  import eu.timepit.refined.auto._

  def agentsDI(bearer: BearerToken): Task[Unit] = {
    implicit val meJIO: MonadError[JIO, JsonClient.Error] = unliftMonadError[JIO, JErr, JsonClient.Error]
    implicit val mIO: MonadIO[JIO, Throwable] = unliftMonadIO[JErr, Throwable]

    for {
      bearerState <- refState[JErr, BearerToken](bearer)
      // Your config aint reading
      // config <- readConfig[AppConfig]
      config = AppConfig(bearer, OAuth2Config(RefreshToken("adfg"), ServerConfig("https://abc.com/", "https://abc.com/", "https://abc.com/", "", "", "")))
      blaze <- BlazeJsonClient[JIO](meJIO, mIO)

      module =
        new ModuleDef {
          make[MonadState[JIO, BearerToken]].from(bearerState)
          make[JsonClient[JIO]].from(blaze)
          make[AppConfig].from(config)
          make[ServerConfig].from((_: AppConfig).machines.server)
          make[RefreshToken].from((_: AppConfig).machines.token)

          addImplicit[Applicative[JIO]]
          addImplicit[Monad[JIO]]
          addImplicit[MonadIO[JIO, Throwable]]
          addImplicit[MonadError[JIO, JErr]]
          addImplicit[MonadError[JIO, JsonClient.Error]]

          make[LocalClock[Task]].from[LocalClockTask]
          make[LocalClock[JIO]].from(LocalClock.liftErr[JErr](_))
          make[Sleep[Task]].from[SleepTask]
          make[Sleep[JIO]].from(Sleep.liftErr[JErr](_))

          make[JsonClient[JIO]].from(blaze)

          make[BearerToken].from(bearer)
          make[OAuth2JsonClient[JIO]]
            .named("drone")
            .from[BearerJsonClientModule[JIO]]

          make[Drone[JIO]].from {
            o: OAuth2JsonClient[JIO] @distage.Id("drone") =>
              new DroneModule[JIO](o)
          }
          make[Refresh[JIO]].from[RefreshModule[JIO]]

          make[OAuth2JsonClient[JIO]]
            .from[OAuth2JsonClientModule[JIO]]
          make[Machines[JIO]].from[GoogleMachinesModule[JIO]]
          make[DynAgents[JIO]].from[DynAgentsModule[JIO]]
        }

      plan <- Task(Injector(new GraphDumpBootstrapModule).plan(module))

      algs = Injector().produce(plan)
      agents = algs.get[DynAgents[JIO]]
      sleep = algs.get[Sleep[JIO]]

      start <- agents.initial.swallowErr

      _ <- refState[JErr, WorldView](start) >>= {
        step[JIO](agents, sleep)(_).forever[Unit].swallowErr
      }
    } yield ()
  }

  private def step[F[_]](
    A: DynAgents[F],
    S: Sleep[F]
  )(
    implicit
    F: MonadState[F, WorldView]
  ): F[Unit] =
    for {
      old     <- F.get
      updated <- A.update(old)
      changed <- A.act(updated)
      _       <- F.put(changed)
      _       <- S.sleep(10.seconds)
    } yield ()

  @deriving(ConfigReader)
  final case class OAuth2Config(
    token: RefreshToken,
    server: ServerConfig
  )
  @deriving(ConfigReader)
  final case class AppConfig(
    drone: BearerToken,
    machines: OAuth2Config
  )
}
