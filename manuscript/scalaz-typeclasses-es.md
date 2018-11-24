# Scalaz Typeclasses

En este capítulo tendremos un tour de la mayoría de las typeclasses en `scalaz-core`. No usamos
todas en `drone-dynamic-agents` de modo que daremos ejemplos standalone cuando sea apropiado.

Ha habido críticas con respecto a los nombres usados en Scalaz, y en la programación funcional en
general. La mayoría de los nombres siguen las convenciones introducidas en el lenguaje de
programación funcional Haskell, basándose en la *Teoría de las Categorías*. Siéntase libre de crear
`type` aliases si los verbos basados en la funcionalidad primaria son más fáciles de recordar cuando
esté aprendiendo (por ejemplo, `Mappable`, `Pureable`, `FlatMappable`).

Antes de introducir la jerarquía de typeclasses, echaremos un vistazo a los cuatro métodos más
importantes desde una perspectiva de control de flujo: los métodos que usaremos más en las
aplicaciones típicas de PF:

| Typeclass     | Method     | Desde     | Dado        | Hacia     |
| ------------- | ---------- | --------- | ----------- | --------- |
| `Functor`     | `map`      | `F[A]`    | `A => B`    | `F[B]`    |
| `Applicative` | `pure`     | `A`       |             | `F[A]`    |
| `Monad`       | `flatMap`  | `F[A]`    | `A => F[B]` | `F[B]`    |
| `Traverse`    | `sequence` | `F[G[A]]` |             | `G[F[A]]` |

Sabemos que las operaciones que regresan un `F[_]` pueden ejecutarse secuencialmente en una `for`
comprehension mediante `.flatMap` definida en su `Monad[F]`. El contexto `F[_]` puede considerarse
como un contenedor para un *efecto* intencional que tiene una `A` como la salida: `flatMap` nos
permite generar nuevos efectos `F[B]` en tiempo de ejecución basándose en los resultados de evaluar
efectos previos.

Por supuesto, no todos los constructores de tipo `F[_]` tienen efectos, incluso si tienen una
`Monad[_]`. Con frecuencia son estructuras de datos. Mediante el uso de la abstracción menos
específica, podemos reusar código para `List`, `Either`, `Future` y más.

Si únicamente necesitamos transformar la salida de un `F[_]`, esto simplemente es un `map`,
introducido por `Functor`. En el capítulo 3, ejecutamos efectos en paralelo mediante la creación de
un producto y realizando un mapeo sobre ellos. En programación funcional, los cómputos
paralelizables son considerados **menos** poderosos que los secuenciales.

Entre `Monad` y `Functor` se encuentra `Applicative`, que define `pure` que nos permite alzar un
valor en un efecto, o la creación de una estructura de datos a partir de un único valor.

`.sequence` es útil para rearreglar constructores de tipo. Si tenemos un `F[G[_]]` pero requerimos
un `G[F[_]]`, por ejemplo, `List[Future[Int]]` pero requerimos un `Future[List[_]]`, entonces
ocupamos `.sequence`.

## Agenda

Este capítulo es más largo de lo usual y está repleto de información: es perfectamente razonable
abordarlo en varias sesiones de estudio. Recordar todo requeriría poderes sobrehumanos, de modo que
trate este capítulo como una manera de buscar más información.

Es notable la ausencia de typeclasses que extienden `Monad`. Estas tendrán su propio capítulo más
adelante.

Scalaz usa generación de código, no simulacrum. Sin embargo, por brevedad, presentaremos los
fragmentos de código con `@typeclass`. La sintaxis equiovalente estará disponible cuando hagamos un
`import scalaz._, Scalaz._` y estará disponible en el paquete `scalaz.syntax` en el código fuente de
scalaz.

{width=100%}
![](images/scalaz-core-tree.png)

{width=60%}
![](images/scalaz-core-cliques.png)

{width=60%}
![](images/scalaz-core-loners.png)

## Cosas que pueden agregarse

