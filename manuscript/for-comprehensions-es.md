# Comprensión for

La *comprensión for* de Scala es la abstracción ideal de la programación
funcional para los programas secuenciales que interactúan con el mundo. Dado que
la usaremos mucho, vamos a reaprender los principios de `for` y cómo Scalaz
puede ayudarnos a escribir código más claro.

Este capítulo no intenta enseñarnos a escribir programas puros y las técnicas
son aplicables a bases de código que no sean puramente funcionales.

## Conveniencia sintáctica

`for` en Scala es simplemente una regla de reescritura, llamada también una
conveniencia sintáctica (azúcar sintáctico) y no tiene ninguna información
contextual.

Para ver qué es lo que hace una `for` comprehension, usamos la característica
`show` y `reify` de la REPL (Read-Eval-Print-Loop) para mostrar cómo se ve el
código después de la inferencia de tipos.

```scala
  scala> import scala.reflect.runtime.universe._
  scala> val a, b, c = Option(1)
  scala> show { reify {
           for { i <- a ; j <- b ; k <- c } yield (i + j + k)
         } }

  res:
  $read.a.flatMap(
    ((i) => $read.b.flatMap(
      ((j) => $read.c.map(
        ((k) => i.$plus(j).$plus(k)))))))
```

Existe mucho "ruido" debido a las conveniencias sintácticas adicionales (por
ejemplo, `+` se reescribe `$plus`, etc.). No mostraremos el `show` y el `reify`
por brevedad cuando la línea del REPL se muestre como `reify`, y manualmente
limpiaremos el código generado de modo que no se vuelva una distracción.

```scala
  reify> for { i <- a ; j <- b ; k <- c } yield (i + j + k)

  a.flatMap {
    i => b.flatMap {
      j => c.map {
        k => i + j + k }}}
```

La regla de oro es que cada `<-` (llamada un *generador*) es una invocación
anidada de `flatMap`, siendo el último generador un `map` que contiene el cuerpo
del `yield`.

### Asignación

Podemos asignar valores al vuelo (*inline*) como `ij = i + j` (no es necesaria
la palabra reservada `val`).

```scala
  reify> for {
           i <- a
           j <- b
           ij = i + j
           k <- c
         } yield (ij + k)

  a.flatMap {
    i => b.map { j => (j, i + j) }.flatMap {
      case (j, ij) => c.map {
        k => ij + k }}}
```

Un `map` sobre la `b` introduce la `ij`, sobre la cual se llama un `flatMap`
junto con la `j`, y entonces se invoca a un `map` final sobre el código en el
`yield`.

Desgraciadamente no podemos realizar ninguna asignación antes de algún
generador. Esta característica ya ha sido solicitada para que sea soportada por
el lenguaje, pero la implementación no se ha llevado a cabo:
<https://github.com/scala/bug/issues/907>

```scala
  scala> for {
           initial = getDefault
           i <- a
         } yield initial + i
  <console>:1: error: '<-' expected but '=' found.
```

Podemos proporcionar una solución alternativa al definir un `val` fuera del
`for`

```scala
  scala> val initial = getDefault
  scala> for { i <- a } yield initial + i
```

o crear un `Option` a partir de la asignación inicial

```scala
  scala> for {
           initial <- Option(getDefault)
           i <- a
         } yield initial + i
```

A> `val` no tiene que asignar únicamente un valor, puede ser cualquier cosa que
A> funcione como un `case` en un empate de patrones.
A>
A> ```scala
A>   scala> val (first, second) = ("hello", "world")
A>   first: String = hello
A>   second: String = world
A>
A>   scala> val list: List[Int] = ...
A>   scala> val head :: tail = list
A>   head: Int = 1
A>   tail: List[Int] = List(2, 3)
A> ```
A>
A> Lo mismo es cierto en las asignaciones en las `for` comprehensions
A>
A> ```scala
A>   scala> val maybe = Option(("hello", "world"))
A>   scala> for {
A>            entry <- maybe
A>            (first, _) = entry
A>          } yield first
A>   res: Some(hello)
A> ```
A>
A> Pero, sea cuidadoso al no olvidar ningún caso o encontrará una excepción en
A> tiempo de ejecución (una falla de *totalidad*).
A>
A> ```scala
A>   scala> val a :: tail = list
A>   caught scala.MatchError: List()
A> ```

### Filter

Es posible poner sentencias `if` después de un generador para filtrar los
valores usando un predicado

```scala
  reify> for {
           i  <- a
           j  <- b
           if i > j
           k  <- c
         } yield (i + j + k)

  a.flatMap {
    i => b.withFilter {
      j => i > j }.flatMap {
        j => c.map {
          k => i + j + k }}}
```

