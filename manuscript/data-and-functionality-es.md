# Datos y funcionalidad

De la POO estamos acostumbrados a pensar en datos y funcionalidad a la vez: las
jerarquías de clases llevan métodos, y mediante el uso de traits podemos
demandar que existan campos de datos. El polimorfismo en tiempo de ejecución se
da en términos de relaciones "*is a*" ("es un"), requiriendo que las clases
hereden de interfaces comunes. Esto puede llegar a complicarse a medida que la
base de código crece. Los tipos de datos simples se vuelven obscuros al
complicarse con cientos de líneas de métodos, los mixins con traits sufren a
causa del orden de inicialización, y las pruebas y mocking de componentes
altamente acoplados se convierte en una carga.

La PF toma un enfoque distinto, definiendo los datos y la funcionalidad de
manera separada. En este capítulo, se estudiará lo básico de los tipos de datos
y las ventajas de restringirnos a un subconjunto del lenguaje de programación
Scala. También descubriremos las *typeclasses* como una forma de lograr
polimorfismo en tiempo de compilación: pensando en la funcionalidad de una
estructura de datos en términos de "has a" ("tiene un"), más bien que relaciones
del tipo "es un".

## Datos

Los bloques de construcción fundamentales de los tipos son

- `final case class` también conocidos como *productos*
- `sealed abstract class` también conocidos como *coproductos*
- `case object` e `Int`, `Double`, `String`, (etc.), *valores*.

sin métodos o campos más que los parámetros del constructor. Preferimos usar
`abstract class` a `trait` para lograr mejor compatibilidad binaria y para
evitar la mezcla de traits.

El nombre colectivo para *productos*, *coproductos* y *valores* es *Tipo de
Datos Algebraico* (del inglés *Algebraic Data Type* o *ADT*).

Formamos la composición de tipos de datos a partir de `AND` y `XOR` (`OR`
exclusivos) del álgebra booleana: un producto contiene cada uno de los tipos de
los que está compuesto, pero un coproducto puede ser únicamente uno de ellos.
Por ejemplo

- producto: `ABC = a AND b AND c`
- coproducto: `XYZ = x XOR y XOR z`

escrito en Scala

```scala
  // values
  case object A
  type B = String
  type C = Int

  // product
  final case class ABC(a: A.type, b: B, c: C)

  // coproduct
  sealed abstract class XYZ
  case object X extends XYZ
  case object Y extends XYZ
  final case class Z(b: B) extends XYZ
```

### Estructuras de datos recursivas

Cuando un ADT se refiere a sí misma, la llamamos *Tipo de Datos Algebraico
Recursivo*.

`scalaz.IList`, una alternativa segura a `List` de la librería estándar, es
recursiva porque `ICons` contiene una referencia a `IList`.:

```scala
  sealed abstract class IList[A]
  final case class INil[A]() extends IList[A]
  final case class ICons[A](head: A, tail: IList[A]) extends IList[A]
```

### Funciones sobre ADTs

Las ADTs pueden tener *funciones puras*

```scala
  final case class UserConfiguration(accepts: Int => Boolean)
```

Pero las ADTs que contienen funciones tienen consigo algunas limitaciones dado
que no se pueden traducir de manera perfecta a la JVM. Por ejemplo, los antiguos
métodos `Serializable`, `hashCode`, `equals` y `toString` no se comportan como
uno podría razonablemente esperar.

Desgraciadamente, `Serializable` es usado por frameworks populares, a pesar de
que existen alternativas superiores. Una trampa común es olvidar que
`Serializable` podría intentar serializar la cerradura completa de una función,
lo que podría ocasionar el fallo total de servidores de producción. Una trampa
similar aplica a clases de Java antiguas tales como `Throwable`, que pueden
llevar consigo referencias a objetos arbitrarios.

Exploraremos alternativas a los métodos antiguos cuando discutamos Scalaz en el
próximo capítulo, a costa de perder interoperabilidad con algo de código antiguo
de Java y Scala.

### Exhaustividad

Es importante que usemos `sealed abstract class`, no simplemente `abstract
class`, cuando definimos un tipo de datos. Sellar una `class` significa que
todos los subtipos deben estar definidos en el mismo archivo, permitiendo que el
compilador pueda verificar de manera exhaustiva durante el proceso de empate de
patrones y en macros que eliminen el código repetitivo. Por ejemplo,

```scala
  scala> sealed abstract class Foo
         final case class Bar(flag: Boolean) extends Foo
         final case object Baz extends Foo

  scala> def thing(foo: Foo) = foo match {
           case Bar(_) => true
         }
  <console>:14: error: match may not be exhaustive.
  It would fail on the following input: Baz
         def thing(foo: Foo) = foo match {
                               ^
```

Esto muestra al desarrollador qué ha fallado cuando añaden un nuevo producto a
la base de código. Aquí se está usando `-Xfatal-warnings`, porque de otra manera
esto es solamente un *warning* o advertencia.

Sin embargo, el compilador no realizará un chequeo exhaustivo si la clase no
está sellada o si existen guardas, por ejemplo

```scala
  scala> def thing(foo: Foo) = foo match {
           case Bar(flag) if flag => true
         }

  scala> thing(Baz)
  scala.MatchError: Baz (of class Baz$)
    at .thing(<console>:15)
```

para conseguir seguridad, es mejor evitar las guardas en los tipos sellados.

