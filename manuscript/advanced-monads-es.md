# Mónadas avanzadas

Usted tiene que conocer cosas como las mónadas avanzadas para ser un
programador funcional avanzado.

Sin embargo, somos desarrolladores  buscando una vida simple, y nuestra
idea de "avanzado" es modesta. Para ponernos en contexto:
`scala.concurrent.Future` es más complicada y con más matices que
cualquier `Monad` en este capítulo.

En este capítulo estudiaremos algunas de las implementaciones más
importantes de `Monad`.

## `Future` siempre está en movimiento

El problema más grande con `Future` es que programa trabajo rápidamente
durante su construcción. Como descubrimos en la introducción, `Future`
mezcla la definición del programa con su interpretación (es decir, con
su ejecución).

`Future` también es malo desde una perspectiva de rendimiento: cada vez
que `.flatMap` es llamado, una cerradura se manda al `Ejecutor`, resultando
en una programación de hilos y en cambios de contexto innecesarios. No es
inusual ver 50% del poder de nuestro CPU lidiando con planeación de hilos.
Tanto es así que la paralelización de trabajo con `Futuro` puede volverlo
más lento.

En combinación, la evaluación estricta y el despacho de ejecutores significa
que es imposible de saber cuándo inició un trabajo, cuando terminó, o las
sub tareas que se despacharon para calcular el resultado final. No debería
sobreprendernos que las "soluciones" para el monitoreo de rendimiento para
frameworks basados en `Future` sean buenas opciones para las ventas.

Además, `Future.flatMap` requiere de un `ExecutionContext` en el ámbito
implícito: los usuarios están forzados a pensar sobre la lógica de negocios
y la semántica de ejecución a la vez.

A> Si `Future` fuera un personaje de Star Wars, sería Anakin Skywalker: el
A> malo de la película, apresuránduose y rompiendo cosas sin pensarlo.

## Efectos y efectos laterales

Si no podemos llamar métodos que realicen efectos laterales en nuestra lógica
de negocios, o en `Future` (or `Id`, or `Either`, o `Const`, etc), entonces,
¿*cuándo podemos escribirlos*? La respuesta es: en una `Monad` que retrasa la
ejecución hasta que es interpretada por el punto de entrada de la aplicación.
En este punto, podemos referirnos a I/O y a la mutación como un *efecto* en
el mundo, capturado por el sistema de tipos, en oposición a tener efectos
laterales ocultos.

La implementación simple de tal `Monad` es `IO`, formalizando la versión que
escribimos en la introducción:

{lang="text"}
~~~~~~~~
  final class IO[A](val interpret: () => A)
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(() => a)
  
    implicit val monad: Monad[IO] = new Monad[IO] {
      def point[A](a: =>A): IO[A] = IO(a)
      def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = IO(f(fa.interpret()).interpret())
    }
  }
~~~~~~~~

El método `.interpret` se llama únicamente una vez, en el punto de entrada de una
aplicación.

{lang="text"}
~~~~~~~~
  def main(args: Array[String]): Unit = program.interpret()
~~~~~~~~

Sin embargo, hay dos grandes problemas con este simple `IO`:

1. Puede ocasionar un sobreflujo de la pila
2. No soporta cómputos paralelos

Ambos problemas serán abordados en este capítulo. Sin embargo, sin importar lo
complicado de una implementación interna de una `Monad`, los principios aquí
descritos seguirán siendo ciertos: estamos modularizando la definición de un
programa y su ejecución, tales que podemos capturar los efectos en la signatura
de los tipos, permitiendonos razonar sobre ellos, y reusar más código.

A> El compilador de Scala nos permitirá, felizmente, llamar métodos con efectos
A> laterales a partir de bloques de código inseguros. La herramienta
A> [Scalafix](https://scalacenter.github.io/scalafix/) puede prohibir métodos
A> con efectos laterales en tiempo de compilación, a menos que se invoque desde
A> dentro de una mónada que los postergue, como `IO`.

## Seguridad de la pila

En la JVM, toda invocación a un método, agrega una entrada a la pila de llamadas
del hilo (`Thread`), como agregar al frente de una `List`. Cuando el método
completa, el método en la cabeza (`head`) es descartado/eliminado. La longitud
máxima de la pila de llamadas es determinada por la bandera `-Xss` cuando se
llama a `java`.  Los métodos que realizan una recursión de cola, son detectados
por el compilador de Scala y no agregan una entrada. Si alcanzamos el límite,
al invocar demasiados métodos encadenados, obtenemos una excepción de
`StackOverflowError`.

Desgraciadamente, toda invocación anidada de `.flatMap` (sobre una instancia de
`IO`) agrega otro método de invocación a la pila. La forma más sencilla de ver
esto es repetir esta acción por siempre, y ver si logra sobrevivir más de unos
cuántos segundos. Podemos usar `.forever`, que viene de `Apply` (un "padre" de
`Monad`):

{lang="text"}
~~~~~~~~
  scala> val hello = IO { println("hello") }
  scala> Apply[IO].forever(hello).interpret()
  
  hello
  hello
  hello
  ...
  hello
  java.lang.StackOverflowError
      at java.io.FileOutputStream.write(FileOutputStream.java:326)
      at ...
      at monadio.IO$$anon$1.$anonfun$bind$1(monadio.scala:18)
      at monadio.IO$$anon$1.$anonfun$bind$1(monadio.scala:18)
      at ...
~~~~~~~~

Scalaz tiene una typeclass que las instancias de `Monad` pueden implementar si
son seguras: `BindRec` requiere de un espacio de pila constante para `bind`
recursivo:

{lang="text"}
~~~~~~~~
  @typeclass trait BindRec[F[_]] extends Bind[F] {
    def tailrecM[A, B](f: A => F[A \/ B])(a: A): F[B]
  
    override def forever[A, B](fa: F[A]): F[B] = ...
  }
~~~~~~~~

No necesitamos `BindRec` para todos los programas, pero es esencial para
una implementación de propósito general de `Monad`.

La manera de conseguir seguridad en la pila es la conversión de invocaciones de
métodos en referencias a una ADT, la mónada `Free`:

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A]
  object Free {
    private final case class Return[S[_], A](a: A)     extends Free[S, A]
    private final case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
    private final case class Gosub[S[_], A0, B](
      a: Free[S, A0],
      f: A0 => Free[S, B]
    ) extends Free[S, B] { type A = A0 }
    ...
  }
~~~~~~~~

A> `SUSPEND`, `RETURN` y `GOSUB` son nombres dados en honor a los comandos de `BASIC`
A> del mismo nombre: pausar, completar y retornar de una subrutina, respectivamente.

La ADT `Free` es una es una representación del tipo de datos natural de la interfaz
`Monad`:

1. `Return` representa `.point`
2. `Gosub` representa `.bind` / `.flatMap`

Cuando una ADT es un reflejo de los argumentos de las funciones relacionadas, recibe
el nombre de una *codificación Church*.

`Free` recibe este nombre debido a que puede ser generada de manera gratuita para
cualquier `S[_]`. Por ejemplo, podríamos hacer que `S` sea una de las álgebras `Drone` o `Machines` del Capítulo 3 y generar una representación de la estructura de
datos de nuestro programa. Regresaremos más tarde a este punto para explicar por qué
razón esto es de utilidad.

### `Trampoline`

`Free` es más general de lo necesario. Haciendo que el álgebra `S[_]` sea `() => ?`,
un cálculo diferido o un *thunk*, obtenemos `Trampoline`  que puede implementar una
`Monad` de manera que se conserva el uso seguro de la pila.

