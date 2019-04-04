# Alambrando la aplicación

Para finalizar, aplicaremos lo que hemos aprendido para alambrar la aplicación
de ejemplo, e implementaremos un cliente y servidor HTTP usando la librería de
PF pura [http4s](https://http4s.org/).

El código fuente de la aplicación `drone-dynamic-agents` está disponible junto
con el código fuente del libro en `https://github.com/fommil/fpmortals/` bajo el
folder `examples`. No es necesario estar en una computadora para leer este
capítulo, pero muchos lectores preferirán explorar el código fuente además de
leer este texto.

Algunas partes de la aplicación se han dejado sin implementar, como ejercicios
para el lector. Vea el `README` para más instrucciones.

## Visión general

Nuestra aplicación principal únicamente requiere de una implementación del
álgebra `DynAgents`.

```scala
  trait DynAgents[F[_]] {
    def initial: F[WorldView]
    def update(old: WorldView): F[WorldView]
    def act(world: WorldView): F[WorldView]
  }
```

Ya tenemos una implementación, `DynAgentsModule`, que requiere implementaciones
de las álgebras `Drone` y `Machines`, que requieren álgebras `JsonClient`,
`LocalClock` y OAuth2, etc., etc.

Es valioso tener una visión completa de todas las álgebras, módulos e
intérpretes de una aplicación. Esta es la estructura del código fuente:

```text
  ├── dda
  │   ├── algebra.scala
  │   ├── DynAgents.scala
  │   ├── main.scala
  │   └── interpreters
  │       ├── DroneModule.scala
  │       └── GoogleMachinesModule.scala
  ├── http
  │   ├── JsonClient.scala
  │   ├── OAuth2JsonClient.scala
  │   ├── encoding
  │   │   ├── UrlEncoded.scala
  │   │   ├── UrlEncodedWriter.scala
  │   │   ├── UrlQuery.scala
  │   │   └── UrlQueryWriter.scala
  │   ├── oauth2
  │   │   ├── Access.scala
  │   │   ├── Auth.scala
  │   │   ├── Refresh.scala
  │   │   └── interpreters
  │   │       └── BlazeUserInteraction.scala
  │   └── interpreters
  │       └── BlazeJsonClient.scala
  ├── os
  │   └── Browser.scala
  └── time
      ├── Epoch.scala
      ├── LocalClock.scala
      └── Sleep.scala
```

Las firmas de todas las álgebras pueden resumirse como

```scala
  trait Sleep[F[_]] {
    def sleep(time: FiniteDuration): F[Unit]
  }

  trait LocalClock[F[_]] {
    def now: F[Epoch]
  }

  trait JsonClient[F[_]] {
    def get[A: JsDecoder](
      uri: String Refined Url,
      headers: IList[(String, String)]
    ): F[A]

    def post[P: UrlEncodedWriter, A: JsDecoder](
      uri: String Refined Url,
      payload: P,
      headers: IList[(String, String)]
    ): F[A]
  }

  trait Auth[F[_]] {
    def authenticate: F[CodeToken]
  }
  trait Access[F[_]] {
    def access(code: CodeToken): F[(RefreshToken, BearerToken)]
  }
  trait Refresh[F[_]] {
    def bearer(refresh: RefreshToken): F[BearerToken]
  }
  trait OAuth2JsonClient[F[_]] {
    // same methods as JsonClient, but doing OAuth2 transparently
  }

  trait UserInteraction[F[_]] {
    def start: F[String Refined Url]
    def open(uri: String Refined Url): F[Unit]
    def stop: F[CodeToken]
  }

  trait Drone[F[_]] {
    def getBacklog: F[Int]
    def getAgents: F[Int]
  }

  trait Machines[F[_]] {
    def getTime: F[Epoch]
    def getManaged: F[NonEmptyList[MachineNode]]
    def getAlive: F[MachineNode ==>> Epoch]
    def start(node: MachineNode): F[Unit]
    def stop(node: MachineNode): F[Unit]
  }
```

Note que algunas firmas de capítulos previos han sido refactorizadas para usar
los tipos de datos de Scalaz, ahora que sabemos porqué son superiores a las
alternativas en la librería estándar.

Los tipos de datos son:

```scala
  @xderiving(Order, Arbitrary)
  final case class Epoch(millis: Long) extends AnyVal

  @deriving(Order, Show)
  final case class MachineNode(id: String)

  @deriving(Equal, Show)
  final case class CodeToken(token: String, redirect_uri: String Refined Url)

  @xderiving(Equal, Show, ConfigReader)
  final case class RefreshToken(token: String) extends AnyVal

  @deriving(Equal, Show, ConfigReader)
  final case class BearerToken(token: String, expires: Epoch)

  @deriving(ConfigReader)
  final case class OAuth2Config(token: RefreshToken, server: ServerConfig)

  @deriving(ConfigReader)
  final case class AppConfig(drone: BearerToken, machines: OAuth2Config)

  @xderiving(UrlEncodedWriter)
  final case class UrlQuery(params: IList[(String, String)]) extends AnyVal
```

y las typeclasses son

```scala
  @typeclass trait UrlEncodedWriter[A] {
    def toUrlEncoded(a: A): String Refined UrlEncoded
  }
  @typeclass trait UrlQueryWriter[A] {
    def toUrlQuery(a: A): UrlQuery
  }
```

Derivamos typeclasses útiles usando `scalaz-deriving` y Magnolia. La typeclass
`ConfigReader` es de la librería `pureconfig` y se usa para leer la
configuración en tiempo de ejecución a partir de archivos de propiedades HOCON.

Y sin entrar en detalles sobre cómo implementar las álgebras, necesitamos
conocer el grafo de dependencias de nuestro `DynAgentsModule`.

```scala
  final class DynAgentsModule[F[_]: Applicative](
    D: Drone[F],
    M: Machines[F]
  ) extends DynAgents[F] { ... }

  final class DroneModule[F[_]](
    H: OAuth2JsonClient[F]
  ) extends Drone[F] { ... }

  final class GoogleMachinesModule[F[_]](
    H: OAuth2JsonClient[F]
  ) extends Machines[F] { ... }
```

Existen dos módulos que implementan `OAuth2JsonClient`, uno que usará el álgebra
OAuth2 `Refresh` (para Google) y otra que reutilice un `BearerToken` que no
expira (para Drone).

```scala
  final class OAuth2JsonClientModule[F[_]](
    token: RefreshToken
  )(
    H: JsonClient[F],
    T: LocalClock[F],
    A: Refresh[F]
  )(
    implicit F: MonadState[F, BearerToken]
  ) extends OAuth2JsonClient[F] { ... }

  final class BearerJsonClientModule[F[_]: Monad](
    bearer: BearerToken
  )(
    H: JsonClient[F]
  ) extends OAuth2JsonClient[F] { ... }
```

Hasta el momento hemos visto requerimientos para `F` que tienen un
`Applicative[F]`, `Monad[F]` y `MonadState[F, BearerToken]`. Todos estos
requerimientos pueden satisfacerse usando `StateT[Task, BearerToken, ?]` como
nuestro contexto de la aplicación.

Sin embargo, algunas de nuestras álgebras tienen solo un intérprete, usando
`Task`

```scala
  final class LocalClockTask extends LocalClock[Task] { ... }
  final class SleepTask extends Sleep[Task] { ... }
```

Pero recuerde que nuestras álgebras pueden proporcionar un `liftM` en su objeto
compañero, vea el Capítulo 7.4 sobre la Librería de Transformadores de Mónadas,
permitiéndonos elevar un `LocalClock[Task]` en nuestro contexto deseado
`StateT[Task, BearerToken, ?]`, y todo es consistente.

Tristemente, ese no es el final de la historia. Las cosas se vuelven más
complicadas cuando vamos a la siguiente capa. Nuestro `JsonClient` tiene un
intérprete que usa un contexto distinto

```scala
  final class BlazeJsonClient[F[_]](H: Client[Task])(
    implicit
    F: MonadError[F, JsonClient.Error],
    I: MonadIO[F, Throwable]
  ) extends JsonClient[F] { ... }
  object BlazeJsonClient {
    def apply[F[_]](
      implicit
      F: MonadError[F, JsonClient.Error],
      I: MonadIO[F, Throwable]
    ): Task[JsonClient[F]] = ...
  }
```

Note que el constructor `BlazeJsonClient` devuelve un `Task[JsonClient[F]]`, no
un `JsonClient[F]`. Esto es porque el acto de creación del cliente es un efecto:
se crean *pools* de conexiones mutables y se manejan internamente por https.

A> `OAuth2JsonClientModule` requiere de un `MonadState` y `BlazeJsonClient`
A> requiere de `MonadError` y `MonadIO`. Nuestro contexto de la aplicación ahora
A> será muy probablemente la combinación de ambos.
A>
A> ```scala
A>   StateT[EitherT[Task, JsonClient.Error, ?], BearerToken, ?]
A> ```
A>
A> Una pila de mónadas. Las pilas de mónadas proporcionan automáticamente
A> instancias de `MonadState` y `MonadError` cuando están anidadas, de modo que
A> no necesitamos pensar en esto. Si hubiéramos fijado la implementación en el
A> intérprete, y devuelto un `EitherT[Task, Error, ?]` de `BlazeJsonClient`,
A> sería mucho más complicado de instanciar.

No debemos olvidar que tenemos que proporcionar un `RefreshToken` para
`GoogleMachinesModule`. Podríamos solicitar al usuario hacer todo el trabajo
mecánico, pero somos buenas personas y proveemos una aplicación que usa las
álgebras `Auth` y `Access`. LAs implementaciones `AuthModule` y `AccessModule`
traen consigo dependencias adicionales, pero felizmente no cambian al contexto
de la aplicación `F[_]`.

```scala
  final class AuthModule[F[_]: Monad](
    config: ServerConfig
  )(
    I: UserInteraction[F]
  ) extends Auth[F] { ... }

  final class AccessModule[F[_]: Monad](
    config: ServerConfig
  )(
    H: JsonClient[F],
    T: LocalClock[F]
  ) extends Access[F] { ... }

  final class BlazeUserInteraction private (
    pserver: Promise[Void, Server[Task]],
    ptoken: Promise[Void, String]
  ) extends UserInteraction[Task] { ... }
  object BlazeUserInteraction {
    def apply(): Task[BlazeUserInteraction] = ...
  }
```

El intérprete para `UserInteraction` es la parte más compleja de nuestro código:
inicia un servidor HTTP, envía al usuario a visitar una página web en su
navegador, captura un callback en el servidor, y entonces devuelve el resultado
mientras que apaga el servidor web de manera segura.

Más bien que usar un `StateT` para administrar este estado, usaremos la
primitiva `Promise` (de `ioeffect`). Siempre usaremos `Promise` (o `IORef`) en
lugar de un `StateT` cuando estemos escribiendo un intérprete `IO` dado que nos
permite contener la abstracción. Si fuéramos a usar un `StateT`, no sólo tendría
un impacto en el rendimiento de la aplicación completa, sino que también habría
una fuga de manejo de stado interno en la aplicación principal, que sería
responsable de proporcionar el valor inicial. Tampoco podríamos usar `StateT` en
este escenario debido a que requerimos semántica de "espera" que sólo es
proporcionada por `Promise`.

## `Main`

La parte más fea de la PF es asegurarse de que las mónadas estén alineadas y eso
tiende a pasar en el punto de entrada `Main`.

Nuestro ciclo principal es

```scala
  state = initial()
  while True:
    state = update(state)
    state = act(state)
```

y las buenas noticias es que el código real se verá como

```scala
  for {
    old     <- F.get
    updated <- A.update(old)
    changed <- A.act(updated)
    _       <- F.put(changed)
    _       <- S.sleep(10.seconds)
  } yield ()
```

donde `F` mantiene el estado del mundo en una `MonadState[F, WorldView]`.
Podemos poner esto en un método que se llame `.step` y repetirlo por siempre al
invocar `.step[F].forever[Unit]`.

Hay dos enfoques que podemos tomar, y exploraremos ambos. El primero, y el más
simple, es construir una pila de mónadas con la que todas las mónadas sean
compatibles. A todo se le agregaría un `.liftM` para elevarlo a la pila más
general.

El código que deseamos escribir para el modo de autenticación única es

```scala
  def auth(name: String): Task[Unit] = {
    for {
      config    <- readConfig[ServerConfig](name + ".server")
      ui        <- BlazeUserInteraction()
      auth      = new AuthModule(config)(ui)
      codetoken <- auth.authenticate
      client    <- BlazeJsonClient
      clock     = new LocalClockTask
      access    = new AccessModule(config)(client, clock)
      token     <- access.access(codetoken)
      _         <- putStrLn(s"got token: $token")
    } yield ()
  }.run
```

donde `.readConfig` y `.putStrLn` son invocaciones a librerías. Podemos pensar
de ellas como intérpretes `Task` de álgebras que leen la configuración de tiempo
de ejecución de la aplicación e imprimen una cadena a la pantalla.

Pero este código no compila, por dos razones. Primero, necesitamos considerar lo
que será nuestra pila de mónadas. El constructor `BlazeJsonClient` devuelve un
`Task` pero los métodos `JsonClient` requieren un `MonadError[...,
JsonClient.Error]`. Este puede ser provisto por `EitherT`. Por lo tanto podemos
construir la pila de mónadas co,unes para la entera comprensión `for``como

```scala
  type H[a] = EitherT[Task, JsonClient.Error, a]
```

Tristemente esto significa que debemos llamar `.liftM` sobre todo lo que
devuelva un `Task`, lo que agrega mucho código repetitivo y verboso. Además, el
método `.liftM` no toma un tipo de la forma `H[_]`, sino un tipo de la forma
`H[_[_], _]`, de modo que tenemos que crear un alias de tipo para ayudar al
compilador:

```scala
  type HT[f[_], a] = EitherT[f, JsonClient.Error, a]
  type H[a]        = HT[Task, a]
```

ahora podemos llamar `.liftM[HT]` cuando recibimos un `Task`

```scala
  for {
    config    <- readConfig[ServerConfig](name + ".server").liftM[HT]
    ui        <- BlazeUserInteraction().liftM[HT]
    auth      = new AuthModule(config)(ui)
    codetoken <- auth.authenticate.liftM[HT]
    client    <- BlazeJsonClient[H].liftM[HT]
    clock     = new LocalClockTask
    access    = new AccessModule(config)(client, clock)
    token     <- access.access(codetoken)
    _         <- putStrLn(s"got token: $token").liftM[HT]
  } yield ()
```

Pero esto todavía no compila, debido a que `clock` es un `LocalClock[Task]` y
`AccessModule` requiere un `LocalClock[H]`. Entonces agregamos el código verboso
`.liftM` necesario al objeto compañero de `LocalClock` y entonces podemos elevar
el álgebra completa

```scala
  clock     = LocalClock.liftM[Task, HT](new LocalClockTask)
```

¡y ahora todo compila!

El segundo enfoque para alambrar la aplicación completa es más compleja, pero es
necesaria cuando existen conflictos en la pila de la mónada, tales como
necesitamos en nuestro ciclo principal. Si realizamos un análisis encontramos
que son necesarias las siguientes:

- `MonadError[F, JsonClient.Error]` para usos del `JsonClient`
- `MonadState[F, BearerToken]` para usos del `OAuth2JsonClient`
- `MonadState[F, WorldView]` para nuestro ciclo principal

Tristemente, los dos requerimientos de `MonadState` están en conflicto.
Podríamos construir un tipo de datos que capture todo el estado del programa,
pero entonces tendríamos una abstracción defectuosa. En lugar de esto, anidamos
nuestras comprensiones `for` y proporcionamos estado donde sea necesario.

Ahora tenemos que pensar sobre tres capas, que llamaremos `F`, `G`, `H`

```scala
  type HT[f[_], a] = EitherT[f, JsonClient.Error, a]
  type GT[f[_], a] = StateT[f, BearerToken, a]
  type FT[f[_], a] = StateT[f, WorldView, a]

  type H[a]        = HT[Task, a]
  type G[a]        = GT[H, a]
  type F[a]        = FT[G, a]
```

Ahora tenemos algunas malas noticias sobre `.liftM`... funciona solamente una
capa a la vez. Si tenemos un `Task[A]` y deseamos un `F[A]`, debemos ir a través
de cada paso y teclear `ta.liftM[HT].liftM[GT].liftM[FT]`. De manera similar,
cuando elevamos álgebras tenemos que invocar `liftM` múltiples veces. Para tener
un `Sleep[F]`, tenemos que teclear

```scala
  val S: Sleep[F] = {
    import Sleep.liftM
    liftM(liftM(liftM(new SleepTask)))
  }
```

y para obtener un `LocalClock[G]` tenemos que hacer dos elevaciones

```scala
  val T: LocalClock[G] = {
    import LocalClock.liftM
    liftM(liftM(new LocalClockTask))
  }
```

La aplicación principal se vuelve"

```scala
  def agents(bearer: BearerToken): Task[Unit] = {
    ...
    for {
      config <- readConfig[AppConfig]
      blaze  <- BlazeJsonClient[G]
      _ <- {
        val bearerClient = new BearerJsonClientModule(bearer)(blaze)
        val drone        = new DroneModule(bearerClient)
        val refresh      = new RefreshModule(config.machines.server)(blaze, T)
        val oauthClient =
          new OAuth2JsonClientModule(config.machines.token)(blaze, T, refresh)
        val machines = new GoogleMachinesModule(oauthClient)
        val agents   = new DynAgentsModule(drone, machines)
        for {
          start <- agents.initial
          _ <- {
            val fagents = DynAgents.liftM[G, FT](agents)
            step(fagents, S).forever[Unit]
          }.run(start)
        } yield ()
      }.eval(bearer).run
    } yield ()
  }
```

donde el ciclo exterior está usando `Task`, el ciclo de enmedio está usando `G`,
y el ciclo interno está usando `F`.

Las llamadas a `.run(start)` y `.eval(bearer)` son donde proporcionamos el
estado para las partes `StateT` de nuestra aplicación. El `.run` es para revelar
el error `EitherT`.

Podemos invocar estos dos puntos de entrada para nuestra `SafeApp`

```scala
  object Main extends SafeApp {
    def run(args: List[String]): IO[Void, ExitStatus] = {
      if (args.contains("--machines")) auth("machines")
      else agents(BearerToken("<invalid>", Epoch(0)))
    }.attempt[Void].map {
      case \/-(_)   => ExitStatus.ExitNow(0)
      case -\/(err) => ExitStatus.ExitNow(1)
    }
  }
```

¡y entonces ejecutarla!

```text
  > runMain fommil.dda.Main --machines
  [info] Running (fork) fommil.dda.Main --machines
  ...
  [info] Service bound to address /127.0.0.1:46687
  ...
  [info] Created new window in existing browser session.
  ...
  [info] Headers(Host: localhost:46687, Connection: keep-alive, User-Agent: Mozilla/5.0 ...)
  ...
  [info] POST https://www.googleapis.com/oauth2/v4/token
  ...
  [info] got token: "<elided>"
```

¡Yay!

## Blaze

Implementaremos el cliente y el servidor HTTP con la librería de terceros
`http4s`. Los intérpretes para sus álgebras de cliente y servidor se llaman
*Blaze*.

Necesitamos las siguientes dependencias

```scala
  val http4sVersion = "0.18.16"
  libraryDependencies ++= Seq(
    "org.http4s"            %% "http4s-dsl"          % http4sVersion,
    "org.http4s"            %% "http4s-blaze-server" % http4sVersion,
    "org.http4s"            %% "http4s-blaze-client" % http4sVersion
  )
```

### `BlazeJsonClient`

Necesitaremos algunos imports

```scala
  import org.http4s
  import org.http4s.{ EntityEncoder, MediaType }
  import org.http4s.headers.`Content-Type`
  import org.http4s.client.Client
  import org.http4s.client.blaze.{ BlazeClientConfig, Http1Client }
```

El módulo `Client` puede resumirse como

```scala
  final class Client[F[_]](
    val shutdown: F[Unit]
  )(implicit F: MonadError[F, Throwable]) {
    def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A] = ...
    ...
  }
