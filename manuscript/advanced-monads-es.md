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