{width=25%}
![](images/scalaz-semigroup.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Semigroup[A] {
    @op("|+|") def append(x: A, y: =>A): A
  
    def multiply1(value: F, n: Int): F = ...
  }
  
  @typeclass trait Monoid[A] extends Semigroup[A] {
    def zero: A
  
    def multiply(value: F, n: Int): F =
      if (n <= 0) zero else multiply1(value, n - 1)
  }
  
  @typeclass trait Band[A] extends Semigroup[A]
~~~~~~~~

A> `|+|` es conocido como el operador TIE Fighter. Existe un operador Avanzado TIE Fighter en una
A> sección más adelante, lo cual es muy excitante.

Un `Semigroup` puede definirse para un tipo si dos valores pueden combinarse. El operador debe ser
*asociativo*, es decir, que el orden de las operaciones anidadas no debería importar, es decir

{lang="text"}
~~~~~~~~
  (a |+| b) |+| c == a |+| (b |+| c)
  
  (1 |+| 2) |+| 3 == 1 |+| (2 |+| 3)
~~~~~~~~

Un `Monoid` es un `Semigroup` con un elemento *zero* (también llamado *empty* --vacío-- o *identity*
--identidad--). Combinar `zero` con cualquier otra `a` debería dar otra `a` .

{lang="text"}
~~~~~~~~
  a |+| zero == a
  
  a |+| 0 == a
~~~~~~~~

Esto probablemente le traiga memorias sobre `Numeric` del capítulo 4. Existen implementaciones de
`Monoid` para todos los tipos numéricos primitivos, pero el concepto de cosas que se pueden
*agregar* es útil más allá de los números.

{lang="text"}
~~~~~~~~
  scala> "hello" |+| " " |+| "world!"
  res: String = "hello world!"
  
  scala> List(1, 2) |+| List(3, 4)
  res: List[Int] = List(1, 2, 3, 4)
~~~~~~~~

`Band` tiene la ley de que la operación `append` de dos elementos iguales es *idempotente*, es decir
devuelve el mismo valor. Ejemplos de esto pueden ser cualesquier cosa que sólo pueda tener un valor,
tales como `Unit`, los límites superiores más pequeños, o un `Set` (conjunto). `Band` no proporciona
métodos adicionales y sin embargo los usuarios pueden aprovechar las garantías que brinda con fines
de optimización del rendimiento.

A> Viktor Klang, de Lightbend, dice haber acuñado la frase
A> [entrega efectivamente una vez](https://twitter.com/viktorklang/status/789036133434978304)
A> para el proceso de mensajes con operaciones idempotentes, es decir, `Band.append`.

Como un ejemplo realista de `Monoid`, considere un sistema de comercio que tenga una base de datos
grande de plantillas de transacciones comerciales reusables. Llenar las plantillas por default para
una nueva transacción implica la selección y combinación de múltiples plantillas, con la regla del
"último gana" para realizar uniones si dos plantillas proporcionan un valor para el mismo campo. El
trabajo de "seleccionar" trabajo ya se realiza por nosotros mediante otro sistema, es nuestro
trabajo combinar las plantillas en orden.

Crearemos un esquema simple de plantillas para demostrar el principio, pero tenga en mente que un
sistema realista tendría un ADT más complicado.

{lang="text"}
~~~~~~~~
  sealed abstract class Currency
  case object EUR extends Currency
  case object USD extends Currency
  
  final case class TradeTemplate(
    payments: List[java.time.LocalDate],
    ccy: Option[Currency],
    otc: Option[Boolean]
  )
~~~~~~~~

Si escribimos un método que tome `templates: List[TradeTemplate]`, entonces necesitaremos llamar
únicamente

{lang="text"}
~~~~~~~~
  val zero = Monoid[TradeTemplate].zero
  templates.foldLeft(zero)(_ |+| _)
~~~~~~~~

¡y nuestro trabajo está hecho!

Pero para poder usar `zero` o invocar `|+|` debemos tener una instancia de `Monoid[TradeTemplate]`.
Aunque derivaremos genéricamente este en un capítulo posterior, por ahora crearemos la instancia en
el companion:

{lang="text"}
~~~~~~~~
  object TradeTemplate {
    implicit val monoid: Monoid[TradeTemplate] = Monoid.instance(
      (a, b) => TradeTemplate(a.payments |+| b.payments,
                              a.ccy |+| b.ccy,
                              a.otc |+| b.otc),
      TradeTemplate(Nil, None, None)
    )
  }
~~~~~~~~

Sin embargo, esto no hace lo que queremos porque `Monoid[Option[A]]` realizará una agregación de su
contenido, por ejemplo,

{lang="text"}
~~~~~~~~
  scala> Option(2) |+| None
  res: Option[Int] = Some(2)
  scala> Option(2) |+| Option(1)
  res: Option[Int] = Some(3)
~~~~~~~~

mientras que deseamos implementar la regla del "último gana". Podríamos hacer un override del valor
default `Monoid[Option[A]]` con el nuestro propio:

{lang="text"}
~~~~~~~~
  implicit def lastWins[A]: Monoid[Option[A]] = Monoid.instance(
    {
      case (None, None)   => None
      case (only, None)   => only
      case (None, only)   => only
      case (_   , winner) => winner
    },
    None
  )
~~~~~~~~

Ahora todo compila, de modo que si lo intentamos...

{lang="text"}
~~~~~~~~
  scala> import java.time.{LocalDate => LD}
  scala> val templates = List(
           TradeTemplate(Nil,                     None,      None),
           TradeTemplate(Nil,                     Some(EUR), None),
           TradeTemplate(List(LD.of(2017, 8, 5)), Some(USD), None),
           TradeTemplate(List(LD.of(2017, 9, 5)), None,      Some(true)),
           TradeTemplate(Nil,                     None,      Some(false))
         )
  
  scala> templates.foldLeft(zero)(_ |+| _)
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

Todo lo que tuvimos que hacer fue implementar una pieza de lógica de negocios y, !el `Monoid` se
encargó de todo por nosotros!

Note que la lista de `payments` se concatenó. Esto es debido a que el `Monoid[List]` por default usa
concatenación de elementos y simplemente ocurre que este es el comportamiento deseado aquí. Si el
requerimiento de negocios fuera distinto, la solución sería proporcionar un
`Monoid[List[LocalDate]]` personalizado. Recuerde del capítulo 4 que con el polimorfismo de tiempo
de compilación tenemos una implementacion distinta de `append` dependiendo de la `E` en `List[E]`,
no sólo de la clase de tiempo de ejecución `List`.

A> Cuando introdujimos las typeclasses en el capítulo 4 dijimos que sólo podía haber una
A> implementación de una typeclass para un tipo de parámetro dado, es decir, sólo existe un único
A> `Monoid[Option[Boolean]]` en la aplicación. Las *instancias huérfanas* tales como *lastWins* son
A> aquellas que con más facilidad rompen la coherencia.
A>
A> Podríamos intentar justificar el rompimiento de la coherencia de typeclasses al hacer `lastWins`
A> privado, pero cuando lleguemos a la typeclass `Plus` veremos una mejor manera de implementar
A> nuestro `Monoid`. Cuando lleguemos a los tipos marcados (*tagged*), veremos una forma incluso
A> mejor: usando `LastOption` en lugar de `Option` en nuestro modelo de datos.
A>
A> Por favor niños, ¡no rompan la coherencia de typeclasses en casa!

## Cosas parecidas a objetos

En el capítulo sobre Datos y Funcionalidad dijimos que la noción de la JVM de igualdad se derrumba
para muchas cosas que podemos poner en una ADT. El problema es que la JVM fue diseñada para Java, y
`equals` está definido sobre `java.lang.Object` aunque esto tenga sentido o no. No existe manera de
remover `equals` y no hay forma de garantizar que esté implementado.

Sin embargo, en PF preferimos el uso de typeclasses para tener funcionalidad polimórfica e incluso
el concepto de igualdad es capturado en tiempo de compilación.

{width=20%}
![](images/scalaz-comparable.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Equal[F]  {
    @op("===") def equal(a1: F, a2: F): Boolean
    @op("/==") def notEqual(a1: F, a2: F): Boolean = !equal(a1, a2)
  }
~~~~~~~~

En verdad `===` (*triple igual*) es más seguro desde la perspectiva de tipos que `==`(*doble igual*)
porque únicamente puede compilarse cuando los tipos son los mismos en ambos lados de la comparación.
Esto atrapa muchos errores.

`equal` tiene los mismos requisitos de implementación que `Object.equals`

- *conmutativo* `f1 === f2` implica `f2 === f1`
- *reflexivo* `f === f`
- *transitivo* `f1 === f2 && f2 === f3` implica que `f1 === f3`

Al desechar el concepto universal de `Object.equals` no damos por sentado el concepto de igualdad
cuando construimos un ADT, y nos detiene en tiempo de compilación de esperar igualdad cuando en
realidad no existe tal.

Continuando con la tendencia de reemplazar conceptos viejos de Java, más bien que considerar que los
datos *son* un `java.lang.Comparable`, ahora *tienen* un `Order` de acuerdo con

{lang="text"}
~~~~~~~~
  @typeclass trait Order[F] extends Equal[F] {
    @op("?|?") def order(x: F, y: F): Ordering
  
    override  def equal(x: F, y: F): Boolean = order(x, y) == Ordering.EQ
    @op("<" ) def lt(x: F, y: F): Boolean = ...
    @op("<=") def lte(x: F, y: F): Boolean = ...
    @op(">" ) def gt(x: F, y: F): Boolean = ...
    @op(">=") def gte(x: F, y: F): Boolean = ...
  
    def max(x: F, y: F): F = ...
    def min(x: F, y: F): F = ...
    def sort(x: F, y: F): (F, F) = ...
  }
  
  sealed abstract class Ordering
  object Ordering {
    case object LT extends Ordering
    case object EQ extends Ordering
    case object GT extends Ordering
  }
~~~~~~~~

`Order` implementa `.equal` en términos de la primitiva nueva `.order`. Cuando una typeclass
implementa el combinador primitivo de su padre con un *combinador derivado*, se agrega una
**ley implicación de sustitución** para el typeclass. Si una instancia de `Order` fuera a hacer un
override de `.equal` por razones de desempeño, debería comportase de manera idéntica a la original.

Las cosas que tienen un orden también podrían ser discretas, permitiéndonos avanzar o retroceder
hacia un sucesor o predecesor:

{lang="text"}
~~~~~~~~
  @typeclass trait Enum[F] extends Order[F] {
    def succ(a: F): F
    def pred(a: F): F
    def min: Option[F]
    def max: Option[F]
  
    @op("-+-") def succn(n: Int, a: F): F = ...
    @op("---") def predn(n: Int, a: F): F = ...
  
    @op("|->" ) def fromToL(from: F, to: F): List[F] = ...
    @op("|-->") def fromStepToL(from: F, step: Int, to: F): List[F] = ...
    @op("|=>" ) def fromTo(from: F, to: F): EphemeralStream[F] = ...
    @op("|==>") def fromStepTo(from: F, step: Int, to: F): EphemeralStream[F] = ...
  }
~~~~~~~~

{lang="text"}
~~~~~~~~
  scala> 10 |--> (2, 20)
  res: List[Int] = List(10, 12, 14, 16, 18, 20)
  
  scala> 'm' |-> 'u'
  res: List[Char] = List(m, n, o, p, q, r, s, t, u)
~~~~~~~~

A> `\-->` es el sable de luz de Scalaz. Esta es la sintaxis de un programador funcional. No es tan
A> torpe o aleatoria como `fromStepToL`. Una sintaxis elegante... para una era más civilizada.

Discutiremos `EphemeralStream` en el siguiente capítulo, por ahora sólo necesitamos saber que se
trata de una estructura de datos potencialmente infinita que evita los problemas de retención de
memoria en el tipo `Stream` de la librería estándar.

De manera similar a `Object.equals`, el concepto de `.toString` en toda clases no tiene sentido en
Java. Nos gustaría hacer cumplir el concepto de "poder representar como cadena" en tiempo de
compilación y esto es exactamente lo que se consigue con `Show`:

{lang="text"}
~~~~~~~~
  trait Show[F] {
    def show(f: F): Cord = ...
    def shows(f: F): String = ...
  }
~~~~~~~~

Exploraremos `Cord` con más detalle en el capítulo que trate los tipos de datos, por ahora sólo
necesitamos saber que es una estructura de datos eficiente para el almacenamiento y manipulación de
`String`.

## Cosas que se pueden mapear o transformar

Ahora nos enfocamos en las cosas que pueden mapearse, o recorrerse, en cierto sentido:

{width=100%}
![](images/scalaz-mappable.png)

### Functor

{lang="text"}
~~~~~~~~
  @typeclass trait Functor[F[_]] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
  
    def void[A](fa: F[A]): F[Unit] = map(fa)(_ => ())
    def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)] = map(fa)(a => (a, f(a)))
  
    def fpair[A](fa: F[A]): F[(A, A)] = map(fa)(a => (a, a))
    def strengthL[A, B](a: A, f: F[B]): F[(A, B)] = map(f)(b => (a, b))
    def strengthR[A, B](f: F[A], b: B): F[(A, B)] = map(f)(a => (a, b))
  
    def lift[A, B](f: A => B): F[A] => F[B] = map(_)(f)
    def mapply[A, B](a: A)(f: F[A => B]): F[B] = map(f)((ff: A => B) => ff(a))
  }