{lang="text"}
~~~~~~~~
  object Free {
    type Trampoline[A] = Free[() => ?, A]
    implicit val trampoline: Monad[Trampoline] with BindRec[Trampoline] =
      new Monad[Trampoline] with BindRec[Trampoline] {
        def point[A](a: =>A): Trampoline[A] = Return(a)
        def bind[A, B](fa: Trampoline[A])(f: A => Trampoline[B]): Trampoline[B] =
          Gosub(fa, f)
  
        def tailrecM[A, B](f: A => Trampoline[A \/ B])(a: A): Trampoline[B] =
          bind(f(a)) {
            case -\/(a) => tailrecM(f)(a)
            case \/-(b) => point(b)
          }
      }
    ...
  }
~~~~~~~~

La implementación de `BindRec`, `.tailrecM`, ejecuta `.bind` hasta que obtenemos una
`B`. Aunque no se trata, técnicamente, de una implementación `@tailrec`, usa un
espacio de pila constante debido a que cada llamada devuelve un objeto en el heap,
con recursión postergada.

A> Se llama `Trampoline` debido a que cada vez que llamamos `.bind` en la pila,
A> "rebotamos" hacia el espacio heap.

Se proporcionan funciones convenientes para la creación estricta de un `Trampoline` (`.done`) o por nombre (`.delay`). También podemos crear un `Trampoline` a partir de
un `Trampoline` por nombre (`.suspend`):

{lang="text"}
~~~~~~~~
  object Trampoline {
    def done[A](a: A): Trampoline[A]                  = Return(a)
    def delay[A](a: =>A): Trampoline[A]               = suspend(done(a))
    def suspend[A](a: =>Trampoline[A]): Trampoline[A] = unit >> a
  
    private val unit: Trampoline[Unit] = Suspend(() => done(()))
  }
~~~~~~~~

Cuando vemos un `Trampoline[A]` en el código, siempre es posible sustituirlo
mentalmente con una `A`, debido a que únicamente está añadiendo seguridad al uso
de la pila a un cómputo puro. Obtenemos la `A` al interpretar `Free`, provisto por
`.run`.

A> Es instructivo, aunque no es necesario, entender cómo se implementa `Free.run`:
A> `.resume` evalúa una única capa de `Free`, y `go` la ejecuta hasta completarla.
A>
A> En el siguiente bloque de código, `Trampoline[A]` es usado como sinónimo de
A> `Free[() => ?, A]` para hacer el código más fácil de leer.
A>
A> {lang="text"}
A> ~~~~~~~~
A>   sealed abstract class Trampoline[A] {
A>     def run: A = go(f => f())
A>   
A>     def go(f: () => Trampoline[A] => Trampoline[A]): A = {
A>       @tailrec def go2(t: Trampoline[A]): A = t.resume match {
A>         case -\/(s) => go2(f(s))
A>         case \/-(r) => r
A>       }
A>       go2(this)
A>     }
A>   
A>     @tailrec def resume: () => Trampoline[A] \/ A = this match {
A>       case Return(a) => \/-(a)
A>       case Suspend(t) => -\/(t.map(Return(_)))
A>       case Gosub(Return(a), f) => f(a).resume
A>       case Gosub(Suspend(t), f) => -\/(t.map(f))
A>       case Gosub(Gosub(a, g), f) => a >>= (z => g(z) >>= f).resume
A>     }
A>     ...
A>   }
A> ~~~~~~~~
A>
A> El caso más probable de ocasionar confusión es cuando hemos anidado `Gosub`:
A> aplique la función interna `g` y entonces pásela al la función externa `f`,
A> y simplemente se trata de composición de funciones.

### Ejemplo: `DList` con seguridad en el manejo de la pila

En el capítulo anterior hemos descrito el tipo de datos `DList` como

{lang="text"}
~~~~~~~~
  final case class DList[A](f: IList[A] => IList[A]) {
    def toIList: IList[A] = f(IList.empty)
    def ++(as: DList[A]): DList[A] = DList(xs => f(as.f(xs)))
    ...
  }
~~~~~~~~

Sin embargo, la implementación actual se ve más parecida a:

{lang="text"}
~~~~~~~~
  final case class DList[A](f: IList[A] => Trampoline[IList[A]]) {
    def toIList: IList[A] = f(IList.empty).run
    def ++(as: =>DList[A]): DList[A] = DList(xs => suspend(as.f(xs) >>= f))
    ...
  }
~~~~~~~~

En lugar de aplicar llamadas anidadas a `f`, usamos un `Trampoline` suspendido.
Interpretamos el trampolín con `.run` únicamente cuando es necesario, por ejemplo,
en `toIList`. Los cambios son mínimo, pero ahora tenemos una `DList` con uso
seguro de la pila que puede reordenar la concatenación de un número largo de
listas sin ocasionar un sobreflujo de la pila.

### `IO` con uso seguro de la pila

De manera similar, nuestra `IO` puede hacerse segura (respecto al uso de la pila),
gracias a `Trampoline`:

{lang="text"}
~~~~~~~~
  final class IO[A](val tramp: Trampoline[A]) {
    def unsafePerformIO(): A = tramp.run
  }
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(Trampoline.delay(a))
  
    implicit val Monad: Monad[IO] with BindRec[IO] =
      new Monad[IO] with BindRec[IO] {
        def point[A](a: =>A): IO[A] = IO(a)
        def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
          new IO(fa.tramp >>= (a => f(a).tramp))
        def tailrecM[A, B](f: A => IO[A \/ B])(a: A): IO[B] = ...
      }
  }
~~~~~~~~

A> Nos enteramos de que le gusta `Monad`, de modo que hemos fabricado una a partir
de `Monad`, de modo que pueda hacer un bind monádico cuando está haciendo un
bind monádico.

El intérprete, `.unsafePerformIO()`, ahora tiene un nombre intencionalmente
terrorífico para desalentar su uso, con la excepción del punto de entrada de una
aplicación.

Esta vez, no obtendremos un error por sobreflujo de la pila:

{lang="text"}
~~~~~~~~
  scala> val hello = IO { println("hello") }
  scala> Apply[IO].forever(hello).unsafePerformIO()
  
  hello
  hello
  hello
  ...
  hello
~~~~~~~~

El uso de un `Trampoline` típicamente introduce una regresión en el desempeño
comparado a la implementación normal de referencia. Es `Free` en el sentido de que
se genera de manera automática, no en el sentido literal.

A> Siempre realice mediciones en lugar de aceptar ciegamente afirmaciones sobre el
A> desempeño: bien puede ser que se presente el caso en el que el recolector de
A> basura realice su trabajo de manera más eficiente cuando la aplicación está usando
A> `Free` debido al tamaño reducido de objetos retenidos en la pila.

## Librería de transformadores de mónadas

Las transformadores de mónadas son estructuras de datos que envuelven un valor
subyacente y proporcionan un efecto monádico.

Por ejemplo, en el capítulo 2 usamos `OptionT` para que pudiéramos usar
`F[Option[A]]` en una comprehensión `for` como si se tratase de `F[A]`. Esto
le dio a nuestro programa el efecto de un valor *opcional*. De manera alternativa,
podemos conseguir el efcto de opcionalidad si tenemos una `MonadPlus`.

A este subconjunto de tipos de datos y extensiones a `Monad` con frecuencia se le
conoce como una *Librería de Transformadores de Mónadas* (*MTL*, por sus siglas en
inglés), como se resume enseguida. En esta sección, explicaremos cada uno de los
transformadores, por qué razón son útiles, y cómo funcionan.

