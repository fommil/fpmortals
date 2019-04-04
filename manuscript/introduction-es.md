# Introducción

Es parte de la naturaleza humana permanecer escéptico a un nuevo paradigma.
Para poner en perspectiva qué tan lejos hemos llegado, y los cambios que ya
hemos aceptado en la JVM (de las siglas en inglés para Máquina Virtual de
Java), empecemos recapitulando brevemente los últimos 20 años.

Java 1.2 introdujo la API de Colecciones, permitiéndonos escribir métodos que
abstraen sobre colecciones mutables. Era útil para escribir algoritmos de
propósito general y era el fundamento de nuestras bases de código.

Pero había un problema, teníamos que hacer *casting* en tiempo de ejecución:

```java
  public String first(Collection collection) {
    return (String)(collection.get(0));
  }
```

En respuesta, los desarrolladores definieron objectos en su lógica de negocios
que eran efectivamente `ColeccionesDeCosas`, y la API de Colecciones se
convirtió en un detalle de implementación.

En 2005, Java 5 introdujo los *genéricos*, permitiéndonos definir una
`Coleccion<Cosa>`, abstrayendo no sólo el contenedor sino sus elementos. Los
genéricos cambiaron la forma en que escribimos Java.

El autor del compilador de genéricos para Java, Martin Odersky, creó Scala
después, con un systema de tipo más fuerte, estructuras inmutables y herencia
múltiple. Esto trajo consigo una fusión de la Programación Orientada a Objectos
(POO) y la Programación Funcional (PF).

Para la mayoría de los desarrolladores, PF significa usar datos inmutables tanto
como sea posible, pero consideran que el estado mutable todavía es un mal
necesario que debe aislarse y controlarse, por ejemplo con actores de Akka o
clases sincronizadas. Este estilo de PF resulta en programas simples que son
fáciles de paralelizar y distribuir: definitivamente es una mejora con respecto
a Java. Pero este enfoque solo toca la amplia superficie de beneficios de PF,
como descubriremos en este libro.

Scala también incorpora `Future`, haciendo simple escribir aplicaciones
asíncronas. Pero cuando un `Future` se usa en un tipo de retorno, *todo*
necesita reescribirse para usarlos, incluyendo las pruebas, que ahora están
sujetas a *timeouts* arbitrarios.

Nos encontramos con un problema similar al que se tuvo con Java 1.0: no hay
forma de abstraer la ejecución, así como no se tenía una manera de abstraer
sobre las colecciones.

## Abstrayendo sobre la ejecución

Suponga que deseamos interactuar con el usuario a través de la línea de
comandos. Podemos leer (`read`) lo que el usuario teclea y entonces podemos
escribirle (`write`) un mensaje.

```scala
  trait TerminalSync {
    def read(): String
    def write(t: String): Unit
  }

  trait TerminalAsync {
    def read(): Future[String]
    def write(t: String): Future[Unit]
  }
```

¿Cómo podemos escribir código genérico que haga algo tan simple como un eco de
la entrada del usuario, ya sea de manera síncrona o asíncrona, dependiendo de
nuestra implementación para tiempo de ejecución?

Podríamos escribir una versión síncrona y envolverla en un `Future` pero ahora
tendríamos que preocuparnos por cuál *thread pool* debemos usar para ejecutar el
trabajo, o podríamos esperar el `Future` con `Await.result` y bloquear el
*thread*. En ambos casos, es mucho código tedioso y fundamentalmente estamos
lidiando con dos *APIs* que no están unificadas.

Podríamos resolver el problema, como con Java 1.2, con un padre común usando el
soporte de Scala para *higher-kinded types* (HKT).