```

donde `Request` y `Response`son los tipos de datos:

```scala
  final case class Request[F[_]](
    method: Method
    uri: Uri,
    headers: Headers,
    body: EntityBody[F]
  ) {
    def withBody[A](a: A)
                   (implicit F: Monad[F], A: EntityEncoder[F, A]): F[Request[F]] = ...
    ...
  }

  final case class Response[F[_]](
    status: Status,
    headers: Headers,
    body: EntityBody[F]
  )
```

construidos a partir de

```scala
  final case class Headers(headers: List[Header])
  final case class Header(name: String, value: String)

  final case class Uri( ... )
  object Uri {
    // not total, only use if `s` is guaranteed to be a URL
    def unsafeFromString(s: String): Uri = ...
    ...
  }

  final case class Status(code: Int) {
    def isSuccess: Boolean = ...
    ...
  }

  type EntityBody[F[_]] = fs2.Stream[F, Byte]
```

El tipo `EntityBody` es un alias para `Stream` de la librería
`fs2`(https://github.com/functional-streams-for-scala/fs2). El tipo de datos
`Stream` puede pensarse como un torrente de datos con efectos, perezoso, y
basado en tracción (*pull-based*). Está implementado como una mónada `Free` con
capacidades para atrapar excepciones e interrupciones. `Stream` toma dos
parámetros de tipo: un tipo de efecto y un tipo de contenido, y tiene una
representación interna eficiente para hacer un procesamiento por lotes. Por
ejemplo, aunque estemos usando `Stream[F, Byte]`, en realidad está envolviendo
un `Array[Byte]` desnudo que llega sobre la red.

Tenemos que convertir nuestras representaciones para el encabezado y URL en las
versiones requeridas por https4s:

```scala
  def convert(headers: IList[(String, String)]): http4s.Headers =
    http4s.Headers(
      headers.foldRight(List[http4s.Header]()) {
        case ((key, value), acc) => http4s.Header(key, value) :: acc
      }
    )

  def convert(uri: String Refined Url): http4s.Uri =
    http4s.Uri.unsafeFromString(uri.value) // we already validated our String