Versiones anteriores de Scala usaban `filter`, pero `Traversable.filter` crea
nuevas colecciones para cada predicado, de modo que `withFilter` se introdujo
como una alternativa de mayor rendimiento. Podemos ocasionar accidentalmente la
invocación de `withFilter` al proporcionar información de tipo, interpretada
como un empate de patrones.

```scala
  reify> for { i: Int <- a } yield i

  a.withFilter {
    case i: Int => true
    case _      => false
  }.map { case i: Int => i }
```

Como la asignación, un generador puede usar un empate de patrones en el lado
izquierdo. Pero a diferencia de una asignación (que lanza un `MatchError` al
fallar) los generadores son `filtrados` y no fallarán en tiempo de ejecución.
Sin embargo, existe una aplicación ineficiente, doble, del patrón.

A> El plugin del compilador
A> [`better-monadic-for`](https://github.com/oleg-py/better-monadic-for) produce
A> **mejores** reescrituras que el compilador de Scala. Este ejemplo es
A> interpretado como:
A>
A> ```scala
A>   reify> for { i: Int <- a } yield i
A>
A>   a.map { (i: Int) => i}
A> ```
A>
A> en lugar del ineficiente doble empate de patrones (en el mejor de los casos)
A> y el filtrado silencioso en tiempo de ejecución (en el peor de los casos). Es
A> altamente recomendable.

### For Each

Finalmente, si no hay un `yield`, el compilador usará `foreach` en lugar de
`flatMap`, que es útil únicamente por los efectos colaterales.

```scala
  reify> for { i <- a ; j <- b } println(s"$i $j")

  a.foreach { i => b.foreach { j => println(s"$i $j") } }
```

### Resumen

El conjunto completo de métodos soportados por las `for` comprehensions no
comparten un super tipo común; cada fragmento generado es compilado de manera
independiente. Si hubiera un trait, se vería aproximadamente así:

```scala
  trait ForComprehensible[C[_]] {
    def map[A, B](f: A => B): C[B]
    def flatMap[A, B](f: A => C[B]): C[B]
    def withFilter[A](p: A => Boolean): C[A]
    def foreach[A](f: A => Unit): Unit
  }
```

Si el contexto (`C[_]`) de una `for` comprehension no proporciona su propio
`map` y `flatMap`, no todo está perdido. Si existe un valor implícito de
`scalaz.Bind[T]` para la `T` en cuestión, este proporcionará el `map` y el
`flatMap`.

A> Con fecuencia sorprende a los desarrolladores cuando cómputos dentro de una
A> `for` comprehension no se ejecutan en paralelo:
A>
A> ```scala
A>   import scala.concurrent._
A>   import ExecutionContext.Implicits.global
A>
A>   for {
A>     i <- Future { expensiveCalc() }
A>     j <- Future { anotherExpensiveCalc() }
A>   } yield (i + j)
A> ```
A>
A> Esto es porque el `flatMap` que produce `anotherExpensiveCalc` está
A> estrictamente después de `expensiveCalc`. Para asegurar que dos cálculos
A> `Future` empiecen en paralelo, inícielos fuera de la `for` comprehension.
A>
A> ```scala
A>   val a = Future { expensiveCalc() }
A>   val b = Future { anotherExpensiveCalc() }
A>   for { i <- a ; j <- b } yield (i + j)
A> ```
A>
A> Las `for` comprehensions son fundamentalmente para definir programas
A> secuenciales. Mostraremos una forma muy superior de definir cómputos
A> paralelos en un capítulo posterior. En breve: no use `Future`.

## El camino dificultoso

Hasta el momento sólo hemos observado las reglas de reescritura, no lo que está
sucediendo en el `map` y en el `flatMap`. Considere lo que sucede cuando el
contexto del `for` decide que no se puede proceder más.

En el ejemplo del `Option`, el `yield` se invoca únicamente cuando *todos* `i`,
`j`, `k` están definidos.

```scala
  for {
    i <- a
    j <- b
    k <- c
  } yield (i + j + k)
```

Si cualquiera de `a`, `b`, `c` es `None`, la comprehension se corto-circuita con
`None` pero no nos indica qué fue lo que salió mal.

A> Hay muchas funciones en código existente que reciben parámetros `Option` pero
A> que en realidad requieren que todos los parámetros existan. Una alternativa a
A> lanzar excepciones en tiempo de ejecución es usar una `for` comprehension,
A> dandonos totalidad (un valor devuelto por cada entrada):
A>
A> ```scala
A>   def namedThings(
A>     someName  : Option[String],
A>     someNumber: Option[Int]
A>   ): Option[String] = for {
A>     name   <- someName
A>     number <- someNumber
A>   } yield s"$number ${name}s"
A> ```
A>
A> pero esto es verboso, torpe, y considerado mal estilo. Si una función
A> requiere de cada entrada entonces debería hacer que este requerimiento sea
A> explícito, haciendo que sea responsabilidad del código que llama a la función
A> el lidiar con parámetros opcionales.
A>
A> ```scala
A>   def namedThings(name: String, num: Int) = s"$num ${name}s"
A> ```

Si usamos `Either`, entonces un `Left` ocasionará que la `for` comprehension se
corto circuite con información extra, mucho mejor que `Option` para reporte de
errores:

```scala
  scala> val a = Right(1)
  scala> val b = Right(2)
  scala> val c: Either[String, Int] = Left("sorry, no c")
  scala> for { i <- a ; j <- b ; k <- c } yield (i + j + k)

  Left(sorry, no c)
```

Y por último, veamos que sucede con un `Future` que falla:

```scala
  scala> import scala.concurrent._
  scala> import ExecutionContext.Implicits.global
  scala> for {
           i <- Future.failed[Int](new Throwable)
           j <- Future { println("hello") ; 1 }
         } yield (i + j)
  scala> Await.result(f, duration.Duration.Inf)
  caught java.lang.Throwable
```

El `Future` que imprime a la terminal nunca se invoca porque, como `Option` y
`Either`, la `for` comprehension se corto circuita.

El corto circuito para el camino difícil es un tema común e importante. Las
`for` comprehensions no pueden expresar la limpieza de recursos: no hay forma de
realizar un `try`/`finally`. Esto es bueno, en PF asigna claramente la
responsabilidad por la recuperación inesperada de errores y la limpieza de
recursos en el contexto (que normalmente es una `Monad` como veremos más tarde),
no la lógica del negocio.

## Gimnasia

Aunque es sencillo reescribir código secuencial simple como una `for`
comprehension, algunas veces desearemos hacer algo que parezca requerir hacer
volteretas mentales. Esta sección recolecta algunos ejemplos prácticos de cómo
lidiar con ellos.

### Lógica de respaldo

Digamos que estamos llamando a un método que regresa un `Option`. Si no es
exitoso deseamos ejecutar otro método como respaldo (y así consecutivamente),
como cuando estamos unando un cache:

```scala
  def getFromRedis(s: String): Option[String]
  def getFromSql(s: String): Option[String]

  getFromRedis(key) orElse getFromSql(key)
```

Si tenemos que hacer esto para una versión asíncrona de la misma API

```scala
  def getFromRedis(s: String): Future[Option[String]]
  def getFromSql(s: String): Future[Option[String]]
```

entonces tenemos que ser cuidadosos de no hacer trabajo extra porque

```scala
  for {
    cache <- getFromRedis(key)
    sql   <- getFromSql(key)
  } yield cache orElse sql
```

ejecutará ambas consultas. Podemos hacer empate de patrones en el primer
resultado pero el tipo es incorrecto

```scala
  for {
    cache <- getFromRedis(key)
    res   <- cache match {
               case Some(_) => cache !!! wrong type !!!
               case None    => getFromSql(key)
             }
  } yield res
```

Necesitamos crear un `Future` a partir del `cache`

```scala
  for {
    cache <- getFromRedis(key)
    res   <- cache match {
               case Some(_) => Future.successful(cache)
               case None    => getFromSql(key)
             }
  } yield res
```

`Future.successful` crea un nuevo `Future`, de manera muy similar a cómo un
`Option` o un constructor de `List`.

### Salida temprana

Digamos que tenemos alguna condición que implique la terminación temprana con un
valor exitoso.

Si deseamos terminar de manera temprana con un error, es práctica estándar en
programación orientada a objectos lanzar una excepción

```scala
  def getA: Int = ...

  val a = getA
  require(a > 0, s"$a must be positive")
  a * 10
```

que puede reescribirse de manera asíncrona

```scala
  def getA: Future[Int] = ...
  def error(msg: String): Future[Nothing] =
    Future.failed(new RuntimeException(msg))

  for {
    a <- getA
    b <- if (a <= 0) error(s"$a must be positive")
         else Future.successful(a)
  } yield b * 10
```

Pero si deseamos terminar temprano con un valor de retorno exitoso, el código
síncrono simple:

```scala
  def getB: Int = ...

  val a = getA
  if (a <= 0) 0
  else a * getB
```

se traduce a `for` comprehension anidados cuando nuestras dependencias son
asíncronas:

```scala
  def getB: Future[Int] = ...

  for {
    a <- getA
    c <- if (a <= 0) Future.successful(0)
         else for { b <- getB } yield a * b
  } yield c
```

A> Si existe un valor implícito `Monad[T]` para `T[_]` (es decir, `T` es
A> monádica) entonces Scalaz nos permite crear una `T[A]` a partir de un valor
A> `a: A` al invocar `a.pure[T]`.
A>
A> Scalaz proporciona `Monad[Future]`, y `.pure[Future]` incova a
A> `Future.successful`. Además de que `pure` es ligeramente más breve de
A> teclear, es un concepto general que funciona más allá de `Future` y por lo
A> tanto es recomendado.
A>
A> ```scala
A>   for {
A>     a <- getA
A>     c <- if (a <= 0) 0.pure[Future]
A>          else for { b <- getB } yield a * b
A>   } yield c
A> ```

## Sin posibilidad de usar `for` comprehension

El contexto que sobre el que estamos haciendo la `for` comprehension debe ser el
mismo: no es posible mezclar contextos.

```scala
  scala> def option: Option[Int] = ...
  scala> def future: Future[Int] = ...
  scala> for {
           a <- option
           b <- future
         } yield a * b
  <console>:23: error: type mismatch;
   found   : Future[Int]
   required: Option[?]
           b <- future
                ^
```

Nada puede ayudarnos a mezclar contextos arbitrarios en una `for` comprehension
porque el significado no está bien definido.

Cuando tenemos contextos anidados la intención es normalmente obvia, pero sin
embargo, el compilador no aceptará nuestro código.

```scala
  scala> def getA: Future[Option[Int]] = ...
  scala> def getB: Future[Option[Int]] = ...
  scala> for {
           a <- getA
           b <- getB
         } yield a * b
                   ^
  <console>:30: error: value * is not a member of Option[Int]
```

Aquí deseamos que `for` se ocupe del contexto externo y nos deje escribir
nuestro código en el `Option` interno. Esconder el contexto externo es
exactamente lo que hace un *transformador de mónadas*, y Scalaz proporciona
implementaciones para `Option` y `Either` llamadas `OptionT` y `EitherT`
respectivamente.

El contexto externo puede ser cualquier cosa que normalmente funcione en una
`for` comprehension, pero necesita ser el mismo a través de toda la
comprehension.

Aquí creamos un `OptionT` por cada invocación de método. Esto cambia el contexto
del `for` de un `Future[Option[_]]` a `OptionT[Future, _]`.

```scala
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
         } yield a * b
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
```

`.run` nos devuelve el contexto original.

```scala
  scala> result.run
  res: Future[Option[Int]] = Future(<not completed>)
```

El transformador de mónadas también nos permite mezclar invocaciones a
`Future[Option[_]]` con métodos que simplemente devuelven `Future` mediante el
uso de `.liftM[OptionT]` (proporcionado por Scalaz):

```scala
  scala> def getC: Future[Int] = ...
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
           c <- getC.liftM[OptionT]
         } yield a * b / c
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
```

y así podemos mezclar métodos que devuelven un `Option` simple al envolverlos en
un `Future.successful` (`.pure[Future]`) seguido de un `OptionT`

```scala
  scala> def getD: Option[Int] = ...
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
           c <- getC.liftM[OptionT]
           d <- OptionT(getD.pure[Future])
         } yield (a * b) / (c * d)
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
```

De nuevo, el código es algo enmarañado, pero es mejor que escribir `flatMap` y
`map` anidados manualmente. Podríamos limpiarlo un poco con un *Domain Specific
Language* (DSL) que se encargue de todas las conversiones a `OptionT[Future, _]`
que sean necesarias

```scala
  def liftFutureOption[A](f: Future[Option[A]]) = OptionT(f)
  def liftFuture[A](f: Future[A]) = f.liftM[OptionT]
  def liftOption[A](o: Option[A]) = OptionT(o.pure[Future])
  def lift[A](a: A)               = liftOption(Option(a))
```

combinados con el operador `|>`, que aplica la función de la derecha al valor a
la izquierda, para separar visualmente la lógica de los transformadores

```scala
  scala> val result = for {
           a <- getA       |> liftFutureOption
           b <- getB       |> liftFutureOption
           c <- getC       |> liftFuture
           d <- getD       |> liftOption
           e <- 10         |> lift
         } yield e * (a * b) / (c * d)
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
```

A> `|>` es con frecuencia conocido como el operador *thrush* (tordo) debido a su
A> peculiar parecido a la linda ave. Aquellos a los que no les agraden los
A> operadores simbólicos pueden usar el alias `.into`.

Este enfoque también funciona para `EitherT` (y otros) como el contexto interno,
pero sus métodos para hacer *lifting* som más complejos y requieren parámetros.
Scalaz proporciona transformadores de mónadas para muchos de sus propios tipos,
de modo que vale la pena verificar si hay alguno disponible.

<!--  LocalWords:  Scalaz
 -->