~~~~~~~~

El único método abstracto es `map`, y debe ser posible hacer una *composición*, es decir, mapear con
una `f` y entonces nuevamente con una `g` es lo mismo que hacer un mapeo una única vez con la
composición de `f` y `g`:

{lang="text"}
~~~~~~~~
  fa.map(f).map(g) == fa.map(f.andThen(g))
~~~~~~~~

El `map` también debe realizar una operación nula si la función provista es la identidad (es decir,
`x => x`)

{lang="text"}
~~~~~~~~
  fa.map(identity) == fa
  
  fa.map(x => x) == fa
~~~~~~~~

`Functor` define algunos métodos convenientes alrededor de `map` que pueden optimizarse para algunas
instancias específicas. La documentación ha sido intencionalmente omitida en las definiciones arriba
para incentivar el análisis de lo que hace un método antes de que vea la implementación. Por favor,
deténgase unos momentos estudiando únicamente las signaturas de tipo de los siguientes métodos antes
de avanzar más:

{lang="text"}
~~~~~~~~
  def void[A](fa: F[A]): F[Unit]
  def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)]
  
  def fpair[A](fa: F[A]): F[(A, A)]
  def strengthL[A, B](a: A, f: F[B]): F[(A, B)]
  def strengthR[A, B](f: F[A], b: B): F[(A, B)]
  
  // harder
  def lift[A, B](f: A => B): F[A] => F[B]
  def mapply[A, B](a: A)(f: F[A => B]): F[B]