```

Tanto nuestro método `.get` como `.post` requieren una conversión del tipo
`Response` de http4s a una `A`. Podemos refactorizar esto en una única función,
`.handler`

```scala
  import JsonClient.Error

  final class BlazeJsonClient[F[_]] private (H: Client[Task])(
    implicit
    F: MonadError[F, Error],
    I: MonadIO[F, Throwable]
  ) extends JsonClient[F] {
    ...
    def handler[A: JsDecoder](resp: http4s.Response[Task]): Task[Error \/ A] = {
      if (!resp.status.isSuccess)
        Task.now(JsonClient.ServerError(resp.status.code).left)
      else
        for {
          text <- resp.body.through(fs2.text.utf8Decode).compile.foldMonoid
          res = JsParser(text)
            .flatMap(_.as[A])
            .leftMap(JsonClient.DecodingError(_))
        } yield res
    }
  }
```

El `.through(fs2.text.utf8decode)` es para convertir un `Stream[Task, Byte]` en
un `Stream[Task, String]`, con `.compile.foldMonoid` interpretándolo con nuestra
`Task` y combinando todas las partes usando el `Monoid[String]`, dándonos un
`Task[String]`.

Entonces analizamos gramaticalmente la cadena como JSON y usamos el
`JsDecoder[A]` para crear la salida requerida.

Esta es nuestra implementación de `.get`

```scala
  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[A] =
    I.liftIO(
        H.fetch(
          http4s.Request[Task](
            uri = convert(uri),
            headers = convert(headers)
          )
        )(handler[A])
      )
      .emap(identity)