| Efecto                                      | Equivalencia          | Transfordor | Typeclass     |
|-------------------------------------------- |---------------------- |------------ |-------------- |
| opcionalidad                                | `F[Maybe[A]]`         | `MaybeT`    | `MonadPlus`   |
| errores                                     | `F[E \/ A]`           | `EitherT`   | `MonadError`  |
| un valor en tiempo de ejecución             | `A => F[B]`           | `ReaderT`   | `MonadReader` |
| journal / multi tarea                       | `F[(W, A)]`           | `WriterT`   | `MonadTell`   |
| estado en cambio                            | `S => F[(S, A)]`      | `StateT`    | `MonadState`  |
| mantén la calma y continúa                  | `F[E \&/ A]`          | `TheseT`    |               |
| control de flujo                            | `(A => F[B]) => F[B]` | `ContT`     |               |

### `MonadTrans`

Cada transformador tiene la forma general `T[F[_], A]`, proporcionando al menos una instancia de
`Monad` y `Hoist` (y por lo tanto de `MonadTrans`):

{lang="text"}
~~~~~~~~
  @typeclass trait MonadTrans[T[_[_], _]] {
    def liftM[F[_]: Monad, A](a: F[A]): T[F, A]
  }
  
  @typeclass trait Hoist[F[_[_], _]] extends MonadTrans[F] {
    def hoist[M[_]: Monad, N[_]](f: M ~> N): F[M, ?] ~> F[N, ?]
  }
~~~~~~~~

A> `T[_[_], _]` es otro ejemplo de un *higher kinded type*. Lo que dice es lo siguiente:
A> `T` toma dos parámetros de tipo; el primero también toma un parámetro de tipo,
A> escrito como `_[_]`, y el segundo no toma ningún parámetro de tipo, escrito como `_`.

`.liftM` nos permite crear un transformador de mónadas si tenemos un `F[A]`. Por ejemplo,
podemos crear un `OptionT[IO, String]` al invocar ` .liftM[OptionT]` en una `IO[String]`.

`.hoist` es la misma idea, pero para transformaciones naturales.

Generalmente, hay tres maneras de crear un transformador de mónadas:

- A partir de la estructura equivalente, usando el constructor del transformador
- A partir de un único valor `A`, usando `.pure` usando la sintaxis de `Monad`
- A partir de `F[A]`, usando `.liftM` usando la sintaxis de `MonadTrans`

Debido a la forma en la que funciona la inferencia de tipos en Scala, esto con frecuencia
significa que un parámetro de tipo complejo debe escribirse de manera explícita. Como una
forma de lidiar con el problema, los transformadores proporcionan constructores convenientes
en su objeto compañero que los hacen más fáciles de usar.

### `MaybeT`

`OptionT`, `MaybeT` y `LazyOption` tienen implementaciones similares, proporcionando
opcionalidad a través de `Option`, `Maybe` y `LazyOption`, respectivamente. Nos
enfocaremos en `MaybeT` para evitar la repetición.

{lang="text"}
~~~~~~~~
  final case class MaybeT[F[_], A](run: F[Maybe[A]])
  object MaybeT {
    def just[F[_]: Applicative, A](v: =>A): MaybeT[F, A] =
      MaybeT(Maybe.just(v).pure[F])
    def empty[F[_]: Applicative, A]: MaybeT[F, A] =
      MaybeT(Maybe.empty.pure[F])
    ...
  }
~~~~~~~~

proporcionando una `MonadPlus`

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad] = new MonadPlus[MaybeT[F, ?]] {
    def point[A](a: =>A): MaybeT[F, A] = MaybeT.just(a)
    def bind[A, B](fa: MaybeT[F, A])(f: A => MaybeT[F, B]): MaybeT[F, B] =
      MaybeT(fa.run >>= (_.cata(f(_).run, Maybe.empty.pure[F])))
  
    def empty[A]: MaybeT[F, A] = MaybeT.empty
    def plus[A](a: MaybeT[F, A], b: =>MaybeT[F, A]): MaybeT[F, A] = a orElse b
  }
~~~~~~~~

Esta mónada se ve un poco complicada, pero simplemente está delegando todo a la
`Monad[F]` y entonces envolviendo todo dentro de un `MaybeT`. Simplemente es
código para cumplir con el trabajo.

Con esta mónada podemos escribir lógica que maneja la opcionalidad en el
contexto de `F[_]`, más bien que estar lidiando con `Option` o `Maybe`.

Por ejemplo, digamos que estamos interactuando con un sitio social para contar
el número de estrellas que tiene el usuario, y empezamos con una cadena (`String`)
que podría o no corresponder al usuario. Tenemos esta álgebra:

{lang="text"}
~~~~~~~~
  trait Twitter[F[_]] {
    def getUser(name: String): F[Maybe[User]]
    def getStars(user: User): F[Int]
  }
  def T[F[_]](implicit t: Twitter[F]): Twitter[F] = t
~~~~~~~~

Necesitamos invocar `getUser` seguido de `getStars`. Si usamos `Monad` como nuestro
contexto, nuestra función es difícil porque tendremos que lidiar con el caso `Empty`:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): F[Maybe[Int]] = for {
    maybeUser  <- T.getUser(name)
    maybeStars <- maybeUser.traverse(T.getStars)
  } yield maybeStars
~~~~~~~~

Sin embargo, si tenemos una `MonadPlus` como nuestro contexto, podemos poner
`Maybe` dentro de `F[_]` usando `.orEmpty`, y olvidarnos del asunto:

{lang="text"}
~~~~~~~~
  def stars[F[_]: MonadPlus: Twitter](name: String): F[Int] = for {
    user  <- T.getUser(name) >>= (_.orEmpty[F])
    stars <- T.getStars(user)
  } yield stars
~~~~~~~~

Sin embargo, agregar un requerimiento de `MonadPlus` puede ocasionar problemas más
adelante en el proceso, si el contexto no tiene una. La solución es, o cambiar el
contexto del programa a `MaybeT[F, ?]` (elevando el contexto de `Monad[F]` a una
`MonadPlus`), o para usar explícitamente `MaybeT` en el tipo de retorno, a costa de
un poco de código adicional:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): MaybeT[F, Int] = for {
    user  <- MaybeT(T.getUser(name))
    stars <- T.getStars(user).liftM[MaybeT]
  } yield stars
~~~~~~~~

La decisión de requerir una `Monad` más poderosa vs devolver un transformador es
algo que cada equipo puede decidir por sí mismo basándose en los intérpretes que
planee usar en sus programas.

### `EitherT`

Un valor opcional es el caso especial de un valor que puede ser un error, pero no
sabemos nada sobre el error. `EitherT` (y la variante perezosa `LazyEitherT`) nos
permite usar cualquier tipo que deseemos como el valor del error, porporcionando
información contextual sobre lo que pasó mal.

`EitherT` es un envoltorio sobre `F[A \/ B]`

{lang="text"}
~~~~~~~~
  final case class EitherT[F[_], A, B](run: F[A \/ B])
  object EitherT {
    def either[F[_]: Applicative, A, B](d: A \/ B): EitherT[F, A, B] = ...
    def leftT[F[_]: Functor, A, B](fa: F[A]): EitherT[F, A, B] = ...
    def rightT[F[_]: Functor, A, B](fb: F[B]): EitherT[F, A, B] = ...
    def pureLeft[F[_]: Applicative, A, B](a: A): EitherT[F, A, B] = ...
    def pure[F[_]: Applicative, A, B](b: B): EitherT[F, A, B] = ...
    ...
  }
