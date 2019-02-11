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