```

`.get` es totalmente código para conectar partes: convertimos nuestros tipos de
entrada en `http4s.Request`, entonces invocamos `.fetch` sobre el `Client` con
nuestro `handler`. Esto nos da un `Task[Error \/ A]`, pero tenemos que devolver
un `F[A]`. Por lo tanto usamos el método `MonadIO.liftIO` para crear un
`F[Error \/ A]` y entonces usamos `.emap` para poner el error dentro de la `F`.

Tristemente, la compilación de este código fallaría si lo intentamos. El error
se vería como

```text
  [error] BlazeJsonClient.scala:95:64: could not find implicit value for parameter
  [error]  F: cats.effect.Sync[scalaz.ioeffect.Task]
```

Básicamente, es porque faltan dependencias del ecosistema de cats.

La razón de esta falla es que la librería http4s está usando una librería de
programación FP diferente, no Scalaz. Felizmente, `scalaz-ioeffect` proporciona
una capa de compatibilidad y el proyecto
[shims](https://github.com/djspiewak/shims) proporciona conversiones implícitas
transparentes (hasta que no lo son). Podemos lograr que nuestro código compile
con las siguientes dependencias:

```scala
  libraryDependencies ++= Seq(
    "com.codecommit" %% "shims"                % "1.4.0",
    "org.scalaz"     %% "scalaz-ioeffect-cats" % "2.10.1"
  )