~~~~~~~~

`Monad` es una `MonadError`

{lang="text"}
~~~~~~~~
  @typeclass trait MonadError[F[_], E] extends Monad[F] {
    def raiseError[A](e: E): F[A]
    def handleError[A](fa: F[A])(f: E => F[A]): F[A]
  }
~~~~~~~~

`.raiseError` y `.handleError` son descriptivos en sí mismos: el equivalente
de lanzar (`throw`) y atrapar (`catch`) una excepción, respectivamente.

`MonadError` tiene sintaxis adicional para lidiar con problemas comunes:

{lang="text"}
~~~~~~~~
  implicit final class MonadErrorOps[F[_], E, A](self: F[A])(implicit val F: MonadError[F, E]) {
    def attempt: F[E \/ A] = ...
    def recover(f: E => A): F[A] = ...
    def emap[B](f: A => E \/ B): F[B] = ...
  }
~~~~~~~~

`.attempt` trae los errores dentro del valor, lo cual es útil para exponer
los errores en los subsistemas como valores de primera clase.

`.recover` es para convertir un error en un valor en todos los casos, en
oposición a `.handleError` que toma una `F[A]` y por lo tanto permite una
recuperación parcial.

`.emap`, es para aplicar transformaciones que pueden fallar.

El `MonadError` para `EitherT` es:

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad, E] = new MonadError[EitherT[F, E, ?], E] {
    def monad[F[_]: Monad, E] = new MonadError[EitherT[F, E, ?], E] {
    def bind[A, B](fa: EitherT[F, E, A])
                  (f: A => EitherT[F, E, B]): EitherT[F, E, B] =
      EitherT(fa.run >>= (_.fold(_.left[B].pure[F], b => f(b).run)))
    def point[A](a: =>A): EitherT[F, E, A] = EitherT.pure(a)
  
    def raiseError[A](e: E): EitherT[F, E, A] = EitherT.pureLeft(e)
    def handleError[A](fa: EitherT[F, E, A])
                      (f: E => EitherT[F, E, A]): EitherT[F, E, A] =
      EitherT(fa.run >>= {
        case -\/(e) => f(e).run
        case right => right.pure[F]
      })
  }
~~~~~~~~

No debería sorprender que podamos reescribir el ejemplo con `MonadPlus` con
`MonadError`, insertando mensajes informativos de error:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Twitter](name: String)
                          (implicit F: MonadError[F, String]): F[Int] = for {
    user  <- T.getUser(name) >>= (_.orError(s"user '$name' not found")(F))
    stars <- T.getStars(user)
  } yield stars
~~~~~~~~

donde `.orError` es un método conveniente sobre `Maybe`

{lang="text"}
~~~~~~~~
  sealed abstract class Maybe[A] {
    ...
    def orError[F[_], E](e: E)(implicit F: MonadError[F, E]): F[A] =
      cata(F.point(_), F.raiseError(e))
  }
~~~~~~~~

A> Es común usar bloques de parámetros implícitos en lugar de límites de contexto
A> cuando la firma de una typeclass tiene más de un parámetro.
A>
A> También es práctica común nombrar el parámetro implícito después del tipo
A> primario, en este caso `F`.

La versión que usa `EitherT` directamente es:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): EitherT[F, String, Int] = for {
    user <- EitherT(T.getUser(name).map(_ \/> s"user '$name' not found"))
    stars <- EitherT.rightT(T.getStars(user))
  } yield stars
~~~~~~~~

La instancia más simple de `MonadError` es la de `\/`, perfecta para probar
lógica de negocios que es requerida por una `MonadError`. Por ejemplo,

{lang="text"}
~~~~~~~~
  final class MockTwitter extends Twitter[String \/ ?] {
    def getUser(name: String): String \/ Maybe[User] =
      if (name.contains(" ")) Maybe.empty.right
      else if (name === "wobble") "connection error".left
      else User(name).just.right
  
    def getStars(user: User): String \/ Int =
      if (user.name.startsWith("w")) 10.right
      else "stars have been replaced by hearts".left
  }
~~~~~~~~

Nuestras pruebas unitarias para `.stars` pueden cubrir estos casos:

{lang="text"}
~~~~~~~~
  scala> stars("wibble")
  \/-(10)
  
  scala> stars("wobble")
  -\/(connection error)
  
  scala> stars("i'm a fish")
  -\/(user 'i'm a fish' not found)
  
  scala> stars("fommil")
  -\/(stars have been replaced by hearts)
~~~~~~~~

Así como hemos visto varias veces, podemos enfocarnos en probar la lógica
de negocios sin distracciones.

Finalmente, si devolvemos nuestra álgebra `JsonClient` del capítulo 4.3

{lang="text"}
~~~~~~~~
  trait JsonClient[F[_]] {
    def get[A: JsDecoder](
      uri: String Refined Url,
      headers: IList[(String, String)]
    ): F[A]
    ...
  }
~~~~~~~~

recuerde que únicamente hemos codificado el camino feliz en la API. Si nuestro
intérprete para esta álgebra únicamente funciona para una `F` que tiene una
`MonadError`, podemos definir los tipos de errores como una preocupación
tangencial. En verdad, podemos tener **dos** capas de errores si definimos el
intérprete para una `EitherT[IO, JsonClient.Error, ?]`

{lang="text"}
~~~~~~~~
  object JsonClient {
    sealed abstract class Error
    final case class ServerError(status: Int)       extends Error
    final case class DecodingError(message: String) extends Error
  }
~~~~~~~~

que cubre los problemas de I/O (red), problemas de estado del servidor, y
asuntos con el modelado de los payloads JSON de nuestro servidor.

#### Escogiendo un tipo de errores

La comunidad no se ha decidido sobre la mejor estrategia para el tipo de
errores `E` en `MonadError`.

Una escuela de pensamiento dice que deberíamos escoger algo general, como una
cadena (`String`). La otra escuela de pensamiento dice que una aplicación
debería tener una ADT de errores, permitiendo que errores distintos sean
reportados o manejados de manera diferente. Una "pandilla" de gente prefiere
usar `Throwable` para tener máxima compatibilidad con la JVM.

Hay dos problemas con una ADT de errores a nivel de la aplicación:

- Es muy torpe crear un nuevo error. Una archivo se vuelve un repositorio
  monolítico de errores, agregando las ADTs de subsistemas individuales.
- Sin importar qué tan granular sea el reporte de errores, la solución es
  con frecuencia la misma, realice un log de los datos e intente de nuevo,
  o ríndase. No necesitamos una ADT para esto.

Una ADT de errores es útil si cada valor permite la ejecución de una estrategia
distinta de recuperación.

El compromiso entre una ADT de errores y una cadena `String` está en un formato
intermedio. JSON es una buena elección si puede entenderse por la mayoría de los
frameworks de loggeo y monitoreo.

Un problema con la ausencia de stacktraces es que puede ser complicado ubicar
cúal porción de código fue la causa del error. Con
[`sourcecode` de Li Haoyi](https://github.com/lihaoyi/sourcecode/), podemos incluir
información contextual como metadatos sobre nuestros errores:

{lang="text"}
~~~~~~~~
  final case class Meta(fqn: String, file: String, line: Int)
  object Meta {
    implicit def gen(implicit fqn: sourcecode.FullName,
                              file: sourcecode.File,
                              line: sourcecode.Line): Meta =
      new Meta(fqn.value, file.value, line.value)
  }
  
  final case class Err(msg: String)(implicit val meta: Meta)
~~~~~~~~

Aunque `Err` es referencialmente transparente, la construcción implícita de `Meta`
no parece ser referencialmente transparente desde una lectura natural:
dos invocaciones a `Meta.gen` (que se invoca implícitamente cuando se crea un
`Err`) producirán diferentes valores porque la ubicación en el código fuente
impacta el valor retornado:

{lang="text"}
~~~~~~~~
  scala> println(Err("hello world").meta)
  Meta(com.acme,<console>,10)
  
  scala> println(Err("hello world").meta)
  Meta(com.acme,<console>,11)
~~~~~~~~

Para entender esto, tenemos que apreciar que los métodos `sourcecode.*` son
macros que están generando código fuente para nosotros. Si tuvieramos que
escribir el código arriba de manera explícita sería claro lo que está
sucediendo:

{lang="text"}
~~~~~~~~
  scala> println(Err("hello world")(Meta("com.acme", "<console>", 10)).meta)
  Meta(com.acme,<console>,10)
  
  scala> println(Err("hello world")(Meta("com.acme", "<console>", 11)).meta)
  Meta(com.acme,<console>,11)
~~~~~~~~

Sí, hemos usado macros, pero también pudimos escribir `Meta` manualmente
y hubiera sido necesario hacerla obsoleta antes que nuestra documentación.

### `ReaderT`

La mónada reader envuelve `A => F[B]` permitiendo que un programa `F[B]` dependa
del valor de tiempo de ejecución `A`. Para aquellos que etán familiarizados con la inyección de dependencias, la mónada reader es el equivalente funcional de la
inyección `@Inject` de Spring o de Guice, sin el uso de XML o reflexión.

`ReaderT` es simplemente un alias de otro tipo de datos que con frecuencia es más
general, y que recibe su nombre en honor al matemático *Heinrich Kleisli*.

{lang="text"}
~~~~~~~~
  type ReaderT[F[_], A, B] = Kleisli[F, A, B]
  
  final case class Kleisli[F[_], A, B](run: A => F[B]) {
    def dimap[C, D](f: C => A, g: B => D)(implicit F: Functor[F]): Kleisli[F, C, D] =
      Kleisli(c => run(f(c)).map(g))
  
    def >=>[C](k: Kleisli[F, B, C])(implicit F: Bind[F]): Kleisli[F, A, C] = ...
    def >==>[C](k: B => F[C])(implicit F: Bind[F]): Kleisli[F, A, C] = this >=> Kleisli(k)
    ...
  }
  object Kleisli {
    implicit def kleisliFn[F[_], A, B](k: Kleisli[F, A, B]): A => F[B] = k.run
    ...
  }
~~~~~~~~

A> algunas personas llaman a `>->` el operador pez. Siempre existe un pez más
A> grande, y por lo tanto existe `>==>`. También se llaman las flechas de Kleisli.

Una conversión implícita en el objeto companion nos permite usar `Kleisli` en
lugar de una función, de modo que puede proporcionarse como el parámetro de
`.bind`, o `>>=`.

El uso más común para `ReaderT` es proporcionar información del contexto a un
programa. En `drone-dynamic-agents` necesitamos acceso al token Oauth 2.0 del
usuario para ser capaz de contactar a Google. El proceder obvio es cargar
`RefreshTokens` del disco al momento de arranque, y hacer que cada método tome
un parámetro `RefreshToken`. De hecho, se trata de un requerimiento tan común que
Martin Odersky propuso las [funciones implícitas](https://www.scala-lang.org/blog/2016/12/07/implicit-function-types.html).

Una mejor solución para nuestro programa es tener un álgebra de configuración que
proporcione la configuración cuando sea necesario, es decir

{lang="text"}
~~~~~~~~
  trait ConfigReader[F[_]] {
    def token: F[RefreshToken]
  }
~~~~~~~~

Hemos reinventado `MonadReader`, la typeclass que está asociada a `ReaderT`, donde
`.ask` es la misma que nuestra `.token` y `S` es `RefreshToken`:

{lang="text"}
~~~~~~~~
  @typeclass trait MonadReader[F[_], S] extends Monad[F] {
    def ask: F[S]
  
    def local[A](f: S => S)(fa: F[A]): F[A]
  }
~~~~~~~~

con la implementación

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad, R] = new MonadReader[Kleisli[F, R, ?], R] {
    def point[A](a: =>A): Kleisli[F, R, A] = Kleisli(_ => F.point(a))
    def bind[A, B](fa: Kleisli[F, R, A])(f: A => Kleisli[F, R, B]) =
      Kleisli(a => Monad[F].bind(fa.run(a))(f))
  
    def ask: Kleisli[F, R, R] = Kleisli(_.pure[F])
    def local[A](f: R => R)(fa: Kleisli[F, R, A]): Kleisli[F, R, A] =
      Kleisli(f andThen fa.run)
  }
~~~~~~~~

Una ley de `MonadReader` es que `S` no puede cambiar entre invocaciones, es decir
`ask >> ask === ask`. Para nuestro caso de uso, esto significa que la configuración
se lee una única vez. Si decidimos después que deseamos recargar nuestra
configuración cada vez que sea necesario, por ejemplo, al permitir el cambio de
token sin reiniciar la aplicación, podemos reintroducir `ConfigReader` que no tiene
tal ley.

En nuestra implementación OAuth 2.0 podemos primero mover la evidencia de `Monad`
a los métodos:

{lang="text"}
~~~~~~~~
  def bearer(refresh: RefreshToken)(implicit F: Monad[F]): F[BearerToken] =
    for { ...
~~~~~~~~

y entonces refactorizar el parámetro `refresh` para que sea parte de `Monad`

{lang="text"}
~~~~~~~~
  def bearer(implicit F: MonadReader[F, RefreshToken]): F[BearerToken] =
    for {
      refresh <- F.ask
~~~~~~~~

Cualquier parámetro puede moverse dentro de `MonadReader`. Esto es del mayor valor
posible para los usuarios que simplemente desean pasar esta información. Con
`ReaderT`, podemos reservar bloques de parámetros implícitos, para un uso exclusivo
de typeclases, reduciendo la carga mental que viene con el uso de Scala.

El otro método en `MonadReader` es `.local`

{lang="text"}
~~~~~~~~
  def local[A](f: S => S)(fa: F[A]): F[A]
~~~~~~~~

Podemos cambiar `S` y ejecutar un programa `fa` cdentro del contexto local,
devolviendo la `S` original. Un caso de uso para `.local` es la generación de un
stacktrace que tenga sentido para nuestro dominio, ¡proporcionándonos un logging
anidado! A partir de lo que aprendimos en nuestra estructura de datos `Meta`
de la sección anterior, definimos una función para realizar un chequeo:

{lang="text"}
~~~~~~~~
  def traced[A](fa: F[A])(implicit F: MonadReader[F, IList[Meta]]): F[A] =
    F.local(Meta.gen :: _)(fa)
~~~~~~~~

y la podemos usar para envolver funciones que operan en este contexto

{lang="text"}
~~~~~~~~
  def foo: F[Foo] = traced(getBar) >>= barToFoo
~~~~~~~~

automáticamente pasando cualquier información que no se esté rastreando de manera
explícita. Un plugin para el compilador o una macro podría realizar lo opuesto,
haciendo que todo se realice por default.

Si accedemos a `.ask` podemos ver el rastro completo de cómo se realizaron las
llamadas, sin la distracci[on de los detalles en la implementación del bytecode.
¡Un stacktrace referencialmente transparente!

Un programador a la defensiva podría truncar la `IList[Meta]` a cierta longitud
para evitar el equivalente de un sobreflujo de pila. En realidad, una estructura
de datos más apropiada es `Dequeue`.

`.local` puede también usarse para registrar la información contextual que es
directamente relavante a la tarea que se está realizando, como el número de
espacios que debemos indentar una línea cuando se está imprimiendo un archivo
con formato legible por humanos, haciendo que esta indentación aumente en dos
espacios cuando introducimos una estructura anidada.

Finalmente, si no podemos pedir una `MonadReader` porque nuestra aplicación no
proporciona una, siempre podemos devolver un `ReaderT`.

{lang="text"}
~~~~~~~~
  def bearer(implicit F: Monad[F]): ReaderT[F, RefreshToken, BearerToken] =
    ReaderT( token => for {
    ...
~~~~~~~~

Si alguien que recibe un `ReaderT`, y tienen el parámetro `token` a la mano,
entonces pueden invocar `access.run(token)` y tener de vuelta un `F[BearerToken]`.

Dado que no tenemos muchos callers, deberíamos simplemente revertir a un parámetro
de función regular. `MonadReader` es de mayor utilidad cuando:

1. Deseamos refactorizar el código más tarde para recargar la configuración
2. El valor no es necesario por usuarios  (llamadas) intermedias
3. o, cuando deseamos restringir el ámbito/alcance para que sea local

Dotty puede quedarse con sus funciones implícitas... nosotros ya tenemos `ReaderT`
y `MonadReader`.

### `WriterT`

Lo opuesto a la lectura es la escritura. El transformador de mónadas `WriterT` es
usado típicamente para escribir a un journal.

{lang="text"}
~~~~~~~~
  final case class WriterT[F[_], W, A](run: F[(W, A)])
  object WriterT {
    def put[F[_]: Functor, W, A](value: F[A])(w: W): WriterT[F, W, A] = ...
    def putWith[F[_]: Functor, W, A](value: F[A])(w: A => W): WriterT[F, W, A] = ...
    ...
  }
~~~~~~~~

El tipo envuelto es `F[(W, A)]` y el journal se acumula en `W`.

¡No hay únicamente una mónada asociada, sino dos! `MonadTell` y `MonadListen`

{lang="text"}
~~~~~~~~
  @typeclass trait MonadTell[F[_], W] extends Monad[F] {
    def writer[A](w: W, v: A): F[A]
    def tell(w: W): F[Unit] = ...
  
    def :++>[A](fa: F[A])(w: =>W): F[A] = ...
    def :++>>[A](fa: F[A])(f: A => W): F[A] = ...
  }
  
  @typeclass trait MonadListen[F[_], W] extends MonadTell[F, W] {
    def listen[A](fa: F[A]): F[(A, W)]
  
    def written[A](fa: F[A]): F[W] = ...
  }
~~~~~~~~

`MonadTell` es para escribir al journal y `MonadListen` es para recuperarlo.
La implementación de `WriterT` es

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad, W: Monoid] = new MonadListen[WriterT[F, W, ?], W] {
    def point[A](a: =>A) = WriterT((Monoid[W].zero, a).point)
    def bind[A, B](fa: WriterT[F, W, A])(f: A => WriterT[F, W, B]) = WriterT(
      fa.run >>= { case (wa, a) => f(a).run.map { case (wb, b) => (wa |+| wb, b) } })
  
    def writer[A](w: W, v: A) = WriterT((w -> v).point)
    def listen[A](fa: WriterT[F, W, A]) = WriterT(
      fa.run.map { case (w, a) => (w, (a, w)) })
  }
~~~~~~~~

El ejemplo más obvio es usar `MonadTell` para loggear información, o para
reportar la auditoría. Reusar `Meta` para reportar errores podría servir para
crear una estructura de log como

{lang="text"}
~~~~~~~~
  sealed trait Log
  final case class Debug(msg: String)(implicit m: Meta)   extends Log
  final case class Info(msg: String)(implicit m: Meta)    extends Log
  final case class Warning(msg: String)(implicit m: Meta) extends Log
~~~~~~~~

y usar `Dequeue[Log]` como nuestro tipo de journal. Podríamos cambiar
nuestro método OAuth2 `authenticate` a

{lang="text"}
~~~~~~~~
  def debug(msg: String)(implicit m: Meta): Dequeue[Log] = Dequeue(Debug(msg))
  
  def authenticate: F[CodeToken] =
    for {
      callback <- user.start :++> debug("started the webserver")
      params   = AuthRequest(callback, config.scope, config.clientId)
      url      = config.auth.withQuery(params.toUrlQuery)
      _        <- user.open(url) :++> debug(s"user visiting $url")
      code     <- user.stop :++> debug("stopped the webserver")
    } yield code
~~~~~~~~

Incluso podríamos conbinar esto con las trazas de `ReaderT` y tener logs
estructurados.

El que realiza la llamada puede recuperar los logs con `.written` y hacer algo
con ellos.

Sin embargo, existe el argumento fuerte de que el logging merece su propia álgebra.
El nivel de log es con frecuencia necesario en el momento de creación por razones
de rendimiento y la escritura de logs es típicamente manejado a nivel de la
aplicación más bien que algo sobre lo que cada componente necesite estar preocupado.

La `W` en `WriterT` tiene un `Monoid`, permitiéndonos escribir al journal cualquier
clase de cálculoo monoidal como un valor secundario junto con nuestro programa
principal. Por ejemplo, el conteo del número de veces que hacemos algo, construyendo
una explicación del cálculo, o la construcción de una `TradeTemplate` para una nueva
transacción mientras que asignamos un precio.

Una especialización popular de `WriterT` ocurre cuando la mónada es `Id`, indicando
que el valor subyacente `run` es simplemente la tupla simple `(W, A)`.

{lang="text"}
~~~~~~~~
  type Writer[W, A] = WriterT[Id, W, A]
  object WriterT {
    def writer[W, A](v: (W, A)): Writer[W, A] = WriterT[Id, W, A](v)
    def tell[W](w: W): Writer[W, Unit] = WriterT((w, ()))
    ...
  }
  final implicit class WriterOps[A](self: A) {
    def set[W](w: W): Writer[W, A] = WriterT(w -> self)
    def tell: Writer[A, Unit] = WriterT.tell(self)
  }
~~~~~~~~

que nos permite dejar que cualquier valor lleve consigo un cálculo monoidal
secundario, sin la necesidad de un contexto `F[_]`.

En resumen, `WriterT` / `MonadTell` es la manera de conseguir multi tareas en la
programación funcional.

### `StateT`

`StateT` nos permite ejecutar `.put`, `.get` y `.modify` sobre un valor que es
manejado por el contexto monádico. Es el reemplazo funcional de `var`.

Si fueramos a escribir un método impuro que tiene acceso a algún estado mutable,
y contenido en un `var`, pudiera haber tenido la firma `() => F[A]` y devolver un
valor diferente en cada invocación, rompiendo con la transparencia referencial.
Con la programación funcional pura, la función toma el estado como la entrada y
devuelve el estado actualizado como la salida, que es la razón por la que el tipo
subyacente de `StateT` es  `S => F[(S, A)]`.

La mónada asociada es `MonadState`

{lang="text"}
~~~~~~~~
  @typeclass trait MonadState[F[_], S] extends Monad[F] {
    def put(s: S): F[Unit]
    def get: F[S]
  
    def modify(f: S => S): F[Unit] = get >>= (s => put(f(s)))
    ...
  }
~~~~~~~~

A> `S` debe ser de un tipo inmutable: `.modify` no es un escape para tener la
A> capacidad de actualizar una estructura de datos mutable. La mutabilidad es
A> impura y sólo se permite dentro de un bloque `IO`.

`StateT` se implementa de manera ligeramente diferente que los transformadores
de mónada que hemos estudiado hasta el momento. En lugar de usar un `case class`
es una ADT con dos miembros:

{lang="text"}
~~~~~~~~
  sealed abstract class StateT[F[_], S, A]
  object StateT {
    def apply[F[_], S, A](f: S => F[(S, A)]): StateT[F, S, A] = Point(f)
  
    private final case class Point[F[_], S, A](
      run: S => F[(S, A)]
    ) extends StateT[F, S, A]
    private final case class FlatMap[F[_], S, A, B](
      a: StateT[F, S, A],
      f: (S, A) => StateT[F, S, B]
    ) extends StateT[F, S, B]
    ...
  }
~~~~~~~~

que es una forma especializada de `Trampoline`, proporcionandonos seguridad en el
uso de la pila cuando deseamos recuperar la estructura de datos subyacente, `.run`:

{lang="text"}
~~~~~~~~
  sealed abstract class StateT[F[_], S, A] {
    def run(initial: S)(implicit F: Monad[F]): F[(S, A)] = this match {
      case Point(f) => f(initial)
      case FlatMap(Point(f), g) =>
        f(initial) >>= { case (s, x) => g(s, x).run(s) }
      case FlatMap(FlatMap(f, g), h) =>
        FlatMap(f, (s, x) => FlatMap(g(s, x), h)).run(initial)
    }
    ...
  }
~~~~~~~~

`StateT` puede implemnentar de manera directa `MonadState` con su ADT:

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Applicative, S] = new MonadState[StateT[F, S, ?], S] {
    def point[A](a: =>A) = Point(s => (s, a).point[F])
    def bind[A, B](fa: StateT[F, S, A])(f: A => StateT[F, S, B]) =
      FlatMap(fa, (_, a: A) => f(a))
  
    def get       = Point(s => (s, s).point[F])
    def put(s: S) = Point(_ => (s, ()).point[F])
  }
~~~~~~~~

con `.pure` reflejado en el objeto compañero como `.stateT`:

{lang="text"}
~~~~~~~~
  object StateT {
    def stateT[F[_]: Applicative, S, A](a: A): StateT[F, S, A] = ...
    ...
  }
~~~~~~~~

y `MonadTrans.liftM` proporcionando el constructor `F[A] => StateT[F, S, A]` como
es usual.

Una variante común de `StateT` es cuando `F = Id`, proporcionando la signatura de
tipo subyacente `S => (S, A)`. Scalaz proporciona un alias de tipo y funciones
convenientes para interactuar con el transformador de mónadas `State` de manera
directa, y reflejando `MonadState`:

{lang="text"}
~~~~~~~~
  type State[a] = StateT[Id, a]
  object State {
    def apply[S, A](f: S => (S, A)): State[S, A] = StateT[Id, S, A](f)
    def state[S, A](a: A): State[S, A] = State((_, a))
  
    def get[S]: State[S, S] = State(s => (s, s))
    def put[S](s: S): State[S, Unit] = State(_ => (s, ()))
    def modify[S](f: S => S): State[S, Unit] = ...
    ...
  }
~~~~~~~~

Para un ejemplo podemos regresar a las pruebas de lógica de negocios de
`drone-dynamic-agents`. Recuerde del Capítulo 3 que creamos `Mutablep  como
intérpretes de prueba para nuestra aplicación y que almacenamos el número de
`started` y los nodos `stopped` en una `var`.

{lang="text"}
~~~~~~~~
  class Mutable(state: WorldView) {
    var started, stopped: Int = 0
  
    implicit val drone: Drone[Id] = new Drone[Id] { ... }
    implicit val machines: Machines[Id] = new Machines[Id] { ... }
    val program = new DynAgentsModule[Id]
  }
~~~~~~~~

Ahora sabemos que podemos escribir un mucho mejor simulador de pruebas con `State`.
Tomaremos la oportunidad de hacer un ajuste a la precisión de nuestra simulación
al mismo tiempo. Recuerde que un objeto clave de nuestro dominio de aplicación es
la visión del mundo:

{lang="text"}
~~~~~~~~
  final case class WorldView(
    backlog: Int,
    agents: Int,
    managed: NonEmptyList[MachineNode],
    alive: Map[MachineNode, Epoch],
    pending: Map[MachineNode, Epoch],
    time: Epoch
  )
~~~~~~~~

Dado que estamos escribiendo una simulación del mundo para nuestras pruebas, podemos
crear un tipo de datos que capture la realidad de todo

{lang="text"}
~~~~~~~~
  final case class World(
    backlog: Int,
    agents: Int,
    managed: NonEmptyList[MachineNode],
    alive: Map[MachineNode, Epoch],
    started: Set[MachineNode],
    stopped: Set[MachineNode],
    time: Epoch
  )
~~~~~~~~

A> No hemos reescrito la aplicación para que haga un uso completo de las estructuras
A> de datos de Scalaz y de las typeclases, y todavía estamos dependiendo de las
A> colecciones de la librería estándar. No hay urgencia en actualizar la
A> implementación dado es sencillo y estos tipos pueden usarse de una manera
A> funcionalmente pura.

La diferencia principal es que los nodos que están en los estados `started` y
`stopped` pueden ser separados. Nuestr intérprete puede ser implementado en
términos de `State[World, a]` y popdemos escribir nuestras pruebas para realizar
aserciones sobre la forma en la que se ve el `World` y `WorldView` después de
haber ejecutado la lógica de negocios.

Los intérpretes, que están simulando el contacto externo con los servicios
Drone y Google, pueden implementarse como:

{lang="text"}
~~~~~~~~
  import State.{ get, modify }
  object StateImpl {
    type F[a] = State[World, a]
  
    private val D = new Drone[F] {
      def getBacklog: F[Int] = get.map(_.backlog)
      def getAgents: F[Int]  = get.map(_.agents)
    }
  
    private val M = new Machines[F] {
      def getAlive: F[Map[MachineNode, Epoch]]   = get.map(_.alive)
      def getManaged: F[NonEmptyList[MachineNode]] = get.map(_.managed)
      def getTime: F[Epoch]                      = get.map(_.time)
  
      def start(node: MachineNode): F[Unit] =
        modify(w => w.copy(started = w.started + node))
      def stop(node: MachineNode): F[Unit] =
        modify(w => w.copy(stopped = w.stopped + node))
    }
  
    val program = new DynAgentsModule[F](D, M)
  }
~~~~~~~~

y podemos reescribir nuestras pruebas para seguir la convención donde:

- `world1` es el estado del mundo antes de ejecutar el programa
- `view1` es la creencia/visión de la aplicación sobre el mundo
- `world2` es el estado del mundo después de ejecutar el programa
- `view2` es la creencia/visión sobre la aplicación después de ejecutar el programa

Por ejemplo,

{lang="text"}
~~~~~~~~
  it should "request agents when needed" in {
    val world1          = World(5, 0, managed, Map(), Set(), Set(), time1)
    val view1           = WorldView(5, 0, managed, Map(), Map(), time1)
  
    val (world2, view2) = StateImpl.program.act(view1).run(world1)
  
    view2.shouldBe(view1.copy(pending = Map(node1 -> time1)))
    world2.stopped.shouldBe(world1.stopped)
    world2.started.shouldBe(Set(node1))
  }
~~~~~~~~

Esperemos que el lector nos perdone al mirar atrás a nuestro bucle anterior con
implementación de lógica de negocio

{lang="text"}
~~~~~~~~
  state = initial()
  while True:
    state = update(state)
    state = act(state)
~~~~~~~~

y usar `StateT` para manejar el estado (`state`). Sin embargo, nuestra lógica de
negocios `DynAgents` requiere únicamente de `Applicative` y estaríamos violando
la *ley del poder mínimo* al requerir `MonadState` que es estrictamente más poderoso.
Es, por lo tanto, enteramente razonable manejar el estado manualmente al pasarlo en
un `update` y `act`, y dejar que quien realiza una llamada use un `StateT` si así
lo desea.

### `IndexedStateT`

El código que hemos estudiado hasta el momento no es como Scalaz implementa
`StateT`. En lugar de esto, un type alias apunta a `IndexedStateT`

{lang="text"}
~~~~~~~~
  type StateT[F[_], S, A] = IndexedStateT[F, S, S, A]
~~~~~~~~

La implementación de `IndexedStateT` es básicamente la que ya hemos estudiado,
con un parámetro de tipo extra que permiten que los estados de entrada `S1` y
`S2` difieran:

{lang="text"}
~~~~~~~~
  sealed abstract class IndexedStateT[F[_], -S1, S2, A] {
    def run(initial: S1)(implicit F: Bind[F]): F[(S2, A)] = ...
    ...
  }
  object IndexedStateT {
    def apply[F[_], S1, S2, A](
      f: S1 => F[(S2, A)]
    ): IndexedStateT[F, S1, S2, A] = Wrap(f)
  
    private final case class Wrap[F[_], S1, S2, A](
      run: S1 => F[(S2, A)]
    ) extends IndexedStateT[F, S1, S2, A]
    private final case class FlatMap[F[_], S1, S2, S3, A, B](
      a: IndexedStateT[F, S1, S2, A],
      f: (S2, A) => IndexedStateT[F, S2, S3, B]
    ) extends IndexedStateT[F, S1, S3, B]
    ...
  }
~~~~~~~~

`IndexedStateT` no tiene una `MonadState` cuando `S1 != s2`, aunque sí tiene una
`Monad`.

El siguiente ejemplo está adaptado de [Index your State](https://www.youtube.com/watch?v=JPVagd9W4Lo) de Vincent Marquez.

Considere el escenario en el que deseamos diseñar una interfaz algebraica para una
búsqueda de un mapeo de un `Int`a un `String`. Se puede tratar de una implementación
en red y el orden de las invocaciones es esencial. Nuestro primer intento de
realizar la API es algo como lo siguiente:

{lang="text"}
~~~~~~~~
  trait Cache[F[_]] {
    def read(k: Int): F[Maybe[String]]
  
    def lock: F[Unit]
    def update(k: Int, v: String): F[Unit]
    def commit: F[Unit]
  }
~~~~~~~~

con errores en tiempo de ejecución si `.update` o `.commit` es llamada sin un
`.lock`. Un diseño más complejo puede envolver múltiples traits y una DSL a la medida
que nadie recuerde cómo usar.

En vez de esto, podemos usar `IndexedStateT` para requerir que la invocación se
realice en el estado correcto. Primero definimos nuestros estados posibles como una
ADT.

{lang="text"}
~~~~~~~~
  sealed abstract class Status
  final case class Ready()                          extends Status
  final case class Locked(on: ISet[Int])            extends Status
  final case class Updated(values: Int ==>> String) extends Status
~~~~~~~~

y entonces revisitar nuestra álgebra

{lang="text"}
~~~~~~~~
  trait Cache[M[_]] {
    type F[in, out, a] = IndexedStateT[M, in, out, a]
  
    def read(k: Int): F[Ready, Ready, Maybe[String]]
    def readLocked(k: Int): F[Locked, Locked, Maybe[String]]
    def readUncommitted(k: Int): F[Updated, Updated, Maybe[String]]
  
    def lock: F[Ready, Locked, Unit]
    def update(k: Int, v: String): F[Locked, Updated, Unit]
    def commit: F[Updated, Ready, Unit]
  }
~~~~~~~~


lo que nos ocasionará un error en tiempo de compilación si intentamos realizar
un `.update` sin un `.lock`

{lang="text"}
~~~~~~~~
  for {
        a1 <- C.read(13)
        _  <- C.update(13, "wibble")
        _  <- C.commit
      } yield a1
  
  [error]  found   : IndexedStateT[M,Locked,Ready,Maybe[String]]
  [error]  required: IndexedStateT[M,Ready,?,?]
  [error]       _  <- C.update(13, "wibble")
  [error]          ^
~~~~~~~~

pero nos permite construir funciones que pueden componerse al incluirlas
explícitamente en su estado:

{lang="text"}
~~~~~~~~
  def wibbleise[M[_]: Monad](C: Cache[M]): F[Ready, Ready, String] =
    for {
      _  <- C.lock
      a1 <- C.readLocked(13)
      a2 = a1.cata(_ + "'", "wibble")
      _  <- C.update(13, a2)
      _  <- C.commit
    } yield a2
~~~~~~~~

A> Hemos introducido algo de duplicación en el código en nuestra API cuando definimos
A> operaciones múltiples `.read`
A>
A>  {lang="text"}
A> ~~~~~~~~
A>   def read(k: Int): F[Ready, Ready, Maybe[String]]
A>   def readLocked(k: Int): F[Locked, Locked, Maybe[String]]
A>   def readUncommitted(k: Int): F[Updated, Updated, Maybe[String]]
A> ~~~~~~~~
A> 
A> en lugar de
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def read[S <: Status](k: Int): F[S, S, Maybe[String]]
A> ~~~~~~~~
A>
A> La razón por la que no hacemos esto es, *debido al subtipo*. Este código (malo)
A> podría compilar con la signatura de tipo inferido
A> `F[Nothing, Ready, Maybe[String]]`
A>
A> {lang="text"}
A> ~~~~~~~~
A>   for {
A>     a1 <- C.read(13)
A>     _  <- C.update(13, "wibble")
A>     _  <- C.commit
A>   } yield a1
A> ~~~~~~~~
A> 
A> Scala tiene un tipo `Nothing` que es subtipo de todos los otros tipos. Es una
A> buena noticia que este código no llegue a tiempo de ejecución, dado que sería
A> imposible invocarlo, pero es una mala API dado que usuarios tendrían que
A> recordar agregar adscripciones de tipo.
A>
A> Otro enfoque para resolver el problema sería evitar que el compilador infiera
A> `Nothing`. Scalaz proporciona evidencia implícita para realizar aserciones de
A> que un tipo no es inferido como `Nothing` y podemos usar este mecanismo en
A> lugar de esto:
A>
A> A> {lang="text"}
A> ~~~~~~~~
A>   def read[S <: Status](k: Int)(implicit NN: NotNothing[S]): F[S, S, Maybe[String]]
A> ~~~~~~~~
A>
A> La elección de cual de las tres alternativas de diseño de la API usar, se deja
A> al gusto personal del diseñador de la API.