~~~~~~~~

1. `void` toma una instancia de `F[A]` y siempre devuelve un `F[Unit]`, y se olvida de todos los
   valores a la vez que preserva la estructura.
2. `fproduct` toma la misma entrada que `map` pero devuelve `F[(A, B)]`, es decir, devuelve el
   contenido dentro de una tupla, con el resultado obtenido al aplicar la función. Esto es útil
   cuando deseamos retener la entrada.
3. `fpair` repite todos los elementos de `A` en una tupla `F[(A, A)]`
4. `strengthL` empareja el contenido de una `F[B]` con una constante `A` a la izquierda.
5. `strenghtR` empareja el contenido de una `F[A]` con una constante `B` a la derecha.
6. `lift` toma una función `A => B` y devuelve una `F[A] => F[B]`. En otras palabras, toma una
   función del contenido de una `F[A]` y devuelve una función que opera **en** el `F[A]`
   directamente.
7. `mapply` nos obliga a pensar un poco. Digamos que tenemos una `F[_]` de funciones `A => B` y el
   valor `A`, entonces podemos obtener un `F[B]`. Tiene una firma/signatura similar a la de `pure`
   pero requiere que el que hace la llamada proporcione `F[A => B]`.

`fpair`, `strenghL` y `strengthR` tal vez parezcan inútiles, pero mostrarán su utilidad cuando
deseemos retener algo de información que de otra manera se perdería en el ámbito.

`Functor` tiene una sintaxis especial:

{lang="text"}
~~~~~~~~
  implicit class FunctorOps[F[_]: Functor, A](self: F[A]) {
    def as[B](b: =>B): F[B] = Functor[F].map(self)(_ => b)
    def >|[B](b: =>B): F[B] = as(b)
  }
~~~~~~~~

`.as` y `>|` es una forma de reemplazar la salida con una constante.

A> Cuando Scalaz proporciona funcionalidad adicional como sintaxis, más bien que en la typeclass en
A> sí misma, es debido a la compatibilidad binaria.
A>
A> Cuando una versión `X.Y.0` de Scalaz se publica, no es posible añadir métodos a la typeclass en
A> esa serie de publicaciones para Scala 2.10 y 2.11. Por lo tanto, es de mucho valor leer tanto el
A> código fuente de la typeclass y su sintaxis.

En nuestra aplicación de ejemplo, como un hack espantoso (que no admitimos hasta ahora), definimos
`start` y `stop` de modo que devolvieran su entrada:

{lang="text"}
~~~~~~~~
  def start(node: MachineNode): F[MachineNode]
  def stop (node: MachineNode): F[MachineNode]
~~~~~~~~

Esto nos permitió escribir lógica breve de negocios como

{lang="text"}
~~~~~~~~
  for {
    _      <- m.start(node)
    update = world.copy(pending = Map(node -> world.time))
  } yield update
~~~~~~~~

y

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(m.stop)
    updates = stopped.map(_ -> world.time).toList.toMap
    update  = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~

pero este hack nos obliga a poner complejidad innecesaria en las implementaciones. Es mejor si
dejamos que nuestras álgebras regresen `F[Unit]` y usar `as`:

{lang="text"}
~~~~~~~~
  m.start(node) as world.copy(pending = Map(node -> world.time))
~~~~~~~~

y

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(a => m.stop(a) as a)
    updates = stopped.map(_ -> world.time).toList.toMap
    update  = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~

### Foldable

Técnicamente, `Foldable` es para estructuras de datos que pueden recorrerse y producir un valor que
las resuma. Sin embargo, esto no dice lo suficiente sobre el hecho de que se trata de un arma
poderosa proporcionada por las typeclasses que nos puede proporcionar la mayoría de lo que esperamos
ver en una API de colecciones.