A> **Higher-Kinded Types** nos permiten usar un *constructor de tipo* en
A> nuestros parámetros de tipo, lo cuál se ve como `C[_]`. Esta es una forma de
A> decir que sin importar qué sea `C`, debe tomar un parámetro de tipo. Por
A> ejemplo:
A>
A> ```scala
A>   trait Foo[C[_]] {
A>     def create(i: Int): C[Int]
A>   }
A> ```
A>
A> `List` es un constructor de tipo porque acepta un tipo (por ejemplo, `Int`) y
A> construye otro tipo (`List[Int]`). Podemos implementar `Foo` usando `List`:
A>
A> ```scala
A>   object FooList extends Foo[List] {
A>     def create(i: Int): List[Int] = List(i)
A>   }
A> ```
A>
A> Podemos implementar `Foo` para cualquier cosa con un hoyo en el parámetro de
A> tipo, por ejemplo `Either[String, _]`. Desafortunadamente es necesario crear
A> un alias de tipo (*type alias*) para lograr que el compilador lo acepte:
A> ```scala
A>   type EitherString[T] = Either[String, T]
A> ```
A>
A> Los alias de tipo no definen nuevos tipos, simplemente usan sustitución y no
A> proporcionan seguridad adicional con tipos. El compilador sustituye
A> `EitherString[T]` con `Either[String, T]` en todos lados. Esta técnica puede
A> usarse para conseguir que el compilador acepte tipos con un hoyo cuando de
A> otra manera detectaría que hay dos, como cuando implementamos `Foo` con
A> `EitherString`:
A>
A> ```scala
A>   object FooEitherString extends Foo[EitherString] {
A>     def create(i: Int): Either[String, Int] = Right(i)
A>   }
A> ```
A>
A> Una alternativa es usar el plugin [kind
A> projector](https://github.com/non/kind-projector/) que permite evitar el
A> alias de tipo y usar la sintaxis `?` para indicar al compilador dónde se
A> encuentra el hoyo de tipo.
A>
A> ```scala
A>   object FooEitherString extends Foo[Either[String, ?]] {
A>     def create(i: Int): Either[String, Int] = Right(i)
A>   }
A> ```
A>
A> Finalmente, podemos usar un truco cuando se desee ignorar el constructor de
A> tipo. Defina un type alias que sea igual a su parámetro:
A>
A> ```scala
A>   type Id[T] = T
A> ```
A>
A> Antes de continuar, entiéndase que `Id[Int]` es lo mismo que `Int`, si se
A> sustituye `T` por `Int`. Debido a que `Id` es un constructor de tipo válido,
A> podemos usar `Id` en una implementación de `Foo`
A>
A> ```scala
A>   object FooId extends Foo[Id] {
A>     def create(i: Int): Int = i
A>   }
A> ```

Deseamos definir `Terminal` para un constructor de tipo `C[_]`. Si definimos
`Now` (en español, *ahora*) de modo que sea equivalente a su parámetro de tipo
(como `Id`), es posible implementar una interfaz común para terminales síncronas
y asíncronas:

```scala
  trait Terminal[C[_]] {
    def read: C[String]
    def write(t: String): C[Unit]
  }

  type Now[X] = X

  object TerminalSync extends Terminal[Now] {
    def read: String = ???
    def write(t: String): Unit = ???
  }

  object TerminalAsync extends Terminal[Future] {
    def read: Future[String] = ???
    def write(t: String): Future[Unit] = ???
  }
```

Podemos pensar en `C` como un *Contexto* porque decimos "en el contexto de
ejecución `Now` (ahora)" o "en el `Futuro`".

Pero no sabemos nada sobre `C` y tampoco podemos hacer algo con un `C[String]`.
Lo que necesitamos es un contexto/ambiente de ejecución que nos permita invocar
un método que devuelva `C[T]` y después sea capaz de hacer algo con la `T`,
incluyendo la invocación de otro método sobre `Terminal`. También necesitamos
una forma de envolver un valor como un `C[_]`. La siguiente signatura funciona
bien:

```scala
  trait Execution[C[_]] {
    def chain[A, B](c: C[A])(f: A => C[B]): C[B]
    def create[B](b: B): C[B]
  }
```

que nos permite escribir:

```scala
  def echo[C[_]](t: Terminal[C], e: Execution[C]): C[String] =
    e.chain(t.read) { in: String =>
      e.chain(t.write(in)) { _: Unit =>
        e.create(in)
      }
    }
```

Ahora podemos compartir la implementación de `echo` en código síncrono y
asíncrono. Podemos escribir una implementación simulada de `Terminal[Now]` y
usarla en nuestras pruebas sin ningún tiempo límite (*timeout*).

Las implementaciones de `Execution[Now]` y `Execution[Future]` son reusables por
métodos genéricos como `echo`.

Pero el código para `echo` es horrible!

La característica de clases implícitas del lenguaje Scala puede darle a `C`
algunos métodos. Llamaremos a estos métodos `flatMap` y `map` por razones que
serán más claras en un momento. Cada método acepta un `implicit Execution[C]`.
Estos no son más que los métodos `flatMap` y `map` a los que estamos
acostumbrados a usar con `Seq`, `Option` y `Future`.

```scala
  object Execution {
    implicit class Ops[A, C[_]](c: C[A]) {
      def flatMap[B](f: A => C[B])(implicit e: Execution[C]): C[B] =
            e.chain(c)(f)
      def map[B](f: A => B)(implicit e: Execution[C]): C[B] =
            e.chain(c)(f andThen e.create)
    }
  }

  def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
    t.read.flatMap { in: String =>
      t.write(in).map { _: Unit =>
        in
      }
    }
```

Ahora podemos revelar porqué usamos `flatMap` como el nombre del método: nos
permite usar una *for comprehension*, que es una conveniencia sintáctica
(*syntax sugar*) para `flatMap` y `map` anidados.

```scala
  def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
    for {
      in <- t.read
       _ <- t.write(in)
    } yield in
```

Nuestro contexto `Execution` tiene las mismas signaturas que una `trait` en
Scalaz llamada `Monad`, excepto que `chain` es `bind` y `create` es `pure`.
Decimos que `C` es *monádica* cuando hay una `Monad[C]` implícita disponible en
el ámbito. Además, Scalaz tiene el alias de tipo `Id`.

En resumen, si escribimos métodos que operen en tipos monádicos, entonces
podemos escribir código sequencial que puede abstraer sobre su contexto de
ejecución. Aquí, hemos mostrado una abstracción sobre la ejecución síncrona y
asíncrona pero también puede ser con el propósito de conseguir un manejo más
riguroso de los errores (donde `C[_]` es `Either[Error, ?]`), administrar o
controlar el acceso a estado volátil, realizar I/O, o la revisión de la sesión.

## Programación Funcional Pura

La programación funcional es el acto de escribir programas con *funciones
puras*. Las funciones puras tienen tres propiedades:

- **Totales**: devuelven un valor para cada entrada posible.
- **Deterministas**: devuelven el mismo valor para la misma entrada.
- **Inculpable**: no interactúan (directamente) con el mundo o el estado del programa.

Juntas, estas propiedades nos dan una habilidad sin precedentes para razonar
sobre nuestro código. Por ejemplo, la validación de entradas es más fácil de
aislar gracias a la totalidad, el almacenamiento en caché es posible cuando las
funciones son deterministas, y la interacción con el mundo es más fácil de
controlar, y probar, cuando las funciones son inculpables.

Los tipos de cosas que violan estas propiedades son *efectos laterales*: el
acceso o cambio directo de estado mutable (por ejemplo, mantener una `var` en
una clase o el uso de una API antigua e impura), la comunicación con recursos
externos (por ejemplo, archivos o una búsqueda en la red), o lanzar y atrapar
excepciones.

Escribimos funciones puras mediante evitar las excepciones, e interactuando con
el mundo únicamente a través de un contexto de ejecución `F[_]` seguro.

En la sección previa, se hizo una abstracción sobre la ejecución y se definieron
`echo[Id]` y `echo[Future]`. Tal vez esperaríamos, razonablemente, que la
invocación de cualquier `echo` no realizara ningún efecto lateral, porque es
puro. Sin embargo, si usamos `Future` o `Id` como nuestro contexto de ejecución,
nuestra aplicación empezará a escuchar a `stdin`:

```scala
  val futureEcho: Future[String] = echo[Future]
```

Estaríamos violando la pureza y no estaríamos escribiendo código puramente
funcional: `futureEcho`es el resultado de invocar `echo` una vez. `Future`
combina la definición de un programa con su *interpretación* (su ejecución).
Como resultado, es más difícil razonar sobre las aplicaciones construidas con
`Future`.

A> Una expresión es *referencialmente transparente* si puede ser reemplazada con
A> su correspondiente valor sin cambiar el comportamiento del programa.
A>
A> Las funciones puras son referencialmente transparentes, permitiendo un alto
A> grado de reutilización del código, optimización del rendimiento, comprensión
A> y control de un programa.
A>
A> Las funciones impuras no son referencialmente transparentes. No podemos
A> reemplazar `echo[Future]` con un valor, tal como `val futureEcho`, dado que
A> el usuario podría teclear algo diferente la segunda vez.

Podemos definir un contexto de ejecución simple `F[_]`:

```scala
  final class IO[A](val interpret: () => A) {
    def map[B](f: A => B): IO[B] = IO(f(interpret()))
    def flatMap[B](f: A => IO[B]): IO[B] = IO(f(interpret()).interpret())
  }
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(() => a)
  }
```

que evalúa un thunk de manera perezosa (o por necesidad). `IO` es simplemente
una estructura de datos que referencia (posiblemente) código impuro, y no está,
en realidad, ejecutando algo. Podemos implementar `Terminal[IO]`:

```scala
  object TerminalIO extends Terminal[IO] {
    def read: IO[String]           = IO { io.StdIn.readLine }
    def write(t: String): IO[Unit] = IO { println(t) }
  }
```

e invocar `echo[IO]` para obtener de vuelta el valor

```scala
  val delayed: IO[String] = echo[IO]
```

Este `val delayed` puede ser reutilizado, es simplemente la definición del
trabajo que debe hacerse. Podemos mapear la cadena y componer (en el sentido
funcional) programas, tal como podríamos mapear un `Futuro`. `IO` nos mantiene
honestos al hacer explícito que dependemos de una interacción con el mundo, pero
no nos detiene de acceder a la salida de tal interacción.

El código impuro dentro de `IO` solo es evaluado cuando se invoca `.interpret()`
sobre el valor, y se trata de una acción impura

```scala
  delayed.interpret()
```

Una aplicación compuesta de programas `IO` solamente se interpreta una vez, en
el método `main`, que tambié se llama *el fin del mundo*.

En este libro, expandiremos los conceptos introducidos en este capítulo y
mostraremos cómo escribir funciones mantenibles y puras, que logren nuestros
objetivos de negocio.