```

y estos imports

```scala
  import shims._
  import scalaz.ioeffect.catz._
```

La implementación de `.post` es similar pero también debemos proporcionar una
instancia de

```scala
  EntityEncoder[Task, String Refined UrlEncoded]
```

Felizmente, la typeclass `EntityEncoder` proporciona conveniencias que nos
permiten derivar una a partir de un codificador existente `String`

```scala
  implicit val encoder: EntityEncoder[Task, String Refined UrlEncoded] =
    EntityEncoder[Task, String]
      .contramap[String Refined UrlEncoded](_.value)
      .withContentType(
        `Content-Type`(MediaType.`application/x-www-form-urlencoded`)
      )
```

La única diferencia entre `.get` y `.post` es la forma en que construimos
`http4s.Request`

```scala
  http4s.Request[Task](
    method = http4s.Method.POST,
    uri = convert(uri),
    headers = convert(headers)
  )
  .withBody(payload.toUrlEncoded)
```

y la pieza final es el constructor, que es un caso de invocar `HttpClient` con
un objeto de configuración

```scala
  object BlazeJsonClient {
    def apply[F[_]](
      implicit
      F: MonadError[F, JsonClient.Error],
      I: MonadIO[F, Throwable]
    ): Task[JsonClient[F]] =
      Http1Client(BlazeClientConfig.defaultConfig).map(new BlazeJsonClient(_))
  }