La bandera
[`-Xstrict-patmat-analysis`](https://github.com/scala/scala/pull/5617) se ha
propuesto como una mejora al lenguaje para realizar chequeos adicionales durante
el proceso de empate de patrones.

### Productos alternativos y coproductos

Otra forma de producto es la tupla, que es como una `final case class` sin
etiqueta o nombre.

`(A.type, B, C)` es equivalente a `ABC` en el ejemplo de arriba, pero es mejor
usar `final case class` cuando se trate de una parte de alguna ADT porque es
algo difícil lidiar con la falta de nombres, y `case class` tiene mucho mejor
performance para valores primitivos.

Otra forma de coproducto es aquella que se logra al anidar tipos `Either`, por
ejemplo

```scala
  Either[X.type, Either[Y.type, Z]]
```

y es equivalente a la clase abstracta sellada `XYZ`. Se puede lograr una
sintáxis más limpia al definir tipos `Either` anidados al crear un alias de tipo
que termine en dos puntos, permitiendo así el uso de notación infija con
asociación hacia la derecha:

```scala
  type |:[L,R] = Either[L, R]

  X.type |: Y.type |: Z
```

Esto es útil al crear coproductos anónimos cuando no podemos poner todas las
implementaciones en el mismo archivo de código fuente.

```scala
  type Accepted = String |: Long |: Boolean
```

Otra forma de coproducto alternativa es creat un `sealed abstract class`
especial con definiciones `final case class` que simplemente envuelvan a los
tipos deseados:

```scala
  sealed abstract class Accepted
  final case class AcceptedString(value: String) extends Accepted
  final case class AcceptedLong(value: Long) extends Accepted
  final case class AcceptedBoolean(value: Boolean) extends Accepted
```

El empate de patrones en estas formas de coproducto puede ser tediosa, razón por
la cual los [tipos Unión](https://contributors.scala-lang.org/t/733) están
explorandose en la siguiente generación del compilador de Scala, Dotty. Macros
tales como [totalitarian](https://github.com/propensive/totalitarian) y
[iotaz](https://github.com/frees-io/iota) existen como formas alternativas de
codificar coproductos anónimos.

### Transmisión de información

Además de ser contenedores para información de negocios necesaria, los tipos de
datos pueden usarse para codificar restricciones. Por ejemplo,

```scala
  final case class NonEmptyList[A](head: A, tail: IList[A])
```

nunca puede estar vacía. Esto hace que `scalaz.NonEmptyList` sea un tipo de
datos útil a pesar de que contiene la misma información que `IList`.

Los tipos producto con frecuencia contienen tipos que son mucho más generales de
lo que en realidad se requiere. En programación orientada a objetos tradicional,
la situación se manejaría con validación de datos de entrada a través de
aserciones:

```scala
  final case class Person(name: String, age: Int) {
    require(name.nonEmpty && age > 0) // breaks Totality, don't do this!
  }
```

En lugar de hacer esto, podríamos usar el tipo de datos `Either` para
proporcionar `Right[Person]` para instancias válidas y protegernos de que
instancias inválidas se propaguen. Note que el constructor es privado
(`private`):

```scala
  final case class Person private(name: String, age: Int)
  object Person {
    def apply(name: String, age: Int): Either[String, Person] = {
      if (name.nonEmpty && age > 0) Right(new Person(name, age))
      else Left(s"bad input: $name, $age")
    }
  }

  def welcome(person: Person): String =
    s"${person.name} you look wonderful at ${person.age}!"

  for {
    person <- Person("", -1)
  } yield welcome(person)
```

#### Tipos de datos refinados

Una forma limpia de restringir los valores de un tipo general es con la librería
`refined`, que provee una conjunto de restricciones al contenido de los datos.
Para instalar refined, agregue la siguiente línea a su archivo `build.sbt`

```scala
  libraryDependencies += "eu.timepit" %% "refined-scalaz" % "0.9.2"
```

y los siguientes imports

```scala
  import eu.timepit.refined
  import refined.api.Refined
```

`Refined` permite definir `Person` usando tipos ad hoc refinados para capturar
los requerimientos de manera exacta, escrito `A Refined B`.

A> Todos los tipos con dos parámetros pueden escribirse de manera *infija* en
A> Scala. Por ejemplo, `Either[String, Int]`es lo mismo que `String Either Int`.
A> Es costumbre en el uso de `Refined` escribir usando notación infija dado que
A> `A Refined B` puede leerse como "una `A` que cumple los requisitos definidos
A> en `B`".

```scala
  import refined.numeric.Positive
  import refined.collection.NonEmpty

  final case class Person(
    name: String Refined NonEmpty,
    age: Int Refined Positive
  )
```

El valor subyacente puede obtenerse con `.value`. Podemos construir un valor en
tiempo de ejecución usando `.refineV`, que devuelve un `Either`

```scala
  scala> import refined.refineV
  scala> refineV[NonEmpty]("")
  Left(Predicate isEmpty() did not fail.)

  scala> refineV[NonEmpty]("Sam")
  Right(Sam)
```

Si agregamos el siguiente import

```scala
  import refined.auto._
```

podemos construir valores válidos en tiempo de compilación y obtener errores si
el valor provisto no cumple con los requerimientos

```scala
  scala> val sam: String Refined NonEmpty = "Sam"
  Sam

  scala> val empty: String Refined NonEmpty = ""
  <console>:21: error: Predicate isEmpty() did not fail.
```

Es posible codificar requerimientos más completos, por ejemplo podríamos usar la
regla `MaxSize` que con los siguientes imports

```scala
  import refined.W
  import refined.boolean.And
  import refined.collection.MaxSize
```

que captura los requerimientos de que `String` debe no ser vacía y además tener
un máximo de 10 caracteres.

```scala
  type Name = NonEmpty And MaxSize[W.`10`.T]

  final case class Person(
    name: String Refined Name,
    age: Int Refined Positive
  )
```

A> La notación `W` es una abreviatura para "witness". Esta sintaxis será mucho
A> más simple en Scala 2.13, que tiene soporte para *tipos literales*:
A>
A> ```scala
A>   type Name = NonEmpty And MaxSize[10]
A> ```

Es fácil definir requerimientos a la medida que no estén cubiertos por la
librería refined. Por ejemplo en `drone-dynamic-agents` necesitaremos una manera
de asegurarnos de que `String` tenga contenido
`application/x-www-form-urlencoded`. Podríamos crear una regla de `Refined`
usando la librería de expresiones regulares de Java:

```scala
  sealed abstract class UrlEncoded
  object UrlEncoded {
    private[this] val valid: Pattern =
      Pattern.compile("\\A(\\p{Alnum}++|[-.*_+=&]++|%\\p{XDigit}{2})*\\z")

    implicit def urlValidate: Validate.Plain[String, UrlEncoded] =
      Validate.fromPredicate(
        s => valid.matcher(s).find(),
        identity,
        new UrlEncoded {}
      )
  }
```

### Simple de compartir

Al no proporcionar ninguna funcionalidad, las ADTs pueden tener un conjunto
mínimo de dependencias. Esto hace que sean fáciles de publicar y de compartir
con otros desarrolladores. Al usar un lenguaje de modelado de datos simple, se
hace posible la interacción con equipos interdisciplinarios, tales como DBAs,
desarrolladores de interfaces de usuario y analistas de negocios, usando el
código fuente actual en lugar de un documento escrito manualmente como la fuente
de la verdad.

Más aún, las herramientas pueden ser escritas más fácilmente para producir o
consumir esquemas de otros lenguajes de programación y protocolos físicos.

### Contando la complejidad

La complejidad de un tipo de datos es la cuenta de los valores que puede tener.
Un buen tipo de datos tiene la cantidad mínima de complejidad requerida para
contener la información que transmite, y nada más.

Los valores tienen una complejidad inherente:

- `Unit` tien un solo valor (razón por la cual se llama "unit")
- `Boolean` tiene dos valores
- `Int` tiene 4,294,967,295 valores
- `String` tiene valores infinitos, de manera práctica

Para encontrar la complejidad de un producto, multiplicamos la complejidad de
cada parte

- `(Boolean, Boolean)` tiene 4 valores, (`2 * 2`)
- `(Boolean, Boolean, Boolean)` tiene 8 valores (`2 * 2 * 2)`

Para encontrar la complejidad de un coproducto, sumamos la complejidad de cada
parte

- `(Boolean |: Boolean)` tiene 4 valores (`2 + 2`)
- `(Boolean |: Boolean |: Boolean`) tiene 6 valores (`2 + 2 + 2`)

Para encontrar la complejidad de un ADT con un parámetro de tipo, multiplique
cada parte por la complejidad del parámetro de tipo:

- `Option[Boolean]` tiene 3 valores, `Some[Boolean]` y `None`. (`2 + 1`).

En la PF, las funciones son *totales* y deben devolver el mismo valor para cada
entrada, no una `Exception`. Minimizar la complejidad de las entradas y las
salidas es la mejor forma de lograr totalidad. Como regla de oro, es un signo de
una función con mal dise;o cuando la complejidad del valor de retorno es más
grande que el producto de sus entradas: es una fuente de entropía.

La complejidad de una función total es el número de funciones posibles que
pueden satisfacer la signatura del tipo: la salida a la potencia de la entrada.

- `Unit => Boolean` tiene complejidad 2
- `Boolean => Boolean` tiene complejidad 4
- `Option[Boolean] => Option[Boolean]` tiene complejidad 27
- `Boolean => Int` es como un quintillón, aproximándose a un sextillón.
- `Int => Boolean` es tan grande que si a todas las implementaciones se les asignara un número
  único, cada una requeriría 4 gigabytes para representarla.

En la práctica, `Int => Boolean` será algo tan simple como `isOdd`, `isEven` o
un `BitSet` disperso. Esta función, cuando se usa en una ADT, será mejor
reemplazarla con un coproducto que asigne un nombre al conjunto de funciones que
son relevantes.

Cuando la complejidad es "infinito a la entrada, infinito a la salida"
deberíamos introducir tipos de datos que restrictivos y validaciones más
cercanos al punto de entrada con `Refined` de la sección anterior.

La habilidad de contar la complejidad de una signatura de tipo tiene otra
aplicación práctica: podemos encontrar signaturas de tipo más simples usando
Álgebra de la secundaria! Para ir de una signatura de tipos al álgebra de las
complejidades, simplemente reemplace

- `Either[A, B]` con `a + b`
- `(A, B)` con `a * b`
- `A => B` con `b ^ a`

hacer algunos rearreglos, y convertir de vuelta. Por ejemplo, digamos que hemos
diseñado un framework basado en callbacks y que hemos logrado llegar a la
situación donde tenemos la siguiente signatura de tipos:

```scala
  (A => C) => ((B => C) => C)
```

Podríamos convertir y reordenar

```scala
  (c ^ (c ^ b)) ^ (c ^ a)
  = c ^ ((c ^ b) * (c ^ a))
  = c ^ (c ^ (a + b))
```

y convertir de vuelta a tipos y obtener

```scala
  (Either[A, B] => C) => C
```

que es mucho más simple: sólo es necesario pedir a los usuarios de nuestro
framework que proporcionen un `Either[A, B] => C`.

La misma línea de razonamiento puede ser usada para probar que

```scala
  A => B => C
```

es equivalente a

```scala
  (A, B) => C
```

que también se conoce como *Currying*.

### Prefiera coproducto en vez de producto

Un problema de modelado típico que surge con frecuencia es el que tenemos cuando
hay parámetros de configuración mutuamente exclusivos `a`, `b`, y `c`. El
producto `(a: Boolean, b: Boolean, c: Boolean)` tiene complejidad 8, mientras
que el coproducto

```scala
  sealed abstract class Config
  object Config {
    case object A extends Config
    case object B extends Config
    case object C extends Config
  }
```


tiene una complejidad de 3. Es mejor modelar estos parámetros de configuración
como un coproducto más bien que permitir la existencia de 5 estados inválidos.

La complejidad de un tipo de datos también tiene implicaciones a la hora de
hacer pruebas. Es prácticamente imposible probar cada entrada a una función,
pero es fácil probar una muestra de los valores con el framework para hacer
pruebas de propiedades con [Scalacheck](https://www.scalacheck.org).

Si una muestra aleatoria de un tipo de datos tiene una probabilidad baja de ser
válida, tenemos una señal de que los datos están modelados incorrectamente.

### Optimizaciones

Una gran ventaja de usar un conjunto simplificado del lenguaje Scala para
representar tipos de datos es que el tooling puede optimizar la representación
en bytecodes de la JVM.

Por ejemplo, podríamos empacar campos `Boolean` y `Option` en un `Array[Byte]`,
hacer un caché con los valores, memoizar `hashCode`, optimizar `equals`, usar
sentencias `@switch` al momento de realizar empate de patrones, y mucho más.

Estas optimizaciones no son aplicables a jerarquías de clases en POO que tal vez
manipulen el estado, lancen excepciones, o proporcionen implementaciones ad hoc
de los métodos.

## Funcionalidad

Las funciones puras están definidas típicamente como métodos en un `object`.

```scala
  package object math {
    def sin(x: Double): Double = java.lang.Math.sin(x)
    ...
  }

  math.sin(1.0)
```

Sin embargo, el uso de métodos en `object`s puede ser un poco torpe, dado que se
lee de dentro hacia afuera, y no de izquierda a derecha. Además, una función en
un `object` se roba el namespace. Si tuvieramos que definir `sin(t: T)` en algún
otro lugar, tendríamos errores por referencias ambiguas. Este es el mismo
problema de los métodos estáticos de Java vs los métodos de clase.

W> La clase de desarrollador que pone los métodos dentro de un `trait`,
W> requiriendo que los usuarios la mezclen usando el patrón del pastel (*cake
W> pattern*), debería ser atormentado. Esto deja filtraciones de los detalles de
W> implementación a APIs públicas, hace que el bytecode se hinche, hace que la
W> compatibilidad binaria sea prácticamente imposible, además de que confunde a
W> los autocompletadores en un IDE.

Con ayuda de la característica del lenguaje `implicit class`, (también conocida
como *metodología de extensión* o *sintaxis*), y un poco de código tedioso, es
posible lograr un estilo más familiar:

```scala
  scala> implicit class DoubleOps(x: Double) {
           def sin: Double = math.sin(x)
         }

  scala> (1.0).sin
  res: Double = 0.8414709848078965
```

Con frecuencia es mejor saltarnos la definición de un `object` e ir directo con
una `implicit class`, manteniendo el código repetitivo al mínimo:

```scala
  implicit class DoubleOps(x: Double) {
    def sin: Double = java.lang.Math.sin(x)
  }
```

A> `implicit class` es una conveniencia sintáctica para una conversión
A>implícita:
A>
A> ```scala
A>   implicit def DoubleOps(x: Double): DoubleOps = new DoubleOps(x)
A>   class DoubleOps(x: Double) {
A>     def sin: Double = java.lang.Math.sin(x)
A>   }
A> ```
A>
A> que desgraciadamente tiene un costo en tiempo de ejecución: cada vez que se
A> invoca al método de extensión, se debe construir un objeto intermedio
A> `DoubleOps` y entonces debe descartarse. Esto contribuye a la presión del
A> garbage collector en las máquinas virtuales.
A>
A> Existe una forma más verbosa de `implicit class` que evita la alocación y que
A> por lo tanto es preferida:
A>
A> ```scala
A>   implicit final class DoubleOps(private val x: Double) extends AnyVal {
A>     def sin: Double = java.lang.Math.sin(x)
A>   }
A> ```

### Funciones polimórficas

La clase de funciones más comunes es la función polimórfica, que vive en una
*typeclass*. Una typeclass es un trait que:

- no tiene estado
- tiene un parámetro de tipo
- tiene al menos un método abstracto (*combinador primitivo*)
- puede contener métodos *generalizadores* (*combinadores derivados*)
- puede extender a otros typeclases

Sólo puede existir una implementación de una typeclass para un parámetro de tipo
específico correspondiente, una propiedad conocida también como *coherencia de
typeclass*. Las typeclasses se ven superficialmente similares a las interfaces
algebraicas del capítulo anterior, pero las álgebras no tienen que ser
coherentes.

A> La coherencia de typeclasses es principalmente sobre consistencia, y la
A> consistencia nos da confianza para usar parámetros `implícitos`. Sería
A> difícil razonar sobre código que se comporta de manera distinta dependiendo
A> de los imports implícitos que estén dentro del alcance (*scope*). La
A> coherencia de typeclasses dice, de manera efectiva, que los imports no
A> deberían impactar el comportamientod el código.
A>
A> Adicionalmente, la coherencia de typeclasses nos permite hacer un caché
A> global de implícitos en tiempo de ejecución y ahorrar en asignación dinámica
A> de memoria, consiguiendo mejoras en el rendimiento debido a presión reducida
A> en el recolector de basura.

Las typeclasses son usadas en la librería estándar de Scala. Exploraremos una
versión simplificada de `scala.math.Numeric` para demostrar el principio:

```scala
  trait Ordering[T] {
    def compare(x: T, y: T): Int

    def lt(x: T, y: T): Boolean = compare(x, y) < 0
    def gt(x: T, y: T): Boolean = compare(x, y) > 0
  }

  trait Numeric[T] extends Ordering[T] {
    def plus(x: T, y: T): T
    def times(x: T, y: T): T
    def negate(x: T): T
    def zero: T

    def abs(x: T): T = if (lt(x, zero)) negate(x) else x
  }
```

Podemos ver las características clave de una typeclass en acción:

- no hay estado
- `Ordering` y `Numeric` tienen un parámetro de tipo `T`
- `Ordering` tiene un `compare` abstracto, y `Numeric` tiene un `plus`, `times`,
  `negate` y `zero` abstractos.
- `Ordering` define un `lt` generalizado, y `gt` basados en `compare`, `Numeric`
  define `abs` en términos de `lt`, `negate` y `zero`.
- `Numeric` extiende `Ordering`.

Ahora podemos escribir funciones para tipos que "tengan un(a)" typeclass
`Numeric`:

```scala
  def signOfTheTimes[T](t: T)(implicit N: Numeric[T]): T = {
    import N._
    times(negate(abs(t)), t)
  }
```

Ya no dependemos de la jerarquía POO de nuestros tipos de entrada, es decir, no
demandamos que nuestra entrada sea ("is a") `Numeric`, lo cual es vitalmente
importante si deseamos soportar clases de terceros que no podemos redefinir.

Otra ventaja de las typeclasses es que la asociación de funcionalidad a los
datos se da en tiempo de compilación, en oposición del despacho dinámico en
tiempo de ejecución de la POO.

Por ejemplo, mientras que la clase `List` solo puede tener una implementación de
un método, una typeclasss nos permite tener diferentes implementaciones
dependiendo del contenido de `List` y por lo tanto nos permite descargar el
trabajo al tiempo de compilación en lugar de dejarlo al tiempo de ejecución.

### Sintaxis

La sintaxis para escribir `signOfTheTimes` es un poco torpe, y hay algunas cosas
que podemos hacer para limpiarla.

Los usuarios finales de nuestro código, preferirán que nuestro métod use
*context bounds*, dado que la signatura se lee limpiamente como "toma una `T`
que tiene un `Numeric`"

```scala
  def signOfTheTimes[T: Numeric](t: T): T = ...
```

pero ahora tenemos que usar `implicitly[Numeric[T]]` en todos lados. Al definir
el código repetitivo en el *companion object* de la typeclass

```scala
  object Numeric {
    def apply[T](implicit numeric: Numeric[T]): Numeric[T] = numeric
  }
```

podemos obtener el implícito con menos ruido

```scala
  def signOfTheTimes[T: Numeric](t: T): T = {
    val N = Numeric[T]
    import N._
    times(negate(abs(t)), t)
  }
```

Pero es todavía peor para nosotros como los implementadores. Tenemos el problema
sintáctico de métodos estáticos afuera vs métodos de la clase. Una forma de
lidiar con esta situación es mediante introducir `ops` en el companion de la
typeclass:

```scala
  object Numeric {
    def apply[T](implicit numeric: Numeric[T]): Numeric[T] = numeric

    object ops {
      implicit class NumericOps[T](t: T)(implicit N: Numeric[T]) {
        def +(o: T): T = N.plus(t, o)
        def *(o: T): T = N.times(t, o)
        def unary_-: T = N.negate(t)
        def abs: T = N.abs(t)

        // duplicated from Ordering.ops
        def <(o: T): T = N.lt(t, o)
        def >(o: T): T = N.gt(t, o)
      }
    }
  }
```

Note que `-x` se expande a `x.unary_-` mediante las conveniencias sintácticas
del compilador, razón por la cual definimos `unary_-` como un método de
extensión. Podemos ahora escribir la forma mucho más clara:

```scala
  import Numeric.ops._
  def signOfTheTimes[T: Numeric](t: T): T = -(t.abs) * t
```

La buena noticia es que nunca necesitamos escribir este tipo de código
repetitivo porque [Simulacrum](https://github.com/mpilquist/simulacrum)
proporciona una anotación `@typeclass` que genera automáticamente los métodos
`apply` y `ops`. Incluso nos permite definir nombres alternativos (normalmente
simbólicos) para métodos comunes. Mostrando el código completo:

```scala
  import simulacrum._

  @typeclass trait Ordering[T] {
    def compare(x: T, y: T): Int
    @op("<") def lt(x: T, y: T): Boolean = compare(x, y) < 0
    @op(">") def gt(x: T, y: T): Boolean = compare(x, y) > 0
  }

  @typeclass trait Numeric[T] extends Ordering[T] {
    @op("+") def plus(x: T, y: T): T
    @op("*") def times(x: T, y: T): T
    @op("unary_-") def negate(x: T): T
    def zero: T
    def abs(x: T): T = if (lt(x, zero)) negate(x) else x
  }

  import Numeric.ops._
  def signOfTheTimes[T: Numeric](t: T): T = -(t.abs) * t
```

Cuando existe un operador simbólico personalizado (`@op`), se puede pronunciar
como el nombre del método, por ejemplo, `<` se pronuncia "menor que", y no
"paréntesis angular izquierdo".

### Instancias

Las *instancias* de `Numeric` (que también son instancias de `Ordering`) se
definen como un `implicit val` que extiende a la typeclass, y que proporciona
implementaciones optimizadas pra los métodos generalizados:

```scala
  implicit val NumericDouble: Numeric[Double] = new Numeric[Double] {
    def plus(x: Double, y: Double): Double = x + y
    def times(x: Double, y: Double): Double = x * y
    def negate(x: Double): Double = -x
    def zero: Double = 0.0
    def compare(x: Double, y: Double): Int = java.lang.Double.compare(x, y)

    // optimised
    override def lt(x: Double, y: Double): Boolean = x < y
    override def gt(x: Double, y: Double): Boolean = x > y
    override def abs(x: Double): Double = java.lang.Math.abs(x)
  }
```

Aunque estamos usando `+`, `*`, `unary_- `, `<` y `>` aquí, que están en el ops
(y podría ser un loop infinito!) estos métodos ya existen en `Double`. Los
métodos de una clase siempre se usan en preferencia a los métodos de extensión.
En realidad, el compilador de Scala realiza un manejo especial de los tipos
primitivos y convierte estas llamadas en instrucciones directas `dadd`, `dmul`,
`dcmpl`, y `dcmpg`, respectivamente.

También podemos implementar `Numeric` para la clase de Java `BigDecimal` (evite
`scala.BigDecimal, [it is fundamentally
broken](https://github.com/scala/bug/issues/9670))

```scala
  import java.math.{ BigDecimal => BD }

  implicit val NumericBD: Numeric[BD] = new Numeric[BD] {
    def plus(x: BD, y: BD): BD = x.add(y)
    def times(x: BD, y: BD): BD = x.multiply(y)
    def negate(x: BD): BD = x.negate
    def zero: BD = BD.ZERO
    def compare(x: BD, y: BD): Int = x.compareTo(y)
  }
```

Podríamos crear nuestra propia estructura de datos para números complejos:

```scala
  final case class Complex[T](r: T, i: T)
```

y construir un `Numeric[Complex[T]]` si `Numeric[T]` existe. Dado que estas
instancias dependen del parámetro de tipo, es un `def` y no un `val`.

```scala
  implicit def numericComplex[T: Numeric]: Numeric[Complex[T]] =
    new Numeric[Complex[T]] {
      type CT = Complex[T]
      def plus(x: CT, y: CT): CT = Complex(x.r + y.r, x.i + y.i)
      def times(x: CT, y: CT): CT =
        Complex(x.r * y.r + (-x.i * y.i), x.r * y.i + x.i * y.r)
      def negate(x: CT): CT = Complex(-x.r, -x.i)
      def zero: CT = Complex(Numeric[T].zero, Numeric[T].zero)
      def compare(x: CT, y: CT): Int = {
        val real = (Numeric[T].compare(x.r, y.r))
        if (real != 0) real
        else Numeric[T].compare(x.i, y.i)
      }
    }
```

El lector observador podrá notar que `abs` no es lo que un matemático esperaría.
El valor de retorno correcto para `abs` debería ser de tipo `T`, y no
`Complex[T]`.

`scala.math.Numeric` intenta hacer muchas cosas y no generaliza adecuadamente
más allá de los números reales. esta es una buena lección que muestra que
typeclasses pequeñas, bien definidas, son con frecuencia mejores que una
colección monolítica de características demasiado específicas.

### Resolución implícita

Ya hemos discutido los implícitos bastante: esta sección es para clarificar qué
son los implícitos y de cómo funcionan.

Los *parámetros implícitos* se usan cuando un método solicita que una instancia
única de un tipo particular exista en el ámbito/alcance implícito (*implicit
scope*) del que realiza la llamada al método, con una sintáxis especial para las
instancias de las typeclasses. Los parámetros implícitos son una manera limpia
de pasar configuración a una aplicación.

En este ejemplo, `foo` requiere que las instancias de typeclass de `Numeric` y
`Typeable` estén disponibles para `A`, así como un objecto implícito `Handler`
que toma dos parámetros de tipo

```scala
  def foo[A: Numeric: Typeable](implicit A: Handler[String, A]) = ...
```

Las *conversiones implícitas* se usan cuando existe un `implicit def`. Un uso
posible de dichas conversiones implícitas es para habilitar la metodología de
extensión. Cuando el compilador está calculando cómo invocar un método, primero
verifica si el método existe en el tipo, luego en sus ancestros (reglas
similares a las de Java). Si no encuentra un emparejamiento, entonces buscará en
conversiones implícitas en el *alcance implícito*, y entonces buscará métodos
sobre esos tipos.

Otro uso de las conversiones implícitas es en la derivación de typeclasses. En
la sección anterior escribimos una `implicit def` que construía un
`Numeric[Complex[T]]` si existía un `Numeric[T]` en el alcance implícito. Es
posible encadenar juntas muchas `implicit def` (incluyendo las que pudieran
invocarse de manera recursiva) y esto forma la bese de la *programación con
tipos*, permitiendo que cálculos se realicen en tiempo de compilación más bien
que en tiempo de ejecución.

El pegamento que combina parámetros implícitos (receptores) con conversiones
implícitas (proveedores) es la resolución implícita.

Primero, se buscan implícitos en el ámbito/alcance de variables normales, en
orden:

- alcance local, incluyendo imports dentro del alcance (por ejemplo, en el
  bloque o el método)
- alcance externo, incluyendo imports detro del alcance (por ejemplo miembros de
  la clase)
- ancestros (por ejemplo miembros en la super clase)
- el objeto paquete actual
- el objeto paquete de los ancestros (cuando se usan paquetes anidados)
- los imports del archivo

Si se falla en encontrar un emparejamiento, se busca en el alcance especial, la
cual se realiza para encontrar instancias implícitas dentro del *companion* del
tipo, su paquete de objetos, objetos externos (si están anidados), y entonces se
repite para sus ancestros. Esto se realiza, en orden, para:

- el parámetro de tipo dado
- el parámetro de tipo esperado
- el parámetro de tipo (si hay alguno)

Si se encuentran dos implícitos que emparejen en la misma fase de resolución
implícita, se lanza un error de implícito ambiguo (*ambiguous implicit*).

Los implícitos con frecuencia se definen un un `trait, el cual se extiende con
un objecto. Esto es para controlar la prioridad de un implícito con referencia a
otro más específico, para evitar implícitos ambiguos.

La Especificación del lenguaje de Scala es un tanto vaga para los casos poco
comunes, y en realidad la implementación del compilador es el estándar real. Hay
algunas reglas de oro que usaremos en este libro, por ejemplo preferir `implicit
val` en lugar de `implicit object` a pesar de la tentación de teclear menos. Es
una [capricho de la resolución
implícita](https://github.com/scala/bug/issues/10411) que los `implicit object`
en los objetos companion no sean tratados de la misma manera que un `implicit
val`.

La resolución implícita se queda corta cuando existe una jerarquía de
typeclasses, como `Ordering` y `Numeric`. Si escribimos una función que tome un
implícito de `Ordering`, y la llamamos para un tipo primitivo que tiene una
instancia de `Numeric` definido en el companion `Numeric`, el compilador fallará
en encontrarlo.

La resolución implícita es particularmente mala [si se usan aliases de
tipo](https://github.com/scala/bug/issues/10582) donde la *forma* de los
parámetros implícitos son cambiados. Por ejemplo un parámetro implícito usando
un alias tal como `type Values[A] = List[Option[A]]` probablemente fallará al
encontrar implícitos definidos como un `List[Option[A]]` porque la forma se
cambió de una *cosa de cosas* de `A` a una *cosa* de `A`s.

## Modelling OAuth2

Terminaremos este capítulo con un ejemplo práctico de modelado de datos y
derivación de typeclasses, combinado con el diseño del álgebra/módulo del
capítulo anterior.

En nuestra aplicación `drone-dynamic-agents`, debemos comunicarnos con Drone y
Google Cloud usando JSON sobre REST. Ambos servicios usan
[OAuth2](https://tools.ietf.org/html/rfc6749) para la autenticación.

Hay muchas maneras de interpretar OAuth2, pero nos enfocaremos en la versión que
funciona para Google Cloud (la versión para Drone es aún más sencilla).

### Descripción

Cada aplicación de Google Cloud necesita tener una *OAuth 2.0 Client key*
configurada en

```scala
  https://console.developers.google.com/apis/credentials?project={PROJECT_ID}
```

y así se obtiene un *Client ID* y un *Client secret*.

La aplicación puede entonces un *código* para usarse *una* sola vez, al hacer
que el usuario realice una *Authorization Request* (petición de autorización) en
su navegador (sí, de verdad, **en su navegador**). Necesitamos hacer que esta
página se abra en el navegador:

```text
  https://accounts.google.com/o/oauth2/v2/auth?\
    redirect_uri={CALLBACK_URI}&\
    prompt=consent&\
    response_type=code&\
    scope={SCOPE}&\
    access_type=offline&\
    client_id={CLIENT_ID}
```

El *código* se entrega al `{CALLBACK_URI}` en una petición `GET`. Para
capturarla en nuestra aplicación, necesitamos un servidor web escuchando en
`localhost`.

Una vez que tenemos el *código*, podemos realizar una petición *Acess Token
Request*:

```text
  POST /oauth2/v4/token HTTP/1.1
  Host: www.googleapis.com
  Content-length: {CONTENT_LENGTH}
  content-type: application/x-www-form-urlencoded
  user-agent: google-oauth-playground
  code={CODE}&\
    redirect_uri={CALLBACK_URI}&\
    client_id={CLIENT_ID}&\
    client_secret={CLIENT_SECRET}&\
    scope={SCOPE}&\
    grant_type=authorization_code
```

y entonces se tiene una respuesta JSON en el payload

```json
  {
    "access_token": "BEARER_TOKEN",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "REFRESH_TOKEN"
  }
```

Todas las peticiones al servidor, provenientes del usuario, deben incluir el
encabezado

```text
  Authorization: Bearer BEARER_TOKEN
```

después de sustituir el `BEARER_TOKEN` real.

Google hace que expiren todas excepto las 50 más recientes *bearer tokens*, de
modo que los tiempos de expiración son únicamente una guía. Los *refresh tokens*
persisten entre sesiones y pueden expirar manualmente por el usuario. Por lo
tanto podemos tener una aplicación de configuración que se use una sola vez para
obtener el *refresh token* y entonces incluirlo como una configuración para la
instalación del usuario del servidor *headless*.

Drone no implementa el endpoint `/auth`, o el refresco, y simplemente
proporciona un `BEARER_TOKEN` a través de su interfaz de usuario.

### Datos

El primer paso es modelar los datos necesarios para OAuth2. Creamos un ADT con
los campos teniendo el mismo nombre como es requerido por el servidor OAuth2.
Usaremos `String` y `Long` por brevedad, pero podríamos usar tipos refinados

```scala
  import refined.api.Refined
  import refined.string.Url

  final case class AuthRequest(
    redirect_uri: String Refined Url,
    scope: String,
    client_id: String,
    prompt: String = "consent",
    response_type: String = "code",
    access_type: String = "offline"
  )
  final case class AccessRequest(
    code: String,
    redirect_uri: String Refined Url,
    client_id: String,
    client_secret: String,
    scope: String = "",
    grant_type: String = "authorization_code"
  )
  final case class AccessResponse(
    access_token: String,
    token_type: String,
    expires_in: Long,
    refresh_token: String
  )
  final case class RefreshRequest(
    client_secret: String,
    refresh_token: String,
    client_id: String,
    grant_type: String = "refresh_token"
  )
  final case class RefreshResponse(
    access_token: String,
    token_type: String,
    expires_in: Long
  )
```

W> Evite usar `java.net.URL` a toda costa: usa DNS para encontrar la parte de
W> hostname cuando se realiza un `toString`, `equals` o `hashCode`.
W>
W> Además de ser poco cuerdo, y **muy, muy** lento, estos métodos pueden lanzar
W> excepciones I/O (y no son *puras*), y pueden cambiar dependiendo de la
W> configuración de red (no son *deterministas*).
W>
W> El tipo refinado `String Refined Url` nos permite realizar chequeos de
W> igualdad basándose en la `String` y podríamos construir de manera segura una
W> `URL` únicamente si una API antigua la requiere.
W>
W> Dicho esto, en código de alto rendimiento preferiríamos evitar `java.net.URL`
W> por completo y usar un parseador de URL tal como
W> [jurl](https://github.com/anthonynsimon/jurl), porque incluso las partes
W> seguras de `java.net.*` son extremadamente lentas a gran escala.

### Funcionalidad

Es necesario serializar las clases de datos que definimos en la sección previa
en JSON, URL, y las formas codificadas POST. Dado que esto requiere de
polimorfismo, necesitaremos typeclasses.

[`jsonformat`](https://github.com/scalaz/scalaz-deriving/tree/master/examples/jsonformat/src)
es una librería JSON simple que estudiaremos en más detalle en un capítulo
posterior, dado que ha sido escrita con principios de PF y facilidad de lectura
como sus objetivos de diseño primario. Consiste de una AST JSON y typeclasses de
codificadores/decodificadores:

```scala
  package jsonformat

  sealed abstract class JsValue
  final case object JsNull                                    extends JsValue
  final case class JsObject(fields: IList[(String, JsValue)]) extends JsValue
  final case class JsArray(elements: IList[JsValue])          extends JsValue
  final case class JsBoolean(value: Boolean)                  extends JsValue
  final case class JsString(value: String)                    extends JsValue
  final case class JsDouble(value: Double)                    extends JsValue
  final case class JsInteger(value: Long)                     extends JsValue

  @typeclass trait JsEncoder[A] {
    def toJson(obj: A): JsValue
  }

  @typeclass trait JsDecoder[A] {
    def fromJson(json: JsValue): String \/ A
  }
```

A> `\/`es el `Either` de Scalaz y tiene un `.flatMap`. La podemos usar en `for`
A> comprehensions, mientras que el `Either` de la stdlib no soporta `.flatMap`
A> antes de Scala 2.12. Se pronuncia como *disyunción* o *conejo enojado*.
A>
A> `scala.Either` [fue una contribución a la librería estándar de
A> Scala](https://issues.scala-lang.org/browse/SI-250) del creador de Scalaz,
A> Tony Morris, en el 2007. `\/` se creó cuando se agregaron métodos inseguros a
A> `Either`.

Necesitamos instancias de `JsDecoder[AccessResponse]` y
`JsDecoder[RefreshResponse]`. Podemos hacer esto mediante el uso de una función
auxiliar:

```scala
  implicit class JsValueOps(j: JsValue) {
    def getAs[A: JsDecoder](key: String): String \/ A = ...
  }
```

Ponemos las instancias de los companions en nuestros tipos de datos, de modo que
siempre estén en el alcance/ámbito implícito:

```scala
  import jsonformat._, JsDecoder.ops._

  object AccessResponse {
    implicit val json: JsDecoder[AccessResponse] = j =>
      for {
        acc <- j.getAs[String]("access_token")
        tpe <- j.getAs[String]("token_type")
        exp <- j.getAs[Long]("expires_in")
        ref <- j.getAs[String]("refresh_token")
      } yield AccessResponse(acc, tpe, exp, ref)
  }

  object RefreshResponse {
    implicit val json: JsDecoder[RefreshResponse] = j =>
      for {
        acc <- j.getAs[String]("access_token")
        tpe <- j.getAs[String]("token_type")
        exp <- j.getAs[Long]("expires_in")
      } yield RefreshResponse(acc, tpe, exp)
  }
```

Podemos entonces parsear una cadena en un `AccessResponse` o una
`RefreshResponse`

```scala
  scala> import jsonformat._, JsDecoder.ops._
  scala> val json = JsParser("""
                       {
                         "access_token": "BEARER_TOKEN",
                         "token_type": "Bearer",
                         "expires_in": 3600,
                         "refresh_token": "REFRESH_TOKEN"
                       }
                       """)

  scala> json.map(_.as[AccessResponse])
  AccessResponse(BEARER_TOKEN,Bearer,3600,REFRESH_TOKEN)
```

Es necesario escribir nuestra propia typeclass para codificación de URL y POST.
El siguiente fragmento de código es un diseño razonable:

```scala
  // URL query key=value pairs, in un-encoded form.
  final case class UrlQuery(params: List[(String, String)])

  @typeclass trait UrlQueryWriter[A] {
    def toUrlQuery(a: A): UrlQuery
  }

  @typeclass trait UrlEncodedWriter[A] {
    def toUrlEncoded(a: A): String Refined UrlEncoded
  }
```

Es necesario proporcionar instancias de una typeclass para los tipos básicos:

```scala
  import java.net.URLEncoder

  object UrlEncodedWriter {
    implicit val encoded: UrlEncodedWriter[String Refined UrlEncoded] = identity

    implicit val string: UrlEncodedWriter[String] =
      (s => Refined.unsafeApply(URLEncoder.encode(s, "UTF-8")))

    implicit val long: UrlEncodedWriter[Long] =
      (s => Refined.unsafeApply(s.toString))

    implicit def ilist[K: UrlEncodedWriter, V: UrlEncodedWriter]
      : UrlEncodedWriter[IList[(K, V)]] = { m =>
      val raw = m.map {
        case (k, v) => k.toUrlEncoded.value + "=" + v.toUrlEncoded.value
      }.intercalate("&")
      Refined.unsafeApply(raw) // by deduction
    }

  }
```

Usamos `Refined.unsafeApply` cuando podemos deducir lógicamente que el contenido
de una cadena ya está codificado como una url, dejando de hacer verificaciones
adicionales.

`ilist` es un ejemplo de derivación de typeclass simple, así como derivamos
`Numeric[Complex]` de la representación numérica subyacente. El método
`.intercalate` es como `.mkString` pero más general.

A> `UrlEncodedWriter` está haciendo uso de los tipos SAM (*Single Abstract
A> Method*) del lenguaje Scala. La forma completa de lo anterior es
A>
A> ```scala
A>   implicit val string: UrlEncodedWriter[String] =
A>     new UrlEncodedWriter[String] {
A>       override def toUrlEncoded(s: String): String = ...
A>     }
A> ```
A>
A> Cuando el compilador de Scala espera una clase (que tiene un único método
A> abstracto) pero recibe una lambda, este proporciona el código repetitivo
A> automáticamente.
A>
A> Antes de los tipos SAM, un patrón común era definir un nombre de método
A> `instance` en el companion del typeclass.
A>
A> ```scala
A>   def instance[T](f: T => String): UrlEncodedWriter[T] =
A>     new UrlEncodedWriter[T] {
A>       override def toUrlEncoded(t: T): String = f(t)
A>     }
A> ```
A>
A> permitiendo
A>
A> ```scala
A>   implicit val string: UrlEncodedWriter[String] = instance { s => ... }
A> ```
A>
A> Este patrón todavía es usado en código que debe soportar versiones más viejas
A> de Scala, o para instancias de typeclasses que necesitan proporcionar más de
A> un método.
A>
A> Note que hay muchos *bugs* alrededor de los tipos SAM, porque no interactúan
A> con todas las características del lenguaje. Revierta a las variantes que no
A> usan tipos SAM si encuentra fallas extrañas en la compilación.

En un capítulo dedicado a la *derivación de typeclasses* calcularemos instancias
de `UrlQueryWriter` automáticamente, y también limpiaremos lo que ya hemos
escrito, pero por ahora escribiremos el código repetitivo para los tipos que
deseemos convertir:

```scala
  import UrlEncodedWriter.ops._
  object AuthRequest {
    implicit val query: UrlQueryWriter[AuthRequest] = { a =>
      UriQuery(List(
        ("redirect_uri"  -> a.redirect_uri.value),
        ("scope"         -> a.scope),
        ("client_id"     -> a.client_id),
        ("prompt"        -> a.prompt),
        ("response_type" -> a.response_type),
        ("access_type"   -> a.access_type))
    }
  }
  object AccessRequest {
    implicit val encoded: UrlEncodedWriter[AccessRequest] = { a =>
      List(
        "code"          -> a.code.toUrlEncoded,
        "redirect_uri"  -> a.redirect_uri.toUrlEncoded,
        "client_id"     -> a.client_id.toUrlEncoded,
        "client_secret" -> a.client_secret.toUrlEncoded,
        "scope"         -> a.scope.toUrlEncoded,
        "grant_type"    -> a.grant_type.toUrlEncoded
      ).toUrlEncoded
    }
  }
  object RefreshRequest {
    implicit val encoded: UrlEncodedWriter[RefreshRequest] = { r =>
      List(
        "client_secret" -> r.client_secret.toUrlEncoded,
        "refresh_token" -> r.refresh_token.toUrlEncoded,
        "client_id"     -> r.client_id.toUrlEncoded,
        "grant_type"    -> r.grant_type.toUrlEncoded
      ).toUrlEncoded
    }
  }
```

### Módulo

Esto concluye con el modelado de los datos y funcionalidad requeridos para
implementar OAuth2. Recuerde del capítulo anterior que definimos componentes que
necesitan interactuar con el mundo como álgebras, y debemos definir la lógica de
negocio en un módulo, de modo que pueda ser probada por completo.

Definimos nuestra dependencia de las álgebras, y usamos los límites de contexto
para mostrar que nuestras respuestas deben tener un `JsDecoder` y nuestro
payload `POST` debe tener un `UrlEncodedWriter`:

```scala
  trait JsonClient[F[_]] {
    def get[A: JsDecoder](
      uri: String Refined Url,
      headers: IList[(String, String)]
    ): F[A]

    def post[P: UrlEncodedWriter, A: JsDecoder](
      uri: String Refined Url,
      payload: P,
      headers: IList[(String, String]
    ): F[A]
  }
```

Note que nosotros únicamente definimos el camino fácil en la API `JsonClient`.
Veremos como lidiar con los errores en un capítulo posterior.

Obtener un `CodeToken` de un servidor `OAuth2` de Google envuelve

1. iniciar un servidor HTTP en la máquina local, y obtener su número de puerto.
2. hacer que el usuario abra una página web en su navegador, lo que les permite
   identificarse con sus credenciales de Google y autorizar la aplicación, con
   una redirección de vuelta a la máquina local.
3. capturar el código, informando al usuario de los siguientes pasos, y cerrar
   el servidor HTTP.

Podemos modelar esto con tres métodos en una álgebra `UserInteraction`

```scala
  final case class CodeToken(token: String, redirect_uri: String Refined Url)

  trait UserInteraction[F[_]] {
    def start: F[String Refined Url]
    def open(uri: String Refined Url): F[Unit]
    def stop: F[CodeToken]
  }
```

Casi suena fácil cuando lo escribimos de esta manera.

También requerimos de un álgebra para abstraer el sistema local de tiempo

```scala
  trait LocalClock[F[_]] {
    def now: F[Epoch]
  }
```

E introducimos los tipos de datos que usaremos en la lógica de refresco

```scala
  final case class ServerConfig(
    auth: String Refined Url,
    access: String Refined Url,
    refresh: String Refined Url,
    scope: String,
    clientId: String,
    clientSecret: String
  )
  final case class RefreshToken(token: String)
  final case class BearerToken(token: String, expires: Epoch)
```

y ahora podemos escribir un módulo cliente para OAuth2:

```scala
  import http.encoding.UrlQueryWriter.ops._

  class OAuth2Client[F[_]: Monad](
    config: ServerConfig
  )(
    user: UserInteraction[F],
    client: JsonClient[F],
    clock: LocalClock[F]
  ) {
    def authenticate: F[CodeToken] =
      for {
        callback <- user.start
        params   = AuthRequest(callback, config.scope, config.clientId)
        _        <- user.open(params.toUrlQuery.forUrl(config.auth))
        code     <- user.stop
      } yield code

    def access(code: CodeToken): F[(RefreshToken, BearerToken)] =
      for {
        request <- AccessRequest(code.token,
                                 code.redirect_uri,
                                 config.clientId,
                                 config.clientSecret).pure[F]
        msg     <- client.post[AccessRequest, AccessResponse](
                     config.access, request)
        time    <- clock.now
        expires = time + msg.expires_in.seconds
        refresh = RefreshToken(msg.refresh_token)
        bearer  = BearerToken(msg.access_token, expires)
      } yield (refresh, bearer)

    def bearer(refresh: RefreshToken): F[BearerToken] =
      for {
        request <- RefreshRequest(config.clientSecret,
                                  refresh.token,
                                  config.clientId).pure[F]
        msg     <- client.post[RefreshRequest, RefreshResponse](
                     config.refresh, request)
        time    <- clock.now
        expires = time + msg.expires_in.seconds
        bearer  = BearerToken(msg.access_token, expires)
      } yield bearer
  }
```

## Resumen

- Los ADTs (*tipos de datos algebraicos*) están definidos como *productos*
  (`final case class`) y *coproductos* (`sealed abstract class`).
- Los tipos `refined` hacen cumplir las invariantes/restricciones sobre los
  valores.
- Las funciones concretas pueden definirse en una clase implícita, para mantener
  el flujo de izquierda a derecha.
- Las funciones polimórficas están definidas en *typeclasses*. La funcionalidad
  se proporciona por medio de *límites de contexto* ("has a"), más bién que por
  medio de jerarquías de clases.
- Las *instancias* de typeclasses son implementaciones de una typeclass.
- `@simulacrum.typeclass` genera `.ops` en el companion, proporcionando sintaxis
  conveniente para las funciones de la typeclass.
- La *derivación de typeclasses* es una composición en tiempo de compilación de
  instancias de typeclass.
