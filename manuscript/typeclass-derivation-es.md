# Derivación de typeclasses

Las typeclasses proporcionan funcionalidad polimórfica a nuestras aplicaciones.
Pero para usar una typeclass necesitamos instancias para nuestros objetos del
dominio de negocios.

La creación de una instancia de typeclass a partir de instancias existentes se
conoce como *derivación de typeclasses* y es el tema de este capítulo.

Existen cuatro enfoques para la derivación de typeclasses:

1. Instancias manuales para cada objeto del dominio. Esto no es factible para
   aplicaciones del mundo real porque requiere cientos de lineas de código par
   cada línea de una `case class`. Es útil sólo para propósitos educacionales y
   optimización de rendimiento a la medida.

2. Abstraer sobre la typeclass por una typeclass de Scalaz existente. Este es el
   enfoque usado por `scalaz-deriving`, produciendo pruebas automáticas y
   derivaciones para productos y coproductos.

3. Macros. Sin embargo, la escritura de macros para cada typeclass requiere de
   un desarrollador avanzado y experimentado. Felizmente, la librería
   [Magnolia](https://github.com/propensive/magnolia) de Jon Pretty abstrae
   sobre macros escritas a mano con una API simple, centralizando la interacción
   compleja con el compilador.

4. Escribir un programa genérico usando la librería
   [Shapeless](https://github.com/milessabin/shapeless). El mecanismo `implicit`
   es un lenguaje dentro del lenguaje Scala y puede ser usado para escribir
   programas a nivel de tipo.

En este capítulo estudiaremos typeclasses con complejidad creciente y sus
derivaciones. Empezaremos con `scalaz-deriving` como el mecanismo con más
principios, repitiendo algunas lecciones del Capítulo 5 "Typeclasses de Scalaz",
y después con Magnolia (el más fácil de usar), terminando con Shapeless (el más
poderoso) para las typeclasses con lógica de derivación compleja.

## Ejemplos

Este capítulo mostrará cómo definir derivaciones para cinco typeclasses específicas. Cada ejemplo exhibe una característica que puede ser generalizada:

```scala
  @typeclass trait Equal[A]  {
    // type parameter is in contravariant (parameter) position
    @op("===") def equal(a1: A, a2: A): Boolean
  }

  // for requesting default values of a type when testing
  @typeclass trait Default[A] {
    // type parameter is in covariant (return) position
    def default: String \/ A
  }

  @typeclass trait Semigroup[A] {
    // type parameter is in both covariant and contravariant position (invariant)
    @op("|+|") def append(x: A, y: =>A): A
  }

  @typeclass trait JsEncoder[T] {
    // type parameter is in contravariant position and needs access to field names
    def toJson(t: T): JsValue
  }

  @typeclass trait JsDecoder[T] {
    // type parameter is in covariant position and needs access to field names
    def fromJson(j: JsValue): String \/ T
  }
```

A> Existe una escuela de pensamiento que dice que los formatos de serialización,
A> tales como JSON y XML, **no** deberían tener codificadores/decodificadores de
A> typeclasses, porque esto puede llevar a incoherencia de typeclasses. (es
A> decir más de un codificador/decodificador puede existir para el mismo tipo).
A> La alternativa es usar álgebras y evitar el uso de la característica
A> `implicit` del lenguaje por completo.
A>
A> Aunque es posible aplicar las técnicas en este capítulo para la derivación de
A> typeclasses o álgebras, esta última requiere mucho más código repetitivo. Por
A> lo tanto hemos decidido conscientemente restringir nuestro estudio a los
A> codificadores/decodificadores que tengan coherencia. Como veremos más tarde
A> en este capítulo,la derivación automática en el sitio de uso con Magnolia y
A> Shapeless,combinada con las limitaciones del compilador durante la búsqueda
A> de implícitos, nos lleva comúnmente a la incoherencia de typeclasses.

## `scalaz-deriving`

La librería `scalaz-deriving` es una extensión de Scalaz y puede agregarse al
`build.sbt` con

```scala
  val derivingVersion = "1.0.0"
  libraryDependencies += "org.scalaz" %% "scalaz-deriving" % derivingVersion
```

proporcionando nuevas typeclasses, mostradas abajo con relación a las typeclasses de Scalaz:

{width=60%}
![](images/scalaz-deriving-base.png)

A> En Scalaz 7.3, `Applicative` y `Divisible` heredarán de `InvariantApplicative`

Antes de proceder, aquí tenemos una rápida recapitulación de las typeclasses
centrales de Scalaz:

```scala
  @typeclass trait InvariantFunctor[F[_]] {
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B]
  }

  @typeclass trait Contravariant[F[_]] extends InvariantFunctor[F] {
    def contramap[A, B](fa: F[A])(f: B => A): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = contramap(fa)(g)
  }

  @typeclass trait Divisible[F[_]] extends Contravariant[F] {
    def conquer[A]: F[A]
    def divide2[A, B, C](fa: F[A], fb: F[B])(f: C => (A, B)): F[C]
    ...
    def divide22[...] = ...
  }

  @typeclass trait Functor[F[_]] extends InvariantFunctor[F] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = map(fa)(f)
  }

  @typeclass trait Applicative[F[_]] extends Functor[F] {
    def point[A](a: =>A): F[A]
    def apply2[A,B,C](fa: =>F[A], fb: =>F[B])(f: (A, B) => C): F[C] = ...
    ...
    def apply12[...]
  }

  @typeclass trait Monad[F[_]] extends Functor[F] {
    @op(">>=") def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
  }
  @typeclass trait MonadError[F[_], E] extends Monad[F] {
    def raiseError[A](e: E): F[A]
    def emap[A, B](fa: F[A])(f: A => E \/ B): F[B] = ...
    ...
  }
```

### No repita (DRY)

La forma más simple de derivar una typeclass es reutilizar una que ya exista:

La typeclass `Equal` tiene una instancia de `Contravariant[Equal]`,
proporcionando `.contramap`:

```scala
  object Equal {
    implicit val contravariant = new Contravariant[Equal] {
      def contramap[A, B](fa: Equal[A])(f: B => A): Equal[B] =
        (b1, b2) => fa.equal(f(b1), f(b2))
    }
    ...
  }
```

Como usuarios de `Equal`, podemos usar `.contramap` para nuestros parámetros de
tipo únicos. Recuerde que las instancias de typeclasses van en los compañeros de
tipos de datos para que estén en el alcance implícito:

```scala
  final case class Foo(s: String)
  object Foo {
    implicit val equal: Equal[Foo] = Equal[String].contramap(_.s)
  }

  scala> Foo("hello") === Foo("world")
  false
```

Sin embargo, no todas las typeclasses tienen una instancia de `Contravariant`.
En particular, las typeclasses con parámetros de tipo en posición covariante
podrían más bien tener un `Functor`.

```scala
  object Default {
    def instance[A](d: =>String \/ A) = new Default[A] { def default = d }
    implicit val string: Default[String] = instance("".right)

    implicit val functor: Functor[Default] = new Functor[Default] {
      def map[A, B](fa: Default[A])(f: A => B): Default[B] = instance(fa.default.map(f))
    }
    ...
  }
```

Ahora podríamos derivar un `Default[Foo]`

```scala
  object Foo {
    implicit val default: Default[Foo] = Default[String].map(Foo(_))
    ...
  }
```

Si una typeclass tiene parámetros en posiciones tanto covariantes como
contravariantes, como es el caso con `Semigroup`, podría proporcionar un
`InvariantFunctor`

```scala
  object Semigroup {
    implicit val invariant = new InvariantFunctor[Semigroup] {
      def xmap[A, B](ma: Semigroup[A], f: A => B, g: B => A) = new Semigroup[B] {
        def append(x: B, y: =>B): B = f(ma.append(g(x), g(y)))
      }
    }
    ...
  }
```

y podemos invocar `.xmap`

```scala
  object Foo {
    implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
    ...
  }
```

Generalmente, es más simple usar `.xmap` en lugar de `.map` o `.contramap`:

```scala
  final case class Foo(s: String)
  object Foo {
    implicit val equal: Equal[Foo]         = Equal[String].xmap(Foo(_), _.s)
    implicit val default: Default[Foo]     = Default[String].xmap(Foo(_), _.s)
    implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
  }
```

A> La anotación `@xderiving` automáticamente inserta código repetitivo `.xmap`.
A> Agregue lo siguiente a `build.sbt`
A>
A> {lang="text"}
A> ~~~~~~~~
A>   addCompilerPlugin("org.scalaz" %% "deriving-plugin" % derivingVersion)
A>   libraryDependencies += "org.scalaz" %% "deriving-macro" % derivingVersion % "provided"
A> ~~~~~~~~
A>
A> y úselo de la siguiente manera
A>
A> {lang="text"}
A> ~~~~~~~~
A>   @xderiving(Equal, Default, Semigroup)
A>   final case class Foo(s: String)
A> ~~~~~~~~

### `MonadError`

Típicamente, las cosas que *escriben* a partir de un valor polimórfico tienen
una instancia de `Contravariant`, y las cosas que *leen* a un valor polimórfico
tienen un `Functor`. Sin embargo, es bastante posible que la lectura pueda
fallar. Por ejemplo, si tenemos una `String` por defecto no significa que
podamos derivar una `String Refined NonEmpty` a partir de esta

```scala
  import eu.timepit.refined.refineV
  import eu.timepit.refined.api._
  import eu.timepit.refined.collection._

  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].map(refineV[NonEmpty](_))
```

falla al compilar con

```text
  [error] default.scala:41:32: polymorphic expression cannot be instantiated to expected type;
  [error]  found   : Either[String, String Refined NonEmpty]
  [error]  required: String Refined NonEmpty
  [error]     Default[String].map(refineV[NonEmpty](_))
  [error]                                          ^
```

Recuerde del Capítulo 4.1 que `refineV` devuelve un `Either`, como nos ha
recordado el compilador.

Como el autor de la typeclass de `Default`, podríamos lograr algo mejor que
`Functor` y proporcionar un `MonadError[Default, String]`:

```scala
  implicit val monad = new MonadError[Default, String] {
    def point[A](a: =>A): Default[A] =
      instance(a.right)
    def bind[A, B](fa: Default[A])(f: A => Default[B]): Default[B] =
      instance((fa >>= f).default)
    def handleError[A](fa: Default[A])(f: String => Default[A]): Default[A] =
      instance(fa.default.handleError(e => f(e).default))
    def raiseError[A](e: String): Default[A] =
      instance(e.left)
  }
```

Ahora tenemos acceso a la sintaxis `.emap` y podemos derivar nuestro tipo refinado

```scala
  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].emap(refineV[NonEmpty](_).disjunction)
```

De hecho, podemos proporcionar una regla de derivación para todos los tipos refinados

```scala
  implicit def refined[A: Default, P](
    implicit V: Validate[A, P]
  ): Default[A Refined P] = Default[A].emap(refineV[P](_).disjunction)
```

donde `Validate` es de la librería refined y es requerido por `refineV`.

A> La extensión `refined-scalaz` de `refined` proporciona soporte para derivar
A> automáticamente todas las typeclasses para tipos refinados con el siguiente
A> import
A>
A> {lang="text"}
A> ~~~~~~~~
A>   import eu.timepit.refined.scalaz._
A> ~~~~~~~~
A>
A> Sin embargo, debido a [limitaciones del compilador de
A> Scala](https://github.com/scala/bug/issues/10753) raramente funciona en la
A> práctica, y debemos escribir derivaciones `implicit def refined` para cada
A> typeclass.

De manera similar podemos usar `.emap` para derivar un decodificador `Int` a partir de un `Long`, con protección alrededor del método no total `.toInt` de la librería estándar.

```scala
  implicit val long: Default[Long] = instance(0L.right)
  implicit val int: Default[Int] = Default[Long].emap {
    case n if (Int.MinValue <= n && n <= Int.MaxValue) => n.toInt.right
    case big => s"$big does not fit into 32 bits".left
  }
```

Como autores de la typeclass `Default`, podríamos reconsiderar nuestro diseño de la API de modo que nunca pueda fallar, por ejemplo con la siguiente firma de tipo

```scala
  @typeclass trait Default[A] {
    def default: A
  }
```

No seríamos capaces de definir un `MonadError`, forzándonos a proporcionar
instancias que nunca fallen. Esto resultará en más código repetitivo pero
ganaremos en seguridad en tiempo de compilación. Sin embargo, continuaremos con
`String \/ A` como el retorno de tipo dado que es un ejemplo más general.

### `.fromIso`

Todas las typeclasses en Scalaz tienen un método en su objeto compañero con una
firma similar a la siguiente:

```scala
  object Equal {
    def fromIso[F, G: Equal](D: F <=> G): Equal[F] = ...
    ...
  }

  object Monad {
    def fromIso[F[_], G[_]: Monad](D: F <~> G): Monad[F] = ...
    ...
  }
```

Estas significan que si tenemos un tipo `F`, y una forma de convertirlo en una
`G` que tenga una instancia, entonces podemos llamar `Equal.fromIso` para tener
una instancia para `F`.

Por ejemplo, como usuarios de la typeclass, si tenemos un tipo de datos `Bar` podemos definir un isomorfismo a `(String, Int)`

```scala
  import Isomorphism._

  final case class Bar(s: String, i: Int)
  object Bar {
    val iso: Bar <=> (String, Int) = IsoSet(b => (b.s, b.i), t => Bar(t._1, t._2))
  }
```

y entonces derivar `Equal[Bar]` porque ya existe un `Equal` para todas las tuplas:

```scala
  object Bar {
    ...
    implicit val equal: Equal[Bar] = Equal.fromIso(iso)
  }
```

El mecanismo `.fromIso` también puede ayudarnos como autores de typeclasses.
Considere `Default` que tiene una firma de tipo `Unit => F[A]`. Nuestro método
`default` es de hecho isomórfico a `Kleisli[F, Unit, A]`, el transformador de
mónadas `ReaderT`.

Dado que `Kleisli` ya proporciona un `MonadError` (si `F` tiene una), podemos
derivar `MonadError[Default, String]` al crear un isomorfismo entre `Default` y
`Kleisli`:

```scala
  private type Sig[a] = Unit => String \/ a
  private val iso = Kleisli.iso(
    λ[Sig ~> Default](s => instance(s(()))),
    λ[Default ~> Sig](d => _ => d.default)
  )
  implicit val monad: MonadError[Default, String] = MonadError.fromIso(iso)
```

proporcionándonos `.map`, `.xmap` y `.emap` que hemos estado usando hasta el
momento, efectivamente de manera gratuita.

### `Divisible` y `Applicative`

Para derivar `Equal` para nuestra case class con dos parámetros, reutilizamos la
instancia que Scalaz proporcionó para las tuplas. Pero, ¿de dónde vino la
instancia para las tuplas?

Una typeclass más específica que `Contravariant` es `Divisible`. `Equal` tiene
una instancia:

```scala
  implicit val divisible = new Divisible[Equal] {
    ...
    def divide[A1, A2, Z](a1: =>Equal[A1], a2: =>Equal[A2])(
      f: Z => (A1, A2)
    ): Equal[Z] = { (z1, z2) =>
      val (s1, s2) = f(z1)
      val (t1, t2) = f(z2)
      a1.equal(s1, t1) && a2.equal(s2, t2)
    }
    def conquer[A]: Equal[A] = (_, _) => true
  }
```

A> Cuando implementamos `Divisible` el compilador requerirá que proporcionemos
A> `.contramap`, lo que podemos lograr directamente con una implementación
A> optimizada o con el siguiente combinador derivado:
A>
A> {lang="text"}
A> ~~~~~~~~
A>   override def contramap[A, B](fa: F[A])(f: B => A): F[B] =
A>     divide2(conquer[Unit], fa)(c => ((), f(c)))
A> ~~~~~~~~
A>
A> Este ha sido agregado a `Divisible` en Scala 7.3.

Y a partir de `divide2`, `Divisible` es capaz de construir derivaciones hasta `divide22`. Podemos llamar a estos métodos directamente para nuestros tipos de datos:

```scala
  final case class Bar(s: String, i: Int)
  object Bar {
    implicit val equal: Equal[Bar] =
      Divisible[Equal].divide2(Equal[String], Equal[Int])(b => (b.s, b.i))
  }
```

El equivalente para los parámetros de tipo en posición covariante es `Applicative`:

```scala
  object Bar {
    ...
    implicit val default: Default[Bar] =
      Applicative[Default].apply2(Default[String], Default[Int])(Bar(_, _))
  }
```

Pero debemos ser cuidadosos para no violar las leyes de las typeclasses cuando
implementemos `Divisible` o `Applicative`. En particular, es fácil violar la
*ley de composición* que dice que los siguientes dos caminos de código deben
resultar exactamente en la misma salida:

- `divide2(divide2(a1, a2)(dupe), a3)(dupe)`
- `divide2(a1, divide2(a2, a3)(dupe))(dupe)`
- para cualquier `dupe: A => (A, A)`

con leyes similares para `Applicative`.

Considere `JsEncoder` y la instancia propuesta para `Divisible`

```scala
  new Divisible[JsEncoder] {
    ...
    def divide[A, B, C](fa: JsEncoder[A], fb: JsEncoder[B])(
      f: C => (A, B)
    ): JsEncoder[C] = { c =>
      val (a, b) = f(c)
      JsArray(IList(fa.toJson(a), fb.toJson(b)))
    }

    def conquer[A]: JsEncoder[A] = _ => JsNull
  }
```

De un lado de las leyes de composición, para una entrada de tipo `String`, tenemos

```scala
  JsArray([JsArray([JsString(hello),JsString(hello)]),JsString(hello)])
```

y por el otro

```scala
  JsArray([JsString(hello),JsArray([JsString(hello),JsString(hello)])])
```

que son diferentes. Podríamos experimentar con variaciones de la implementación
`divide`, pero nunca satisfará las leyes para todas las entradas.

Por lo tanto no podemos proporcionar un `Divisible[JsEncoder]` porque violaría
las leyes matemáticas e invalidaría todas las suposiciones de las que dependen
los usuarios de `Divisible`.

Para ayudar en la prueba de leyes, las typeclasses de Scalaz contienen versiones
codificadas de sus leyes en la typeclass misma. Podemos escribir una prueba
automática, verificando que la ley falla, para recordarnos de este hecho:

```scala
  val D: Divisible[JsEncoder] = ...
  val S: JsEncoder[String] = JsEncoder[String]
  val E: Equal[JsEncoder[String]] = (p1, p2) => p1.toJson("hello") === p2.toJson("hello")
  assert(!D.divideLaw.composition(S, S, S)(E))
```

Por otro lado, una prueba similar de `JsDecoder` cumple las leyes de composición de `Applicative`

```scala
  final case class Comp(a: String, b: Int)
  object Comp {
    implicit val equal: Equal[Comp] = ...
    implicit val decoder: JsDecoder[Comp] = ...
  }

  def composeTest(j: JsValue) = {
    val A: Applicative[JsDecoder] = Applicative[JsDecoder]
    val fa: JsDecoder[Comp] = JsDecoder[Comp]
    val fab: JsDecoder[Comp => (String, Int)] = A.point(c => (c.a, c.b))
    val fbc: JsDecoder[((String, Int)) => (Int, String)] = A.point(_.swap)
    val E: Equal[JsDecoder[(Int, String)]] = (p1, p2) => p1.fromJson(j) === p2.fromJson(j)
    assert(A.applyLaw.composition(fbc, fab, fa)(E))
  }
```

para un poco de valores de prueba

```scala
  composeTest(JsObject(IList("a" -> JsString("hello"), "b" -> JsInteger(1))))
  composeTest(JsNull)
  composeTest(JsObject(IList("a" -> JsString("hello"))))
  composeTest(JsObject(IList("b" -> JsInteger(1))))
```

Ahora estamos razonablemente seguros de que nuestra `MonadError` cumple con las leyes.

Sin embargo, simplemente porque tenemos una prueba que pasa para un conjunto
pequeño de datos no prueba que las leyes son satisfechas. También debemos
razonar durante la implementación para convencernos a nosotros mismos que de
**debería** satisfacer las leyes, e intentar proponer casos especiales donde
podría fallar.

Una forma de generar una amplia variedad de datos de prueba es usar la librería
[scalacheck](https://github.com/rickynils/scalacheck), que proporciona una
typeclass `Arbitrary` que se integra con la mayoría de los frameworks de prueba
para repetir una prueba con datos generados aleatoriamente.

La librería `jsonformat` proporciona una `Arbitrary[JsValue]` (¡todos deberían
proporcionar una instancia de `Arbitrary` para sus ADTs!) permitiéndonos hacer
uso de la característica `forAll` de Scalatest:

```scala
  forAll(SizeRange(10))((j: JsValue) => composeTest(j))
```

Esta prueba nos proporciona aún más confianza de que nuestra typeclass cumple
con las leyes de composición de `Applicative`. Al verificar todas las leyes en
`Divisible` y `MonadError` también tenemos **muchas** pruebas de humo de manera
gratuita.

A> Debemos restringir `forAll` de modo que tenga un `SizeRange` de `10`, lo que
A> limita tanto `JsObject` y `JsArray` a un tamaño máximo de 10 elementos. Esto
A> evita sobreflujos de pila a medida que números más grandes pueden generar
A> documentos JSON gigantescos.

### `Decidable` y `Alt`

Donde `Divisible` y `Applicative` nos proporcionan derivaciones de typeclasses
para productos (construidos a partir de tuplas), `Decidable` y `Alt` nos dan
coproductos (construidos a partir de disyunciones anidadas):

```scala
  @typeclass trait Alt[F[_]] extends Applicative[F] with InvariantAlt[F] {
    def alt[A](a1: =>F[A], a2: =>F[A]): F[A]

    def altly1[Z, A1](a1: =>F[A1])(f: A1 => Z): F[Z] = ...
    def altly2[Z, A1, A2](a1: =>F[A1], a2: =>F[A2])(f: A1 \/ A2 => Z): F[Z] = ...
    def altly3 ...
    def altly4 ...
    ...
  }

  @typeclass trait Decidable[F[_]] extends Divisible[F] with InvariantAlt[F] {
    def choose1[Z, A1](a1: =>F[A1])(f: Z => A1): F[Z] = ...
    def choose2[Z, A1, A2](a1: =>F[A1], a2: =>F[A2])(f: Z => A1 \/ A2): F[Z] = ...
    def choose3 ...
    def choose4 ...
    ...
  }
```

Las cuatro typeclasses centrales tienen firmas simétricas:

| Typeclass     | método    | dado           | firma             | devuelve |
|---------------|-----------|----------------|-------------------|----------|
| `Applicative` | `apply2`  | `F[A1], F[A2]` | `(A1, A2) => Z`   | `F[Z]`   |
| `Alt`         | `altly2`  | `F[A1], F[A2]` | `(A1 \/ A2) => Z` | `F[Z]`   |
| `Divisible`   | `divide2` | `F[A1], F[A2]` | `Z => (A1, A2)`   | `F[Z]`   |
| `Decidable`   | `choose2` | `F[A1], F[A2]` | `Z => (A1 \/ A2)` | `F[Z]`   |

soportando productos covariantes; coproductos covariantes; productos
contravariantes; coproductos contravariantes.

Podemos escribir un `Decidable[Equal]`, ¡permitiéndonos derivar `Equal` para
cualquier ADT!

```scala
  implicit val decidable = new Decidable[Equal] {
    ...
    def choose2[Z, A1, A2](a1: =>Equal[A1], a2: =>Equal[A2])(
      f: Z => A1 \/ A2
    ): Equal[Z] = { (z1, z2) =>
      (f(z1), f(z2)) match {
        case (-\/(s), -\/(t)) => a1.equal(s, t)
        case (\/-(s), \/-(t)) => a2.equal(s, t)
        case _ => false
      }
    }
  }
```

Para un ADT

```scala
  sealed abstract class Darth { def widen: Darth = this }
  final case class Vader(s: String, i: Int)  extends Darth
  final case class JarJar(i: Int, s: String) extends Darth
```

donde los productos (`Vader` y `JarJar`) tienen un `Equal`

```scala
  object Vader {
    private val g: Vader => (String, Int) = d => (d.s, d.i)
    implicit val equal: Equal[Vader] = Divisible[Equal].divide2(Equal[String], Equal[Int])(g)
  }
  object JarJar {
    private val g: JarJar => (Int, String) = d => (d.i, d.s)
    implicit val equal: Equal[JarJar] = Divisible[Equal].divide2(Equal[Int], Equal[String])(g)
  }
```

podemos derivar la instancia de `Equal` para la ADT completa

```scala
  object Darth {
    private def g(t: Darth): Vader \/ JarJar = t match {
      case p @ Vader(_, _)  => -\/(p)
      case p @ JarJar(_, _) => \/-(p)
    }
    implicit val equal: Equal[Darth] = Decidable[Equal].choose2(Equal[Vader], Equal[JarJar])(g)
  }

  scala> Vader("hello", 1).widen === JarJar(1, "hello").widen
  false
```

A> Scalaz 7.2 no proporciona un `Decidable[Equal]` desde el inicio, dado que se
A> trata de una adición tardía.

Las typeclasses que tienen un `Applicative` pueden ser elegibles para `Alt`. Si
deseamos usar nuestro truco con `Klick.iso`, tenemos que extender
`IsomorphismMonadError` y hacer un mixin en `Alt`. Actualice nuestro
`MonadError[Default, String]` para tener un `Alt[Default]`:

```scala
  private type K[a] = Kleisli[String \/ ?, Unit, a]
  implicit val monad = new IsomorphismMonadError[Default, K, String] with Alt[Default] {
    override val G = MonadError[K, String]
    override val iso = ...

    def alt[A](a1: =>Default[A], a2: =>Default[A]): Default[A] = instance(a1.default)
  }
```

A> La primitiva de `Alt` es `alt`, tal como la primitiva de `Applicative` es
A> `ap`, pero con frecuencia tiene más sentido usar `altly2` y `apply2` como las
A> primitivas usando los siguientes overrides:
A>
A> {lang="text"}
A> ~~~~~~~~
A>   override def ap[A, B](fa: =>F[A])(f: =>F[A => B]): F[B] =
A>     apply2(fa, f)((a, abc) => abc(a))
A>
A>   override def alt[A](a1: =>F[A], a2: =>F[A]): F[A] = altly2(a1, a2) {
A>     case -\/(a) => a
A>     case \/-(a) => a
A>   }
A> ~~~~~~~~
A>
A> Sólo no olvide implementar `apply2` y `altly2` o existirá un bucle infinito
A> en tiempo de ejecución

Permitiéndonos derivar nuestro `Default[Darth]`

```scala
object Darth {
    ...
    private def f(e: Vader \/ JarJar): Darth = e.merge
    implicit val default: Default[Darth] =
      Alt[Default].altly2(Default[Vader], Default[JarJar])(f)
  }
  object Vader {
    ...
    private val f: (String, Int) => Vader = Vader(_, _)
    implicit val default: Default[Vader] =
      Alt[Default].apply2(Default[String], Default[Int])(f)
  }
  object JarJar {
    ...
    private val f: (Int, String) => JarJar = JarJar(_, _)
    implicit val default: Default[JarJar] =
      Alt[Default].apply2(Default[Int], Default[String])(f)
  }

  scala> Default[Darth].default
  \/-(Vader())
```

Regresando a las typeclasses de `scalaz-deriving`, los padres invariantes de
`Alt` y `Decidable` son:

```scala
  @typeclass trait InvariantApplicative[F[_]] extends InvariantFunctor[F] {
    def xproduct0[Z](f: =>Z): F[Z]
    def xproduct1[Z, A1](a1: =>F[A1])(f: A1 => Z, g: Z => A1): F[Z] = ...
    def xproduct2 ...
    def xproduct3 ...
    def xproduct4 ...
  }

  @typeclass trait InvariantAlt[F[_]] extends InvariantApplicative[F] {
    def xcoproduct1[Z, A1](a1: =>F[A1])(f: A1 => Z, g: Z => A1): F[Z] = ...
    def xcoproduct2 ...
    def xcoproduct3 ...
    def xcoproduct4 ...
  }
```

soportando typeclasses con un `InvariantFunctor` como `Monoid` y `Semigroup`.

### Aridad arbitraria y `@deriving`

Existen dos problemas con `InvariantApplicative` e `InvariantAlt`:

1. Sólo soportan productos de cuatro campos y coproductos de cuatro entradas.
2. Existe *mucho* código repetitivo en el objeto compañero del tipo de datos.

En esta sección resolveremos ambos problemas con clases adicionales introducidas
por `scalaz-deriving`

{width=75%}
![](images/scalaz-deriving.png)

Efectivamente, nuestras typeclasses centrales `Applicative`, `Divisible`, `Alt`
y `Decidable` todas se pueden extender a aridades arbitrarias usando la librería
[iotaz](https://github.com/frees-io/iota), y por lo tanto el postfijo `z`.

La librería  iotaz tiene tres tipos principales:

- `TList` que describe cadenas de tipos con longitudes arbitrarias
- `Prod[A <: TList]` para productos
- `Cop[A<: TList]` para coproductos

A modo de ejemplo, una representación `TList` de `Darth` de la sección previa es

```scala
  import iotaz._, TList._

  type DarthT  = Vader  :: JarJar :: TNil
  type VaderT  = String :: Int    :: TNil
  type JarJarT = Int    :: String :: TNil
```

que puede ser instanciada como:

```scala
  val vader: Prod[VaderT]    = Prod("hello", 1)
  val jarjar: Prod[JarJarT]  = Prod(1, "hello")

  val VaderI = Cop.Inject[Vader, Cop[DarthT]]
  val darth: Cop[DarthT] = VaderI.inj(Vader("hello", 1))
```

Para ser capaces de usar la API de `scalaz-deriving`, necesitamos un
`Isomorfismo` entre nuestras ADTs y la representación genérica de `iotaz`. Es
mucho código repetitivo, y volveremos a esto en un momento:

```scala
  object Darth {
    private type Repr   = Vader :: JarJar :: TNil
    private val VaderI  = Cop.Inject[Vader, Cop[Repr]]
    private val JarJarI = Cop.Inject[JarJar, Cop[Repr]]
    private val iso     = IsoSet(
      {
        case d: Vader  => VaderI.inj(d)
        case d: JarJar => JarJarI.inj(d)
      }, {
        case VaderI(d)  => d
        case JarJarI(d) => d
      }
    )
    ...
  }

  object Vader {
    private type Repr = String :: Int :: TNil
    private val iso   = IsoSet(
      d => Prod(d.s, d.i),
      p => Vader(p.head, p.tail.head)
    )
    ...
  }

  object JarJar {
    private type Repr = Int :: String :: TNil
    private val iso   = IsoSet(
      d => Prod(d.i, d.s),
      p => JarJar(p.head, p.tail.head)
    )
    ...
  }
```

Con esto fuera del camino podemos llamar la API `Deriving` para `Equal`, posible
porque `scalaz-deriving` proporciona una instancia optimizada de
`Deriving[Equal]`

```scala
  object Darth {
    ...
    implicit val equal: Equal[Darth] = Deriving[Equal].xcoproductz(
      Prod(Need(Equal[Vader]), Need(Equal[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val equal: Equal[Vader] = Deriving[Equal].xproductz(
      Prod(Need(Equal[String]), Need(Equal[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val equal: Equal[JarJar] = Deriving[Equal].xproductz(
      Prod(Need(Equal[Int]), Need(Equal[String])))(iso.to, iso.from)
  }
```

A> Typeclasses en la API `Deriving` están envueltas en `Need` (recuerde `Name`
A> del Capítulo 6), que permite construcción perezosa, evitando trabajo
A> innecesario si la typeclass no se requiere, y evitando sobreflujos de la pila
A> para ADTs recursivas.

Para ser capaces de hacer lo mismo para nuestra typeclass `Default`, necesitamos
proporcionar una instancia de `Deriving[Default]`. Esto es solo un caso de
envolver nuestro `Alt` con un auxiliar:

```scala
  object Default {
    ...
    implicit val deriving: Deriving[Default] = ExtendedInvariantAlt(monad)
  }
```

y entonces invocarlo desde los objetos compañeros

```scala
  object Darth {
    ...
    implicit val default: Default[Darth] = Deriving[Default].xcoproductz(
      Prod(Need(Default[Vader]), Need(Default[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val default: Default[Vader] = Deriving[Default].xproductz(
      Prod(Need(Default[String]), Need(Default[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val default: Default[JarJar] = Deriving[Default].xproductz(
      Prod(Need(Default[Int]), Need(Default[String])))(iso.to, iso.from)
  }
```

Hemos resuelto el problema de aridad arbitraria, pero hemos introducido aún más
código repetitivo.

El punto clave es que la anotación `@deriving`, que viene del `deriving-plugin`,
genera todo este código repetitivo automáticamente y únicamente requiere ser
aplicado en el punto más alto de la jerarquía de una ADT:

```scala
  @deriving(Equal, Default)
  sealed abstract class Darth { def widen: Darth = this }
  final case class Vader(s: String, i: Int)  extends Darth
  final case class JarJar(i: Int, s: String) extends Darth
```

También están incluidos en `scalaz-deriving` instancias para `Order`,
`Semigroup` y `Monoid`. Instancias para `Show` y `Arbitrary` están disponibles
al instalar las librerías extras `scalaz-deriving-magnolia` y
`scalaz-deriving-scalacheck`.

### Ejemplos

Finalizamos nuestro estudio de `scalaz-deriving` con implementaciones completamente revisadas de todas las typeclasses de ejemplo. Antes de hacer esto necesitamos conocer sobre un nuevo tipo de datos: `/~\`, es decir, la *serpiente en la pared*, para contener dos estructuras *higher kinded* que comparten el mismo parámetro de tipo:

```scala
Para ser capaces de usar la API de `scalaz-deriving`, necesitamos un
`Isomorfismo` entre nuestras ADTs y la representación genérica de `iotaz`. Es
mucho código repetitivo, y volveremos a esto en un momento:

```scala
  object Darth {
    private type Repr   = Vader :: JarJar :: TNil
    private val VaderI  = Cop.Inject[Vader, Cop[Repr]]
    private val JarJarI = Cop.Inject[JarJar, Cop[Repr]]
    private val iso     = IsoSet(
      {
        case d: Vader  => VaderI.inj(d)
        case d: JarJar => JarJarI.inj(d)
      }, {
        case VaderI(d)  => d
        case JarJarI(d) => d
      }
    )
    ...
  }

  object Vader {
    private type Repr = String :: Int :: TNil
    private val iso   = IsoSet(
      d => Prod(d.s, d.i),
      p => Vader(p.head, p.tail.head)
    )
    ...
  }

  object JarJar {
    private type Repr = Int :: String :: TNil
    private val iso   = IsoSet(
      d => Prod(d.i, d.s),
      p => JarJar(p.head, p.tail.head)
    )
    ...
  }
```

Con esto fuera del camino podemos llamar la API `Deriving` para `Equal`, posible
porque `scalaz-deriving` proporciona una instancia optimizada de
`Deriving[Equal]`

```scala
  object Darth {
    ...
    implicit val equal: Equal[Darth] = Deriving[Equal].xcoproductz(
      Prod(Need(Equal[Vader]), Need(Equal[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val equal: Equal[Vader] = Deriving[Equal].xproductz(
      Prod(Need(Equal[String]), Need(Equal[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val equal: Equal[JarJar] = Deriving[Equal].xproductz(
      Prod(Need(Equal[Int]), Need(Equal[String])))(iso.to, iso.from)
  }
```

A> Typeclasses en la API `Deriving` están envueltas en `Need` (recuerde `Name`
A> del Capítulo 6), que permite construcción perezosa, evitando trabajo
A> innecesario si la typeclass no se requiere, y evitando sobreflujos de la pila
A> para ADTs recursivas.

Para ser capaces de hacer lo mismo para nuestra typeclass `Default`, necesitamos
proporcionar una instancia de `Deriving[Default]`. Esto es solo un caso de
envolver nuestro `Alt` con un auxiliar:

```scala
  object Default {
    ...
    implicit val deriving: Deriving[Default] = ExtendedInvariantAlt(monad)
  }
```

y entonces invocarlo desde los objetos compañeros

```scala
  object Darth {
    ...
    implicit val default: Default[Darth] = Deriving[Default].xcoproductz(
      Prod(Need(Default[Vader]), Need(Default[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val default: Default[Vader] = Deriving[Default].xproductz(
      Prod(Need(Default[String]), Need(Default[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val default: Default[JarJar] = Deriving[Default].xproductz(
      Prod(Need(Default[Int]), Need(Default[String])))(iso.to, iso.from)
  }
```

Hemos resuelto el problema de aridad arbitraria, pero hemos introducido aún más
código repetitivo.

El punto clave es que la anotación `@deriving`, que viene del `deriving-plugin`,
genera todo este código repetitivo automáticamente y únicamente requiere ser
aplicado en el punto más alto de la jerarquía de una ADT:

```scala
Para ser capaces de usar la API de `scalaz-deriving`, necesitamos un
`Isomorfismo` entre nuestras ADTs y la representación genérica de `iotaz`. Es
mucho código repetitivo, y volveremos a esto en un momento:

```scala
  object Darth {
    private type Repr   = Vader :: JarJar :: TNil
    private val VaderI  = Cop.Inject[Vader, Cop[Repr]]
    private val JarJarI = Cop.Inject[JarJar, Cop[Repr]]
    private val iso     = IsoSet(
      {
        case d: Vader  => VaderI.inj(d)
        case d: JarJar => JarJarI.inj(d)
      }, {
        case VaderI(d)  => d
        case JarJarI(d) => d
      }
    )
    ...
  }

  object Vader {
    private type Repr = String :: Int :: TNil
    private val iso   = IsoSet(
      d => Prod(d.s, d.i),
      p => Vader(p.head, p.tail.head)
    )
    ...
  }

  object JarJar {
    private type Repr = Int :: String :: TNil
    private val iso   = IsoSet(
      d => Prod(d.i, d.s),
      p => JarJar(p.head, p.tail.head)
    )
    ...
  }
```

Con esto fuera del camino podemos llamar la API `Deriving` para `Equal`, posible
porque `scalaz-deriving` proporciona una instancia optimizada de
`Deriving[Equal]`

```scala
  object Darth {
    ...
    implicit val equal: Equal[Darth] = Deriving[Equal].xcoproductz(
      Prod(Need(Equal[Vader]), Need(Equal[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val equal: Equal[Vader] = Deriving[Equal].xproductz(
      Prod(Need(Equal[String]), Need(Equal[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val equal: Equal[JarJar] = Deriving[Equal].xproductz(
      Prod(Need(Equal[Int]), Need(Equal[String])))(iso.to, iso.from)
  }
```

A> Typeclasses en la API `Deriving` están envueltas en `Need` (recuerde `Name`
A> del Capítulo 6), que permite construcción perezosa, evitando trabajo
A> innecesario si la typeclass no se requiere, y evitando sobreflujos de la pila
A> para ADTs recursivas.

Para ser capaces de hacer lo mismo para nuestra typeclass `Default`, necesitamos
proporcionar una instancia de `Deriving[Default]`. Esto es solo un caso de
envolver nuestro `Alt` con un auxiliar:

```scala
  object Default {
    ...
    implicit val deriving: Deriving[Default] = ExtendedInvariantAlt(monad)
  }
```

y entonces invocarlo desde los objetos compañeros

```scala
  object Darth {
    ...
    implicit val default: Default[Darth] = Deriving[Default].xcoproductz(
      Prod(Need(Default[Vader]), Need(Default[JarJar])))(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val default: Default[Vader] = Deriving[Default].xproductz(
      Prod(Need(Default[String]), Need(Default[Int])))(iso.to, iso.from)
  }
  object JarJar {
    ...
    implicit val default: Default[JarJar] = Deriving[Default].xproductz(
      Prod(Need(Default[Int]), Need(Default[String])))(iso.to, iso.from)
  }
```

Hemos resuelto el problema de aridad arbitraria, pero hemos introducido aún más
código repetitivo.

El punto clave es que la anotación `@deriving`, que viene del `deriving-plugin`,
genera todo este código repetitivo automáticamente y únicamente requiere ser
aplicado en el punto más alto de la jerarquía de una ADT:

```scala
  sealed abstract class /~\[A[_], B[_]] {
    type T
    def a: A[T]
    def b: B[T]
  }
  object /~\ {
    type APair[A[_], B[_]]  = A /~\ B
    def unapply[A[_], B[_]](p: A /~\ B): Some[(A[p.T], B[p.T])] = ...
    def apply[A[_], B[_], Z](az: =>A[Z], bz: =>B[Z]): A /~\ B = ...
  }
```

Típicamente usamos esto en el contexto de `Id /~\ TC` donde `TC` es nuestra
typeclass, significando que tenemos un valor, y una instancia de una typeclass
para ese valor, sin saber nada más sobre el valor.

Además, todos los métodos en la API `Deriving` API tienen evidencia implícita de
la forma `A PairedWith FA`, permitiendo que la librería `iotaz` sea capaz de
realizar `.zip`, `.traverse`, y otras operaciones sobre `Prod` y `Cop`. Podemos
ignorar estos parámetros, dado que no los usamos directamente.

#### `Equal`

Como con `Default` podríamos definir un `Decidable` de aridad fija y envolverlo con `ExtendedInvariantAlt` (la forma más simple), pero escogemos implementar `Decidablez` directamente por el beneficio de obtener más performance. Hacemos dos optimizaciones adicionales:

1. Realizar igualdad de instancias `.eq` antes de aplicar `Equal.equal`,
   ejecutando un atajo al verificar la igualdad entre valores idénticos.
2. `Foldable.all` permitiendo una salida temprana cuando cualquier comparación
   es `false`, por ejemplo si los primeros campos no empatan, ni siquiera
   pedimos `Equal` para los valores restantes.

```scala
  new Decidablez[Equal] {
    @inline private final def quick(a: Any, b: Any): Boolean =
      a.asInstanceOf[AnyRef].eq(b.asInstanceOf[AnyRef])

    def dividez[Z, A <: TList, FA <: TList](tcs: Prod[FA])(g: Z => Prod[A])(
      implicit ev: A PairedWith FA
    ): Equal[Z] = (z1, z2) => (g(z1), g(z2)).zip(tcs).all {
      case (a1, a2) /~\ fa => quick(a1, a2) || fa.value.equal(a1, a2)
    }

    def choosez[Z, A <: TList, FA <: TList](tcs: Prod[FA])(g: Z => Cop[A])(
      implicit ev: A PairedWith FA
    ): Equal[Z] = (z1, z2) => (g(z1), g(z2)).zip(tcs) match {
      case -\/(_)               => false
      case \/-((a1, a2) /~\ fa) => quick(a1, a2) || fa.value.equal(a1, a2)
    }
  }
```

#### `Default`

Tristemente, la API de `iotaz` para `.traverse` (y su análogo, `.coptraverse`)
requiere que definamos transformaciones naturales, que tienen una sintaxis
torpe, incluso con el plugin `kind-projector`.

```scala
  private type K[a] = Kleisli[String \/ ?, Unit, a]
  new IsomorphismMonadError[Default, K, String] with Altz[Default] {
    type Sig[a] = Unit => String \/ a
    override val G = MonadError[K, String]
    override val iso = Kleisli.iso(
      λ[Sig ~> Default](s => instance(s(()))),
      λ[Default ~> Sig](d => _ => d.default)
    )

    val extract = λ[NameF ~> (String \/ ?)](a => a.value.default)
    def applyz[Z, A <: TList, FA <: TList](tcs: Prod[FA])(f: Prod[A] => Z)(
      implicit ev: A PairedWith FA
    ): Default[Z] = instance(tcs.traverse(extract).map(f))

    val always = λ[NameF ~> Maybe](a => a.value.default.toMaybe)
    def altlyz[Z, A <: TList, FA <: TList](tcs: Prod[FA])(f: Cop[A] => Z)(
      implicit ev: A PairedWith FA
    ): Default[Z] = instance {
      tcs.coptraverse[A, NameF, Id](always).map(f).headMaybe \/> "not found"
    }
  }
```

#### `Semigroup`

No es posible definir un `Semigroup` para coproductos generales, sin embargo es
posible definir uno para productos generales. Podemos usar la aridad arbitraria
`InvariantApplicative`:

```scala
  new InvariantApplicativez[Semigroup] {
    type L[a] = ((a, a), NameF[a])
    val appender = λ[L ~> Id] { case ((a1, a2), fa) => fa.value.append(a1, a2) }

    def xproductz[Z, A <: TList, FA <: TList](tcs: Prod[FA])
                                             (f: Prod[A] => Z, g: Z => Prod[A])
                                             (implicit ev: A PairedWith FA) =
      new Semigroup[Z] {
        def append(z1: Z, z2: =>Z): Z = f(tcs.ziptraverse2(g(z1), g(z2), appender))
      }
  }
```

#### `JsEncoder` y `JsDecoder`

`scalaz-deriving` no proporciona acceso a nombres de los campos de modo que no es posible escribir un codificador/decodificador JSON.

A> Una versión previa de `scalaz-deriving` soportaba los nombres de los campos
A> pero era claro que no había ventaja sobre el uso de Magnolia, de modo que se
A> eliminó el soporte para mantener el enfoque en typeclasses con `Alt` y
A> `Decidable` obedientes a las leyes.

## Magnolia

La librería de macros Magnolia proporciona una API limpia para escribir
derivaciones de typeclasses. Se instala con la siguiente entrada en `build.sbt`

```scala
  libraryDependencies += "com.propensive" %% "magnolia" % "0.10.1"
```

Un autor de typeclasses implementaría los siguientes miembros:

```scala
  import magnolia._

  object MyDerivation {
    type Typeclass[A]

    def combine[A](ctx: CaseClass[Typeclass, A]): Typeclass[A]
    def dispatch[A](ctx: SealedTrait[Typeclass, A]): Typeclass[A]

    def gen[A]: Typeclass[A] = macro Magnolia.gen[A]
  }
```

La API de Magnolia es:

```scala
  class CaseClass[TC[_], A] {
    def typeName: TypeName
    def construct[B](f: Param[TC, A] => B): A
    def constructMonadic[F[_]: Monadic, B](f: Param[TC, A] => F[B]): F[A]
    def parameters: Seq[Param[TC, A]]
    def annotations: Seq[Any]
  }

  class SealedTrait[TC[_], A] {
    def typeName: TypeName
    def subtypes: Seq[Subtype[TC, A]]
    def dispatch[B](value: A)(handle: Subtype[TC, A] => B): B
    def annotations: Seq[Any]
  }
```

con auxiliares

```scala
  class CaseClass[TC[_], A] {
    def typeName: TypeName
    def construct[B](f: Param[TC, A] => B): A
    def constructMonadic[F[_]: Monadic, B](f: Param[TC, A] => F[B]): F[A]
    def parameters: Seq[Param[TC, A]]
    def annotations: Seq[Any]
  }

  class SealedTrait[TC[_], A] {
    def typeName: TypeName
    def subtypes: Seq[Subtype[TC, A]]
    def dispatch[B](value: A)(handle: Subtype[TC, A] => B): B
    def annotations: Seq[Any]
  }
```

La typeclass `Monadic`, usada en `constructMonadic`, es generada automáticamente
si nuestro tipo de datos tiene un `.map` y un método `.flatMap` cuando
escribimos `import mercator._`

No tiene sentido usar Magnolia para typeclasses que pueden abstraerse con
`Divisible`, `Decidable`, `Applicative` o `Alt`, dado que estas abstracciones
proporcionan mucha estructura extra y pruebas de manera gratuita. Sin embargo,
Magnolia ofrece funcionalidad que `scalaz-deriving` no puede proporcionar: acceso a los nombres de campos, nombres de tipos, anotaciones y valores por defecto.

### Ejemplo: JSON

Tenemos ciertas decisiones de diseño que hacer con respecto a la serialización
JSON:

1. ¿Deberíamos incluir campos con valores `null`?
2. ¿Deberíamos decodificar de manera distinta valores faltantes vs `null`?
3. ¿Cómo codificamos el nombre de un coproducto?
4. Cómo lidiamos con coproductos que no son `JsObject`?

Escogemos alternativas por defecto razonables

- No incluya campos si el valor es un `JsNull`.
- Maneje campos faltantes de la misma manera que los valores `null`.
- Use un campo especial `"type"` para desambiguar coproductos usando el nombre
  del tipo.
- Ponga valores primitivos en un campo especial `"xvalue"`.

y deje que los usuarios adjunten una anotación a los campos coproductos y
productos para personalizar sus formatos:

```scala
  sealed class json extends Annotation
  object json {
    final case class nulls()          extends json
    final case class field(f: String) extends json
    final case class hint(f: String)  extends json
  }
```

A> Magnolia no está limitada a una familia de anotaciones. Esta codificación
A> está pensada para realizar una comparación con Shapeless en la siguiente sección.

```scala
  @json.field("TYPE")
  sealed abstract class Cost
  final case class Time(s: String) extends Cost
  final case class Money(@json.field("integer") i: Int) extends Cost
```

Empiece con un `JsEncoder` que maneja únicamente nuestras elecciones razonables:

```scala
  object JsMagnoliaEncoder {
    type Typeclass[A] = JsEncoder[A]

    def combine[A](ctx: CaseClass[JsEncoder, A]): JsEncoder[A] = { a =>
      val empty = IList.empty[(String, JsValue)]
      val fields = ctx.parameters.foldRight(right) { (p, acc) =>
        p.typeclass.toJson(p.dereference(a)) match {
          case JsNull => acc
          case value  => (p.label -> value) :: acc
        }
      }
      JsObject(fields)
    }

    def dispatch[A](ctx: SealedTrait[JsEncoder, A]): JsEncoder[A] = a =>
      ctx.dispatch(a) { sub =>
        val hint = "type" -> JsString(sub.typeName.short)
        sub.typeclass.toJson(sub.cast(a)) match {
          case JsObject(fields) => JsObject(hint :: fields)
          case other            => JsObject(IList(hint, "xvalue" -> other))
        }
      }

    def gen[A]: JsEncoder[A] = macro Magnolia.gen[A]
  }
```

Podemos ver cómo la API de Magnolia hace que sea sencillo acceder a nombres de
campos y typeclasses para cada parámetro.

Ahora agregue soporte para anotaciones que manejan preferencias del usuario.
Para evitar la búsqueda de anotaciones en cada codificación, los guardaremos en
un arreglo.