```

### `BlazeUserInteraction`

Necesitamos iniciar un servidor HTTP, que es mucho más sencillo de lo que suena.
Primero, los imports

```scala
  import org.http4s._
  import org.http4s.dsl._
  import org.http4s.server.Server
  import org.http4s.server.blaze._
```

Necesitamos crear una `dsl` para nuestro tipo de efecto, que entonces importaremos

```scala
  private val dsl = new Http4sDsl[Task] {}
  import dsl._
```

Ahora podemos usar la [dsl de http4s](https://http4s.org/v0.18/dsl/) para crear
*endpoints* HTTP. Más bien que describir todo lo que puede hacerse, simplemente
implementaremos el *endpoint* que sea similar a cualquier otro DLS de HTTP

```scala
  private object Code extends QueryParamDecoderMatcher[String]("code")
  private val service: HttpService[Task] = HttpService[Task] {
    case GET -> Root :? Code(code) => ...
  }
```

El tipo de retorno de cada empate de patrones es un `Task[Response[Task]`. En
nuestra implementación deseamos tomar el `code` y ponerlo en una promesa
`ptoken`:

```scala
  final class BlazeUserInteraction private (
    pserver: Promise[Throwable, Server[Task]],
    ptoken: Promise[Throwable, String]
  ) extends UserInteraction[Task] {
    ...
    private val service: HttpService[Task] = HttpService[Task] {
      case GET -> Root :? Code(code) =>
        ptoken.complete(code) >> Ok(
          "That seems to have worked, go back to the console."
        )
    }
    ...
  }
```

pero la definición de nuestras rutas de servicios no es suficiente, requerimos
arrancar un servidor, lo que hacemos con `BlazeBuilder`

```scala
  private val launch: Task[Server[Task]] =
    BlazeBuilder[Task].bindHttp(0, "localhost").mountService(service, "/").start
```

Haciendo un enlace con el puerto `0` hace que el sistema operativo asigne un
puerto efímero. Podemos descubrir en qué puerto está ejecutándose en realidad al
consultar el campo `server.address`.

Nuestra implementación de los métodos `.start` y `.stop` ahora es directa

```scala
  def start: Task[String Refined Url] =
    for {
      server  <- launch
      updated <- pserver.complete(server)
      _ <- if (updated) Task.unit
           else server.shutdown *> fail("server was already running")
    } yield mkUrl(server)

  def stop: Task[CodeToken] =
    for {
      server <- pserver.get
      token  <- ptoken.get
      _      <- IO.sleep(1.second) *> server.shutdown
    } yield CodeToken(token, mkUrl(server))

  private def mkUrl(s: Server[Task]): String Refined Url = {
    val port = s.address.getPort
    Refined.unsafeApply(s"http://localhost:${port}/")
  }
  private def fail[A](s: String): String =
    Task.fail(new IOException(s) with NoStackTrace)
```

El tiempo de `1.second` ocupado en dormir es necesario para evitar apagar el
servidor antes de que la respuesta se envío de regreso al navegador. ¡La IO no
pierde el tiempo cuando se trata de rendimiento concurrente!

Finalmente, para crear un `BlazeUserInteraction`, únicamente requerimos las dos
promesas sin inicializar

```scala
  object BlazeUserInteraction {
    def apply(): Task[BlazeUserInteraction] = {
      for {
        p1 <- Promise.make[Void, Server[Task]].widenError[Throwable]
        p2 <- Promise.make[Void, String].widenError[Throwable]
      } yield new BlazeUserInteraction(p1, p2)
    }
  }
```

Podríamos haber usado `IO[Void, ?]` en vez de esto, pero dado que el resto de
nuestra aplicación está usando `Task` (es decir, `IO[Throwable, ?]`), invocamos
`.widenError`para evitar introducir cualquier cantidad de código verboso que nos
podría distraer.

## Gracias

¡Y eso es todo! Felicidades por llegar al final.

Si usted aprendió algo de este libro, por favor diga a sus amigos. Este libro no
tiene un departamento de marketing, de modo que sus palabras son necesarias para
dar a conocerlo.

Involúcrese en Scalaz al unirse al [cuarto de charla de
gitter](https://gitter.im/scalaz/scalaz). Ahí puede solicitar consejo, ayudar a
los nuevos (ahora usted es un experto), y contribuir al siguiente *release*.

<!-- LocalWords: Scalaz
-->