Hay tantos métodos que necesitaremos revisarlos en partes, empezando con los métodos abstractos:

{lang="text"}
~~~~~~~~
  @typeclass trait Foldable[F[_]] {
    def foldMap[A, B: Monoid](fa: F[A])(f: A => B): B
    def foldRight[A, B](fa: F[A], z: =>B)(f: (A, =>B) => B): B
    def foldLeft[A, B](fa: F[A], z: B)(f: (B, A) => B): B = ...
~~~~~~~~

Una instancia de `Foldable` necesita implementar únicamente `foldMap` y `foldRight` para obtener
toda la funcionalidad en esta typeclass, aunque los métodos están típicamente optimizqados para
estructuras de datos específicas.

`.foldMap` tiene un nombre usado en mercadotecnia: **MapReduce**. Dada una `F[A]`, una función de
`A` a `B`, y una forma de combinar una `B` (proporcionada por el `Monoid`, junto con el zero `B`),
podemos producir el valor resumen de tipo `B`. No existe un orden impuesto en las operaciones,
permitiendonos realizar cómputos paralelos.

`foldRight` no requiere que sus parámetros tengan un `Monoid`, significando esto que necesita un
valor inicial `z` y una manera de combinar cada elemento de la estructura de datos con el valor
resumen. El orden en el que se recorren los elementos es de derecha a izquierda y es por esta razón
que no puede paralelizarse.

A> `foldRight` es conceptualmente la misma operación que `foldRight` en la librería estándar de
A> Scala. Sin embargo, existe un problema con la firma de la versión de la librería estándar para
A> `foldRight`, que está resuelto en Scalaz: estructuras de datos muy grandes pueden ocasionar un
A> stack overflow. `List.foldRight` hace trampa mediante implementar `foldRight` como un `foldLeft`
A> invertido.
A>
A> {lang="text"}
A> ~~~~~~~~
A>   override def foldRight[B](z: B)(op: (A, B) => B): B =
A>     reverse.foldLeft(z)((right, left) => op(left, right))
A> ~~~~~~~~
A> 
A> pero el concepto de invertir no es universal y este truco no puede usarse para todas las
A> estructuras de datos. Por ejemplo, digamos que deseamos encontrar un número pequeño en un
A> `Stream`, con salida temprana:
A>
A> {lang="text"}
A> ~~~~~~~~
A>   scala> def isSmall(i: Int): Boolean = i < 10
A>   scala> (1 until 100000).toStream.foldRight(false) {
A>            (el, acc) => isSmall(el) || acc
A>          }
A>   java.lang.StackOverflowError
A>     at scala.collection.Iterator.toStream(Iterator.scala:1403)
A>     ...
A> ~~~~~~~~
A> Scalaz resuelve el problema mediante tomar un parámetro *byname* para el valor agregado
A>
A> {lang="text"}
A> ~~~~~~~~
A>   scala> (1 |=> 100000).foldRight(false)(el => acc => isSmall(el) || acc )
A>   res: Boolean = true
A> ~~~~~~~~
A>
A> lo que significa que el `acc` no es evaluado a menos que se necesite.
A>
A> Es digno de mención que no todas las operaciones son seguras para el stack en `foldRight`. Si
A> se requiriera de evaluar todos los elementos también podríamos obtener un `StackOverflow` con
A> un `EphemeralStream` de Scalaz
A>
A> A> {lang="text"}
A> ~~~~~~~~
A>   scala> (1L |=> 100000L).foldRight(0L)(el => acc => el |+| acc )
A>   java.lang.StackOverflowError
A>     at scalaz.Foldable.$anonfun$foldr$1(Foldable.scala:100)
A>     ...
A> ~~~~~~~~

`foldLeft` recorre los elementos de izquierda a derecha. `foldLeft` puede implementarse en términos
de `foldMap`, pero la mayoría de las instancias escogen implementarlas porque se trata de una
operación básica. Dado que normalmente se implementa con recursión de cola, no existen parámetros
*byname*.

La única ley para `Foldable` es que `foldLeft` y `foldRight` deberían ser consistentes con `foldMap`
para operaciones monoidales, por ejemplo, agregando un elemento a una lista para `foldLeft` y
anteponiendo un elemento a la lista para `foldRight`. Sin embargo, `foldLeft` y `foldRight` no
necesitan ser consistentes la una con la otra: de hecho con frecuencia producen el inverso que
produce el otro.

La cosa más sencilla que se puede hacer con `foldMap` es usar la función `identity` (identidad),
proporcionando `fold` (la suma natural de los elementos monoidales), con variantes derecha/izquierda
para permitirnos escoger basándonos en criterios de rendimiento:

{lang="text"}
~~~~~~~~
  def fold[A: Monoid](t: F[A]): A = ...
  def sumr[A: Monoid](fa: F[A]): A = ...
  def suml[A: Monoid](fa: F[A]): A = ...
~~~~~~~~

Recuerde que cuando aprendimos sobre `Monoid`, escribimos lo siguiente:

{lang="text"}
~~~~~~~~
  scala> templates.foldLeft(Monoid[TradeTemplate].zero)(_ |+| _)
~~~~~~~~

Sabemos que esto es tonto y que pudimos escribir:

{lang="text"}
~~~~~~~~
  scala> templates.toIList.fold
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

`.fold` no funciona en la `List` estándar debido a que ya tiene un método llamado `fold` que hace
su propia cosa a su manera especial.

El método llamado de manera extraña `intercalate` inserta una `A` específica entre cada elemento
antes de realizar el `fold`

{lang="text"}
~~~~~~~~
  def intercalate[A: Monoid](fa: F[A], a: A): A = ...
~~~~~~~~

que es una versión generalizada del método de la librería estándar `mkString`:

{lang="text"}
~~~~~~~~
  scala> List("foo", "bar").intercalate(",")
  res: String = "foo,bar"
~~~~~~~~

El `foldLeft` proporciona el medio para obtener cualquier elemento mediante un índice para recorrer
la estructura, incluyendo un grupo grande de métodos relacionados:

{lang="text"}
~~~~~~~~
  def index[A](fa: F[A], i: Int): Option[A] = ...
  def indexOr[A](fa: F[A], default: =>A, i: Int): A = ...
  def length[A](fa: F[A]): Int = ...
  def count[A](fa: F[A]): Int = length(fa)
  def empty[A](fa: F[A]): Boolean = ...
  def element[A: Equal](fa: F[A], a: A): Boolean = ...
~~~~~~~~

Scalaz es una librería funcional pura que tiene únicamente *funciones totales*. Mientras que
`List(0)` puede lanzar excepciones, `Foldable.index` devuelve una `Option[A]` con el método
conveniente `.indexOr` regreseando una `A` cuando se proporciona un valor por default. `.element` es
similar al método de la librería estándar `.contains` pero usa `Equal` más bien que la mal definida
igualdad de la JVM.

Estos métods *realmente* suenan como una API de colecciones. Y, por supuesto, toda cosa con una
instancia de `Foldable` puede convertirse en una `List`

{lang="text"}
~~~~~~~~
  def toList[A](fa: F[A]): List[A] = ...
~~~~~~~~

También existen conversiones a otros tipos de datos de la librería estándar de Scala y de Scalaz,
tales como `.toSet`, `.toVector`, `.toStream`, `.to[T <: TraversableLike]`, `toIList` y la lista
continúa.

Existen verificadores de predicados útiles

{lang="text"}
~~~~~~~~
  def filterLength[A](fa: F[A])(f: A => Boolean): Int = ...
  def all[A](fa: F[A])(p: A => Boolean): Boolean = ...
  def any[A](fa: F[A])(p: A => Boolean): Boolean = ...
~~~~~~~~

`filterLenght` es una forma de contar cuántos elementos son `true` para el predicado, `all` y `any`\
devuelven `true` is todos (o algún) elemento cumple con el predicado, y pueden terminar de manera
temprana.

A> Ya hemos visto la `NonEmptyList` en capítulos anteriores. Con el propósito de ser breves usaremos
A> el type alias `Nel` en lugar de `NonEmptyList`.
A>
A> También hemos visto `IList` en capítulos anteriores, recuerde que se trata de una alternativa a
A> la `List` de la librería estándar cuyos métodos impuros, como `apply`, han sido removidos.

Podemos dividir en partes una `F[A]` que resulten en la misma `B` con `splitBy`

{lang="text"}
~~~~~~~~
  def splitBy[A, B: Equal](fa: F[A])(f: A => B): IList[(B, Nel[A])] = ...
  def splitByRelation[A](fa: F[A])(r: (A, A) => Boolean): IList[Nel[A]] = ...
  def splitWith[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  def selectSplit[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  
  def findLeft[A](fa: F[A])(f: A => Boolean): Option[A] = ...
  def findRight[A](fa: F[A])(f: A => Boolean): Option[A] = ...
~~~~~~~~

por ejemplo

{lang="text"}
~~~~~~~~
  scala> IList("foo", "bar", "bar", "faz", "gaz", "baz").splitBy(_.charAt(0))
  res = [(f, [foo]), (b, [bar, bar]), (f, [faz]), (g, [gaz]), (b, [baz])]
~~~~~~~~

haciendo la observación de que sólamente existen dos valores indexados por `'b'`.

`splitByRelation` evita la necesidad de tener una `Equal` pero debemos proporcionar el operador de
comparación.

`splitWith` divide los elementos en grupos que alternativamente satisfacen y no el predicado.
`selectSplit` selecciona grupos de elementos que satisfacen el predicado, descartando los otros.
Este es uno de esos casos raros en donde dos métodos comparten la misma firma/signatura, pero tienen
significados distintos.

`findLeft` y `findRight` sirven para extraer el primer elemento (de la izquierda o de la derecha)
que cumpla un predicado.

Haciendo uso adicional de `Equal` y `Order`, tenemos métodos `distinct` que devuelven agrupaciones.

{lang="text"}
~~~~~~~~
  def distinct[A: Order](fa: F[A]): IList[A] = ...
  def distinctE[A: Equal](fa: F[A]): IList[A] = ...
  def distinctBy[A, B: Equal](fa: F[A])(f: A => B): IList[A] =
~~~~~~~~

`distinct` se implementa de manera más eficiente que `distinctE` debido a que puede usar el
ordenamiento y por lo tanto usar un algoritmo tipo quicksort que es mucho más rápido que la
implementación ingenua de `List.distinct`. Las estructuras de datos (tales como los conjuntos)
pueden implementar `distinct` y su `Foldable` sin realizar ningún trabajo.

`distinctBy` permite la agrupación mediante la aplicación de una función a sus elementos. Por
ejemplo, agrupar nombres por su letra inicial.

Podemos hacer uso adicional de `Order` al extraer el elemento mínimo o máximo (o ambos extremos)
incluyendo variaciones usando el patrón `Of` o `By` para mapear primero a otro tipo o usar un tipo
diferente para hacer la otra comparación.

{lang="text"}
~~~~~~~~
  def maximum[A: Order](fa: F[A]): Option[A] = ...
  def maximumOf[A, B: Order](fa: F[A])(f: A => B): Option[B] = ...
  def maximumBy[A, B: Order](fa: F[A])(f: A => B): Option[A] = ...
  
  def minimum[A: Order](fa: F[A]): Option[A] = ...
  def minimumOf[A, B: Order](fa: F[A])(f: A => B): Option[B] = ...
  def minimumBy[A, B: Order](fa: F[A])(f: A => B): Option[A] = ...
  
  def extrema[A: Order](fa: F[A]): Option[(A, A)] = ...
  def extremaOf[A, B: Order](fa: F[A])(f: A => B): Option[(B, B)] = ...
  def extremaBy[A, B: Order](fa: F[A])(f: A => B): Option[(A, A)] =
~~~~~~~~

Por ejemplo podríamos preguntar cuál `String` es máxima `By` (por) longitud, o cuál es la máxima
longitud `Of` (de) los elementos.

{lang="text"}
~~~~~~~~
  scala> List("foo", "fazz").maximumBy(_.length)
  res: Option[String] = Some(fazz)
  
  scala> List("foo", "fazz").maximumOf(_.length)
  res: Option[Int] = Some(4)
~~~~~~~~

Esto concluye con las características clave de `Foldable`. El punto clave a recordar es que
cualquier cosa que esperaríamos encontrar en una librearía de colecciones está probablemente en
`Foldable` y si no está ahí, probablemente debería estarlo.

Concluiremos con algunas variantes de los métodos que ya hemos visto. Primero, existen métodos que
toman un `Semigroup` en lugar de un `Monoid`:

{lang="text"}
~~~~~~~~
  def fold1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def foldMap1Opt[A, B: Semigroup](fa: F[A])(f: A => B): Option[B] = ...
  def sumr1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def suml1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  ...
~~~~~~~~

devolviendo `Option` para tomar encuenta las estructuras de datos vacías (recuerde que `Semigroup`
no tiene un `zero`).

A> Los métodos se leen como `una-Opción`, y no `10 pt` com en la tipografía.

La typeclass `Foldable1` contiene muchas más variantes usando `Semigroup` de los métodos que usan
`Monoid` mostrados aquí (todos ellos con el sufijo `1`) y tienen sentido para estructuras de datos
que nunca están vacías, sin requerrir la existencia de un `Monoid` para los elementos.

De manera importante, existen variantes que toman valores de retorno monádicos. Ya hemos usado
`foldLeft` cuando escribimos por primera vez la lógica de negocios de nuestra aplicación, ahora
sabemos que proviene de `Foldable`:

{lang="text"}
~~~~~~~~
  def foldLeftM[G[_]: Monad, A, B](fa: F[A], z: B)(f: (B, A) => G[B]): G[B] = ...
  def foldRightM[G[_]: Monad, A, B](fa: F[A], z: =>B)(f: (A, =>B) => G[B]): G[B] = ...
  def foldMapM[G[_]: Monad, A, B: Monoid](fa: F[A])(f: A => G[B]): G[B] = ...
  def findMapM[M[_]: Monad, A, B](fa: F[A])(f: A => M[Option[B]]): M[Option[B]] = ...
  def allM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
  def anyM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
  ...
~~~~~~~~

### Traverse

`Traverse` es lo que sucede cuando hacemos el cruce de un `Functor` con un `Foldable`

{lang="text"}
~~~~~~~~
  trait Traverse[F[_]] extends Functor[F] with Foldable[F] {
    def traverse[G[_]: Applicative, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
    def sequence[G[_]: Applicative, A](fga: F[G[A]]): G[F[A]] = ...
  
    def reverse[A](fa: F[A]): F[A] = ...
  
    def zipL[A, B](fa: F[A], fb: F[B]): F[(A, Option[B])] = ...
    def zipR[A, B](fa: F[A], fb: F[B]): F[(Option[A], B)] = ...
    def indexed[A](fa: F[A]): F[(Int, A)] = ...
    def zipWithL[A, B, C](fa: F[A], fb: F[B])(f: (A, Option[B]) => C): F[C] = ...
    def zipWithR[A, B, C](fa: F[A], fb: F[B])(f: (Option[A], B) => C): F[C] = ...
  
    def mapAccumL[S, A, B](fa: F[A], z: S)(f: (S, A) => (S, B)): (S, F[B]) = ...
    def mapAccumR[S, A, B](fa: F[A], z: S)(f: (S, A) => (S, B)): (S, F[B]) = ...
  }
~~~~~~~~

Al principio del capítulo mostramos la importancia de `traverse` y `sequence` para invertir los
constructores de tipo para que se ajusten a un requerimiento (por ejemplo, de `List[Future[_]]` a
`Future[List[_]]`).

En `Foldable` no fuimos capaces de asumir que `reverse` fuera un concepto universal, pero ahora
podemos invertir algo.

También podemos hacer un `zip` de dos cosas que tengan un `Traverse`, obteniendo un `None` cuando
uno de los dos lados se queda sin elementos, usando `zipL` o `zipR` para decidir cuál lado truncar
cuando las longitudes no empatan. Un caso especial de `zip` es agregar un índice a cada entrada con
`indexed`.

`zipWithL` y `zipWithR` permiten la combinación de dos lados de un `zip` en un nuevo tipo, y
entonces devuelven simplemente un `F[C]`.

`mapAccumL` y `mapAccumR` son `map` regular combinado con un acumulador. Si nos topamos con la
situación de que nuestra costumbre vieja proveniente de Java nos quiere hacer usar una `var`, y
deseamos referirnos a ella en un `map`, deberíamos estar usando `mapAccumL`.

Por ejemplo, digamos que tenemos una lista de palabras y que deseamos borrar las palabras que ya
hemos encontrado. El algoritmo de filtrado no permite procesar las palabras de la lista una segunda
vez de modo que pueda escalarse a un stream infinito:

{lang="text"}
~~~~~~~~
  scala> val freedom =
  """We campaign for these freedoms because everyone deserves them.
     With these freedoms, the users (both individually and collectively)
     control the program and what it does for them."""
     .split("\\s+")
     .toList
  
  scala> def clean(s: String): String = s.toLowerCase.replaceAll("[,.()]+", "")
  
  scala> freedom
         .mapAccumL(Set.empty[String]) { (seen, word) =>
           val cleaned = clean(word)
           (seen + cleaned, if (seen(cleaned)) "_" else word)
         }
         ._2
         .intercalate(" ")
  
  res: String =
  """We campaign for these freedoms because everyone deserves them.
     With _ _ the users (both individually and collectively)
     control _ program _ what it does _ _"""
~~~~~~~~

Finalmente `Traversable1`, como `Foldable1`, proporciona variantes de estos métodos para las
estructuras de datos que no pueden estar vacías, aceptando `Semigroup` (más débil) en lugar de un
`Monoid`, y un `Apply` en lugar de un `Applicative`. Recuerde que `Semigroup` no tiene que
proporcionar un `.empty`, y `Apply` no tiene que proporcionar un `.point`.

### Align

`Align` es sobre unir y rellenar algo con un `Functor`. Antes de revisar `Align`, conozca al tipo de
datos `\&/` (pronunciado como *These*, o *¡Viva!*).

{lang="text"}
~~~~~~~~
  sealed abstract class \&/[+A, +B]
  final case class This[A](aa: A) extends (A \&/ Nothing)
  final case class That[B](bb: B) extends (Nothing \&/ B)
  final case class Both[A, B](aa: A, bb: B) extends (A \&/ B)
~~~~~~~~

es decir, se trata de una codificación con datos de un `OR` lógico inclusivo. `A` o `B` o ambos `A`
y `B`.

{lang="text"}
~~~~~~~~
  @typeclass trait Align[F[_]] extends Functor[F] {
    def alignWith[A, B, C](f: A \&/ B => C): (F[A], F[B]) => F[C]
    def align[A, B](a: F[A], b: F[B]): F[A \&/ B] = ...
  
    def merge[A: Semigroup](a1: F[A], a2: F[A]): F[A] = ...
  
    def pad[A, B]: (F[A], F[B]) => F[(Option[A], Option[B])] = ...
    def padWith[A, B, C](f: (Option[A], Option[B]) => C): (F[A], F[B]) => F[C] = ...
~~~~~~~~

`alignWith` toma una función de ya sea una `A` o una `B` (o ambos) a una `C` y devuelve una función
alzada de una tupla de `F[A]` y `F[B]` a una `F[C]`. `align` construye `\&/` a partir de dos `F[_]`.

`merge` nos permite combinar dos `F[A]` cuando `A` tiene un `Semigroup`. Por ejemplo, la
implementación de `Semigroup[Map[K, V]]` delega a un `Semigroup[V]`, combinando dos entradas de
resultados en la combinación de sus valores, teniendo la consecuencia de que `Map[K, List[A]]` se
comporta como un multimapa:

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> List(1)) merge Map("foo" -> List(1), "bar" -> List(2))
  res = Map(foo -> List(1, 1), bar -> List(2))
~~~~~~~~

y un `Map[K, Int]` simplemente totaliza sus contenidos cuando hace la unión

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> 1) merge Map("foo" -> 1, "bar" -> 2)
  res = Map(foo -> 2, bar -> 2)
~~~~~~~~

`.pad` y `.padWith` son para realizar una unión parcial de dos estructuras de datos que puedieran
tener valores faltantes en uno de los lados. Por ejemplo si desearamos agregar votos independientes
y retener el conocimiento de donde vinieron los votos

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> 1) pad Map("foo" -> 1, "bar" -> 2)
  res = Map(foo -> (Some(1),Some(1)), bar -> (None,Some(2)))
  
  scala> Map("foo" -> 1, "bar" -> 2) pad Map("foo" -> 1)
  res = Map(foo -> (Some(1),Some(1)), bar -> (Some(2),None))
~~~~~~~~

Existen variantes convenientes de `align` que se aprovechan de la estructura de `\&/`

{lang="text"}
~~~~~~~~
  ...
    def alignSwap[A, B](a: F[A], b: F[B]): F[B \&/ A] = ...
    def alignA[A, B](a: F[A], b: F[B]): F[Option[A]] = ...
    def alignB[A, B](a: F[A], b: F[B]): F[Option[B]] = ...
    def alignThis[A, B](a: F[A], b: F[B]): F[Option[A]] = ...
    def alignThat[A, B](a: F[A], b: F[B]): F[Option[B]] = ...
    def alignBoth[A, B](a: F[A], b: F[B]): F[Option[(A, B)]] = ...
  }
~~~~~~~~

las cuáles deberían tener sentido a partir de las firmas/signaturas de tipo. Ejemplos:

{lang="text"}
~~~~~~~~
  scala> List(1,2,3) alignSwap List(4,5)
  res = List(Both(4,1), Both(5,2), That(3))
  
  scala> List(1,2,3) alignA List(4,5)
  res = List(Some(1), Some(2), Some(3))
  
  scala> List(1,2,3) alignB List(4,5)
  res = List(Some(4), Some(5), None)
  
  scala> List(1,2,3) alignThis List(4,5)
  res = List(None, None, Some(3))
  
  scala> List(1,2,3) alignThat List(4,5)
  res = List(None, None, None)
  
  scala> List(1,2,3) alignBoth List(4,5)
  res = List(Some((1,4)), Some((2,5)), None)
~~~~~~~~

Note que las variantes `A` y `B` usan `OR` inclusivo, mientras que las variantes `This` y `That` son
exclusivas, devolviendo `None` si existen un valor en ambos lados, o ningún valor en algún lado.