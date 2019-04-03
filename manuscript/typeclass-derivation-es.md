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
un arreglo. Aunque el acceso a los campos en un arreglo no es total, tenemos la
garantía de que los índices siempre serán adecuados. El rendimiento es con
frecuencia la víctima en la negociación entre especialización y generalización.

```scala
  object JsMagnoliaEncoder {
    type Typeclass[A] = JsEncoder[A]

    def combine[A](ctx: CaseClass[JsEncoder, A]): JsEncoder[A] =
      new JsEncoder[A] {
        private val anns = ctx.parameters.map { p =>
          val nulls = p.annotations.collectFirst {
            case json.nulls() => true
          }.getOrElse(false)
          val field = p.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse(p.label)
          (nulls, field)
        }.toArray

        def toJson(a: A): JsValue = {
          val empty = IList.empty[(String, JsValue)]
          val fields = ctx.parameters.foldRight(empty) { (p, acc) =>
            val (nulls, field) = anns(p.index)
            p.typeclass.toJson(p.dereference(a)) match {
              case JsNull if !nulls => acc
              case value            => (field -> value) :: acc
            }
          }
          JsObject(fields)
        }
      }

    def dispatch[A](ctx: SealedTrait[JsEncoder, A]): JsEncoder[A] =
      new JsEncoder[A] {
        private val field = ctx.annotations.collectFirst {
          case json.field(name) => name
        }.getOrElse("type")
        private val anns = ctx.subtypes.map { s =>
          val hint = s.annotations.collectFirst {
            case json.hint(name) => field -> JsString(name)
          }.getOrElse(field -> JsString(s.typeName.short))
          val xvalue = s.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse("xvalue")
          (hint, xvalue)
        }.toArray

        def toJson(a: A): JsValue = ctx.dispatch(a) { sub =>
          val (hint, xvalue) = anns(sub.index)
          sub.typeclass.toJson(sub.cast(a)) match {
            case JsObject(fields) => JsObject(hint :: fields)
            case other            => JsObject(hint :: (xvalue -> other) :: IList.empty)
          }
        }
      }

    def gen[A]: JsEncoder[A] = macro Magnolia.gen[A]
  }
```

Para el decodificador usamos `.constructMonadic` que tiene una firma de tipo
similar a `.traverse`

```scala
  object JsMagnoliaDecoder {
    type Typeclass[A] = JsDecoder[A]

    def combine[A](ctx: CaseClass[JsDecoder, A]): JsDecoder[A] = {
      case obj @ JsObject(_) =>
        ctx.constructMonadic(
          p => p.typeclass.fromJson(obj.get(p.label).getOrElse(JsNull))
        )
      case other => fail("JsObject", other)
    }

    def dispatch[A](ctx: SealedTrait[JsDecoder, A]): JsDecoder[A] = {
      case obj @ JsObject(_) =>
        obj.get("type") match {
          case \/-(JsString(hint)) =>
            ctx.subtypes.find(_.typeName.short == hint) match {
              case None => fail(s"a valid '$hint'", obj)
              case Some(sub) =>
                val value = obj.get("xvalue").getOrElse(obj)
                sub.typeclass.fromJson(value)
            }
          case _ => fail("JsObject with type", obj)
        }
      case other => fail("JsObject", other)
    }

    def gen[A]: JsDecoder[A] = macro Magnolia.gen[A]
  }
```

De nuevo, agregando soporte para las preferencias del usuario y valores por
defecto para los campos, junto con algunas optimizaciones:

```scala
  object JsMagnoliaDecoder {
    type Typeclass[A] = JsDecoder[A]

    def combine[A](ctx: CaseClass[JsDecoder, A]): JsDecoder[A] =
      new JsDecoder[A] {
        private val nulls = ctx.parameters.map { p =>
          p.annotations.collectFirst {
            case json.nulls() => true
          }.getOrElse(false)
        }.toArray

        private val fieldnames = ctx.parameters.map { p =>
          p.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse(p.label)
        }.toArray

        def fromJson(j: JsValue): String \/ A = j match {
          case obj @ JsObject(_) =>
            import mercator._
            val lookup = obj.fields.toMap
            ctx.constructMonadic { p =>
              val field = fieldnames(p.index)
              lookup
                .get(field)
                .into {
                  case Maybe.Just(value) => p.typeclass.fromJson(value)
                  case _ =>
                    p.default match {
                      case Some(default) => \/-(default)
                      case None if nulls(p.index) =>
                        s"missing field '$field'".left
                      case None => p.typeclass.fromJson(JsNull)
                    }
                }
            }
          case other => fail("JsObject", other)
        }
      }

    def dispatch[A](ctx: SealedTrait[JsDecoder, A]): JsDecoder[A] =
      new JsDecoder[A] {
        private val subtype = ctx.subtypes.map { s =>
          s.annotations.collectFirst {
            case json.hint(name) => name
          }.getOrElse(s.typeName.short) -> s
        }.toMap
        private val typehint = ctx.annotations.collectFirst {
          case json.field(name) => name
        }.getOrElse("type")
        private val xvalues = ctx.subtypes.map { sub =>
          sub.annotations.collectFirst {
            case json.field(name) => name
          }.getOrElse("xvalue")
        }.toArray

        def fromJson(j: JsValue): String \/ A = j match {
          case obj @ JsObject(_) =>
            obj.get(typehint) match {
              case \/-(JsString(h)) =>
                subtype.get(h) match {
                  case None => fail(s"a valid '$h'", obj)
                  case Some(sub) =>
                    val xvalue = xvalues(sub.index)
                    val value  = obj.get(xvalue).getOrElse(obj)
                    sub.typeclass.fromJson(value)
                }
              case _ => fail(s"JsObject with '$typehint' field", obj)
            }
          case other => fail("JsObject", other)
        }
      }

    def gen[A]: JsDecoder[A] = macro Magnolia.gen[A]
  }
```

Llamamos al método `JsMagnoliaEncoder.gen` o a `JsMagnoliaDecoder.gen` desde el
objeto compañero de nuestro tipo de datos. Por ejemplo, la API de Google Maps

```scala
  final case class Value(text: String, value: Int)
  final case class Elements(distance: Value, duration: Value, status: String)
  final case class Rows(elements: List[Elements])
  final case class DistanceMatrix(
    destination_addresses: List[String],
    origin_addresses: List[String],
    rows: List[Rows],
    status: String
  )

  object Value {
    implicit val encoder: JsEncoder[Value] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[Value] = JsMagnoliaDecoder.gen
  }
  object Elements {
    implicit val encoder: JsEncoder[Elements] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[Elements] = JsMagnoliaDecoder.gen
  }
  object Rows {
    implicit val encoder: JsEncoder[Rows] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[Rows] = JsMagnoliaDecoder.gen
  }
  object DistanceMatrix {
    implicit val encoder: JsEncoder[DistanceMatrix] = JsMagnoliaEncoder.gen
    implicit val decoder: JsDecoder[DistanceMatrix] = JsMagnoliaDecoder.gen
  }
```

Felizmente, ¡la anotación `@deriving` soporta Magnolia! Si el autor de la
typeclass proporciona un archivo `deriving.conf` junto con su jar, que contenga
el siguiente texto

```scala
  jsonformat.JsEncoder=jsonformat.JsMagnoliaEncoder.gen
  jsonformat.JsDecoder=jsonformat.JsMagnoliaDecoder.gen
```

la `deriving-macro` llamará al método provisto por el usuario:

```scala
  @deriving(JsEncoder, JsDecoder)
  final case class Value(text: String, value: Int)
  @deriving(JsEncoder, JsDecoder)
  final case class Elements(distance: Value, duration: Value, status: String)
  @deriving(JsEncoder, JsDecoder)
  final case class Rows(elements: List[Elements])
  @deriving(JsEncoder, JsDecoder)
  final case class DistanceMatrix(
    destination_addresses: List[String],
    origin_addresses: List[String],
    rows: List[Rows],
    status: String
  )
```

### Derivación completamente automática

La generación de instancias `implicit` en el objeto compañero del tipo de datos
se conoce (históricamente) como derivación *semiautomática*, en contraste con la
*completamente automática* que ocurre cuando el `.gen` se hace `implicit`

```scala
  object JsMagnoliaEncoder {
    ...
    implicit def gen[A]: JsEncoder[A] = macro Magnolia.gen[A]
  }
  object JsMagnoliaDecoder {
    ...
    implicit def gen[A]: JsDecoder[A] = macro Magnolia.gen[A]
  }
```

Los usuarios pueden importar estos métodos en su alcance y obtener derivación
mágica en el punto de uso

```scala
  scala> final case class Value(text: String, value: Int)
  scala> import JsMagnoliaEncoder.gen
  scala> Value("hello", 1).toJson
  res = JsObject([("text","hello"),("value",1)])
```

Esto puede parecer tentador, dado que envuelve la cantidad mínima de escritura,
pero hay dos advertencias a tomar en cuenta:

1. La macro se llama en cada sitio de uso, es decir cada vez que llamamos
   `.toJson`. Esto hace que la compilación sea más lenta y también produce más
   objetos en tiempo de ejecución, lo que impactará el rendimiento en tiempo de
   ejecución.
2. Podrían derivarse cosas inesperadas

La primera advertencia es evidente, pero las derivaciones inesperadas se manifiestan como errores sutiles. Considere lo que podría ocurrir con

```scala
  @deriving(JsEncoder)
  final case class Foo(s: Option[String])
```

si olvidamos proporcionar una derivación implícita para `Option`. Esperaríamos que `Foo(Some("hello")` se viera como

```json
  {
    "s":"hello"
  }
```

Pero en realidad sería la siguiente

```json
  {
    "s": {
      "type":"Some",
      "get":"hello"
    }
  }
```

porque Magnolia derivó un codificador `Option` por nosotros.

Esto es confuso, y preferiríamos que el compilador nos indicara que olvidamos
algo. Es por esta razón que no se recomienda la derivación completamente
automática.

## Shapeless

La librería [Shapeless](https://github.com/milessabin/shapeless) es notablemente
la librería más complicada en Scala. La razón de esto es porque toma la
característica `implicit` del lenguaje hasta el extremo: la creación de un
lenguaje de *programación genérica* a nivel de los tipos.

No se trata de un concepto extraño: en Scalaz intentamos limitar nuestro uso de
la característica `implicit` a las typeclasses, pero algunas veces solicitamos
que el compilador nos provea *evidencia* que relacione los tipos. Por ejemplo
las relaciones Liskov o Leibniz (`<~<` y `===`), y el inyectar (`Inject`) una
álgebra libre de `scalaz.Coproduct` de álgebras.

A> No es necesario entender Shapeless para ser un programador funcional. Si este
A> capítulo es demasiado complicado, salte a la siguiente sección.

Para instalar Shapeless, agregue lo siguiente a `build.sbt`

```scala
  libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.3"
```

En el centro de Shapeless están los tipos de datos `HList` y `Coproduct`

```scala
  package shapeless

  sealed trait HList
  final case class ::[+H, +T <: HList](head: H, tail: T) extends HList
  sealed trait NNil extends HList
  case object HNil extends HNil {
    def ::[H](h: H): H :: HNil = ::(h, this)
  }

  sealed trait Coproduct
  sealed trait :+:[+H, +T <: Coproduct] extends Coproduct
  final case class Inl[+H, +T <: Coproduct](head: H) extends :+:[H, T]
  final case class Inr[+H, +T <: Coproduct](tail: T) extends :+:[H, T]
  sealed trait CNil extends Coproduct // no implementations
```

que son representaciones *genéricas* de productos y coproductos. El *trait*
`sealed trait HNil` se usa por motivos de conveniencia de modo que nunca sea
necesario teclear `HNil.type`.

Shapeless tiene un clon del tipo de datos `IsoSet`, llamado `Generic`, que nos
permite viajar entre una ADT y su representación genérica:

```scala
  trait Generic[T] {
    type Repr
    def to(t: T): Repr
    def from(r: Repr): T
  }
  object Generic {
    type Aux[T, R] = Generic[T] { type Repr = R }
    def apply[T](implicit G: Generic[T]): Aux[T, G.Repr] = G
    implicit def materialize[T, R]: Aux[T, R] = macro ...
  }
```

Muchos de los tipos en Shapeless tiene un tipo miembro (`Repr`) y un tipo `.Aux`
alias en su objeto compañero que hace que el segundo tipo sea visible. Esto
permite solicitar `Generic[Foo]` para un tipo `Foo` sin tener que proporcionar
la representación genérica, que se genera por una macro.

```scala
  scala> import shapeless._
  scala> final case class Foo(a: String, b: Long)
         Generic[Foo].to(Foo("hello", 13L))
  res: String :: Long :: HNil = hello :: 13 :: HNil

  scala> Generic[Foo].from("hello" :: 13L :: HNil)
  res: Foo = Foo(hello,13)

  scala> sealed abstract class Bar
         case object Irish extends Bar
         case object English extends Bar

  scala> Generic[Bar].to(Irish)
  res: English.type :+: Irish.type :+: CNil.type = Inl(Irish)

  scala> Generic[Bar].from(Inl(Irish))
  res: Bar = Irish
```

Existe un `LabelledGeneric` complementario que incluye los nombres de los campos

```scala
  scala> import shapeless._, labelled._
  scala> final case class Foo(a: String, b: Long)

  scala> LabelledGeneric[Foo].to(Foo("hello", 13L))
  res: String with KeyTag[Symbol with Tagged[String("a")], String] ::
       Long   with KeyTag[Symbol with Tagged[String("b")],   Long] ::
       HNil =
       hello :: 13 :: HNil

  scala> sealed abstract class Bar
         case object Irish extends Bar
         case object English extends Bar

  scala> LabelledGeneric[Bar].to(Irish)
  res: Irish.type   with KeyTag[Symbol with Tagged[String("Irish")],     Irish.type] :+:
       English.type with KeyTag[Symbol with Tagged[String("English")], English.type] :+:
       CNil.type =
       Inl(Irish)
```

Note que el **valor** de una representación `LabelledGeneric` es la misma que la
representación `Generic`: los nombres de los campos existen únicamente en el
tipo y son borrados en tiempo de ejecución.

Nunca necesitamos teclear `KeyTag` manualmente, y usamos el alias de tipo:

```scala
  type FieldType[K, +V] = V with KeyTag[K, V]
```

Si deseamos acceder al campo de nombre desde un `FielType[K, A]`, pedimos
evidencia implícita `Witness.Aux[K]`, que nos permite acceder al valor de `K` en
tiempo de ejecución.

Superficialmente, esto es todo lo que tenemos que saber de Shapeless para ser
capaces de derivar un typeclass. Sin embargo, las cosas se ponen cada vez más
complejas, de modo que procederemos con ejemplos cada vez más complejos.

### Ejemplo: `Equal`

Un patrón típico es el de extender la typeclass que deseamos derivar, y poner el código de Shapeless en su objeto compañero. Esto nos da un alcance implícito que el compilador puede buscar sin requerir `imports` complejos.

```scala
  trait DerivedEqual[A] extends Equal[A]
  object DerivedEqual {
    ...
  }
```

El punto de entrada a una derivación de Shapeless es un método, `gen`, que
requiere dos parámetros de tipo, la `A` que estamos derivando y la `R` para su
representación genérica. Entonces pedimos por la `Generic.Aux[A, R]`, que
relaciona `A` con `R`, y una instancia de la typeclass `Derived` para la `R`.
Empezamos con esta firma y una implementación simple:

```scala
  import shapeless._

  object DerivedEqual {
    def gen[A, R: DerivedEqual](implicit G: Generic.Aux[A, R]): Equal[A] =
      (a1, a2) => Equal[R].equal(G.to(a1), G.to(a2))
  }
```

Así hemos reducido el problema a proporcionar un implícito `Equal[R]` para una
`R` que es la representación `Generic` de `A`. Primero considere los productos,
donde `R <: HList`. Esta es la firma que deseamos implementar:

```scala
  implicit def hcons[H: Equal, T <: HList: DerivedEqual]: DerivedEqual[H :: T]
```

porque si podemos implementarlo para una cabeza y una cola, el compilador será
capaz de realizar una recursión en este método hasta que alcance el final de la
lista. Donde necesitemos proporcionar una instancia para el `HNil` vacío

```scala
  implicit def hnil: DerivedEqual[HNil]
```

Implementamos estos métodos

```scala
  implicit def hcons[H: Equal, T <: HList: DerivedEqual]: DerivedEqual[H :: T] =
    (h1, h2) => Equal[H].equal(h1.head, h2.head) && Equal[T].equal(h1.tail, h2.tail)

  implicit val hnil: DerivedEqual[HNil] = (_, _) => true
```

y para los coproductos deseamos implementar estas firmas

```scala
  implicit def ccons[H: Equal, T <: Coproduct: DerivedEqual]: DerivedEqual[H :+: T]
  implicit def cnil: DerivedEqual[CNil]
```

A> Scalaz y Shapeless comparten muchos nombres de tipos, y cuando los mezclamos
A> con frecuencia requerimos excluir ciertos elementos del import, por ejemplo
A>
A> {lang="text"}
A> ~~~~~~~~
A>   import scalaz.{ Coproduct => _, :+: => _, _ }, Scalaz._
A>   import shapeless._
A> ~~~~~~~~

`.cnil` nunca será llamada por una typeclass como `Equal` con parámetros de tipo únicamente en posición contravariante. Pero el compilador no sabe esto, de modo que tenemos que proporcionar un *stub*:

```scala
  implicit val cnil: DerivedEqual[CNil] = (_, _) => sys.error("impossible")
```

Para el caso del coproducto sólo podemos comparar dos cosas si están alineadas, que ocurre cuando son tanto `In1` o `Inr`

```scala
  implicit def ccons[H: Equal, T <: Coproduct: DerivedEqual]: DerivedEqual[H :+: T] = {
    case (Inl(c1), Inl(c2)) => Equal[H].equal(c1, c2)
    case (Inr(c1), Inr(c2)) => Equal[T].equal(c1, c2)
    case _                  => false
  }
```

¡Es digno de mención que nuestros métodos se alinean con el concepto de
`conquer` (`hnil`), `divide2` (`hlist`) y `alt2` (`coproduct`)! Sin embargo, no
tenemos ninguna de las ventajas de implementar `Decidable`, debido a que ahora
debemos empezar desde ceros cuando escribimos pruebas para este código.

De modo que probemos esto con una ADT simple

```scala
  sealed abstract class Foo
  final case class Bar(s: String)          extends Foo
  final case class Faz(b: Boolean, i: Int) extends Foo
  final case object Baz                    extends Foo
```

Tenemos que proporcionar instancias en los objetos compañeros:

```scala
  object Foo {
    implicit val equal: Equal[Foo] = DerivedEqual.gen
  }
  object Bar {
    implicit val equal: Equal[Bar] = DerivedEqual.gen
  }
  object Faz {
    implicit val equal: Equal[Faz] = DerivedEqual.gen
  }
  final case object Baz extends Foo {
    implicit val equal: Equal[Baz.type] = DerivedEqual.gen
  }
```

Pero no siempre compila

```text
  [error] shapeless.scala:41:38: ambiguous implicit values:
  [error]  both value hnil in object DerivedEqual of type => DerivedEqual[HNil]
  [error]  and value cnil in object DerivedEqual of type => DerivedEqual[CNil]
  [error]  match expected type DerivedEqual[R]
  [error]     : Equal[Baz.type] = DerivedEqual.gen
  [error]                                      ^
```

¡Bienvenido a los mensajes de error de compilación de Shapeless!

El problema, que no es evidente en lo absoluto a partir del mensaje de error, es
que el compilador es incapaz de averiguar qué es `R`, y "piensa" que es otra
cosa. Requerimos proporcionar los parámetros de tipo explícitamente cuando
llamamos a `gen`, es decir

```scala
  implicit val equal: Equal[Baz.type] = DerivedEqual.gen[Baz.type, HNil]
```

o podemos usar la macro `Generic` para ayudarnos y dejar que el compilador
infiera la representación genérica

```scala
  final case object Baz extends Foo {
    implicit val generic                = Generic[Baz.type]
    implicit val equal: Equal[Baz.type] = DerivedEqual.gen[Baz.type, generic.Repr]
  }
  ...
```

A> En este momento, ¡ignore cualquier línea roja indicando error y confíe
A> únicamente en el compilador! Este es el punto donde Shapeless ya no tiene
A> soporte del IDE.

La razón por la que esto soluciona el problema es porque la firma de tipo, después de quitar algunas conveniencias sintácticas (*syntactic sugar*)

```scala
  def gen[A, R: DerivedEqual](implicit G: Generic.Aux[A, R]): Equal[A]
```

se convierte en

```scala
  def gen[A, R](implicit R: DerivedEqual[R], G: Generic.Aux[A, R]): Equal[A]
```

El compilador de Scala resuelve estas restricciones de tipo de izquierda a
derecha, de modo que encuentra muchas soluciones distintas a `DerivedEqual[R]`
antes de aplicar la restricción `Generic.Aux[A, R]`. Otra forma de resolver esto
es no usar límites de contexto.

A> Más bien que presentar una versión completamente funcional/correcta, sentimos
A> que es importante mostrar cuándo código obvio falla, y tal es la realidad de
A> Shapeless. Otra cosa que pudimos hacer de manera razonable aquí es hacer que
A> el trait `DerivedEqual` sea `sealed`, de modo que únicamente versiones
A> derivadas sean válidas. Sin embargo, ¡`sealed trait` no es compatible con los
A> tipos SAM! Si estamos viviendo al filo de la navaja, espere ser cortado.

Con esto en mente, ya no requerimos el `implicit val generic` o los parámetros
de tipo explícitos en la invocación a `.gen`. Podemos alambrar `@deriving` al
agregar una entrada en `deriving.conf` (asumiendo que deseamos hacer un override
de la implementación `scalaz-deriving`).

```scala
 scalaz.Equal=fommil.DerivedEqual.gen
```

y escribir

```scala
  @deriving(Equal) sealed abstract class Foo
  @deriving(Equal) final case class Bar(s: String)          extends Foo
  @deriving(Equal) final case class Faz(b: Boolean, i: Int) extends Foo
  @deriving(Equal) final case object Baz
```

Pero, reemplazar `scalaz-deriving` significa que los tiempos de compilación se
vuelven más lentos. Esto se debe a que el compilador está resolviendo `N`
búsquedas implícitas para cada producto de `N` campos o coproductos de `N`
productos, mientras que `scalaz-deriving` y Magnolia no.

Note que cuando usamos `scalaz-deriving` o Magnolia podemos poner la anotación
`@deriving` únicamente en el miembro más alto de una ADT, pero para Shapeless
debemos agregarlo a todas sus entradas.

Sin embargo, esta implementación todavía tiene un error: falla para los tipos recursivos **en tiempo de ejecución**, es decir

```scala
  @deriving(Equal) sealed trait ATree
  @deriving(Equal) final case class Leaf(value: String)               extends ATree
  @deriving(Equal) final case class Branch(left: ATree, right: ATree) extends ATree
```

```scala
  scala> val leaf1: Leaf    = Leaf("hello")
         val leaf2: Leaf    = Leaf("goodbye")
         val branch: Branch = Branch(leaf1, leaf2)
         val tree1: ATree   = Branch(leaf1, branch)
         val tree2: ATree   = Branch(leaf2, branch)

  scala> assert(tree1 /== tree2)
  [error] java.lang.NullPointerException
  [error] at DerivedEqual$.shapes$DerivedEqual$$$anonfun$hcons$1(shapeless.scala:16)
          ...
```

La razón por la que esto pasa es porque `Equal[Tree]` depende de
`Equal[Branch]`, que a su vez depende de `Equal[Tree]`. ¡Recursión y BANG! Debe
cargarse de manera perezosa, y no de manera estricta.

Tanto `scalaz-deriving` como Magnolia lidian automáticamente con la pereza, pero
en Shapeless es la responsabilidad del autor de la typeclass.

Los tipos de macro `Cached`, `Strict` y `Lazy` modifican el comportamiento de la
inferencia de tipos del compilador, permitiéndonos alcanzar el nivel de pereza
que necesitemos. El patrón que hay que seguir es usar `Cached[Strict[_]]` en el
punto de entrada y `Lazy[_]` alrededor de las instancias `H`.

Es mejor apartarse de los límites de contexto y los tipos SAM a partir de este
punto:

```scala
  sealed trait DerivedEqual[A] extends Equal[A]
  object DerivedEqual {
    def gen[A, R](
      implicit G: Generic.Aux[A, R],
      R: Cached[Strict[DerivedEqual[R]]]
    ): Equal[A] = new Equal[A] {
      def equal(a1: A, a2: A) =
        quick(a1, a2) || R.value.value.equal(G.to(a1), G.to(a2))
    }

    implicit def hcons[H, T <: HList](
      implicit H: Lazy[Equal[H]],
      T: DerivedEqual[T]
    ): DerivedEqual[H :: T] = new DerivedEqual[H :: T] {
      def equal(ht1: H :: T, ht2: H :: T) =
        (quick(ht1.head, ht2.head) || H.value.equal(ht1.head, ht2.head)) &&
          T.equal(ht1.tail, ht2.tail)
    }

    implicit val hnil: DerivedEqual[HNil] = new DerivedEqual[HNil] {
      def equal(@unused h1: HNil, @unused h2: HNil) = true
    }

    implicit def ccons[H, T <: Coproduct](
      implicit H: Lazy[Equal[H]],
      T: DerivedEqual[T]
    ): DerivedEqual[H :+: T] = new DerivedEqual[H :+: T] {
      def equal(ht1: H :+: T, ht2: H :+: T) = (ht1, ht2) match {
        case (Inl(c1), Inl(c2)) => quick(c1, c2) || H.value.equal(c1, c2)
        case (Inr(c1), Inr(c2)) => T.equal(c1, c2)
        case _                  => false
      }
    }

    implicit val cnil: DerivedEqual[CNil] = new DerivedEqual[CNil] {
      def equal(@unused c1: CNil, @unused c2: CNil) = sys.error("impossible")
    }

    @inline private final def quick(a: Any, b: Any): Boolean =
      a.asInstanceOf[AnyRef].eq(b.asInstanceOf[AnyRef])
  }
```

Mientras estábamos haciendo esto, optimizamos usando el atajo `quick` de `scalaz-deriving`.

Ahora podemos llamar

```scala
  assert(tree1 /== tree2)
```

sin tener una excepción en tiempo de ejecución.

### Ejemplo: `Default`

Hay dos trampas en la implementación de una typeclass con un parámetro de tipo
en posición covariante. Aquí creamos valores `HList` y `Coproducto`, y debemos
proporcionar un valor para el caso `CNil` dado que corresponde al caso donde
ningún coproducto es capaz de proporcionar un valor.

```scala
  sealed trait DerivedDefault[A] extends Default[A]
  object DerivedDefault {
    def gen[A, R](
      implicit G: Generic.Aux[A, R],
      R: Cached[Strict[DerivedDefault[R]]]
    ): Default[A] = new Default[A] {
      def default = R.value.value.default.map(G.from)
    }

    implicit def hcons[H, T <: HList](
      implicit H: Lazy[Default[H]],
      T: DerivedDefault[T]
    ): DerivedDefault[H :: T] = new DerivedDefault[H :: T] {
      def default =
        for {
          head <- H.value.default
          tail <- T.default
        } yield head :: tail
    }

    implicit val hnil: DerivedDefault[HNil] = new DerivedDefault[HNil] {
      def default = HNil.right
    }

    implicit def ccons[H, T <: Coproduct](
      implicit H: Lazy[Default[H]],
      T: DerivedDefault[T]
    ): DerivedDefault[H :+: T] = new DerivedDefault[H :+: T] {
      def default = H.value.default.map(Inl(_)).orElse(T.default.map(Inr(_)))
    }

    implicit val cnil: DerivedDefault[CNil] = new DerivedDefault[CNil] {
      def default = "not a valid coproduct".left
    }
  }
```

Así como pudimos establecer una analogía entre `Equal` y `Decidable`, podemos
ver la relación con `Alt` en `.point` (`hnil`), `.apply2` (`.hcons`) y `.altly2`
(`.ccons`).

Hay poco que aprender de un ejemplo como `Semigroup`, de modo que iremos
directamente a estudiar los codificadores y decodificadores.

### Ejemplo: `JsEncoder`

Para ser capaces de reproducir nuestro codificador JSON de Magnolia, debemos ser
capaces de acceder a

1. los nombres de los campos y de las clases
2. las anotaciones para las preferencias del usuario
3. los valores por defecto en una `case class`

Empezaremos al crear un codificador que maneje únicamente los valores por
defecto.

Para obtener los nombres de los campos, usamos `LabelledGeneric` en lugar de
`Generic`, y cuando definimos el tipo del elemento en la cabeza, usamos
`FieldType[K, H]` en lugar de simplemente `H`. Un `Witness.Aux[K]` proporciona
el valor del nombre del campo en tiempo de ejecución.

Todos nuestros métodos van a devolver `JsObject`, de modo que en lugar de
devolver un `JsValue` podemos especializar y crear `DerivedJsEncoder` que tiene
una firma de tipo distinta a la de `JsEncoder`

```scala
  import shapeless._, labelled._

  sealed trait DerivedJsEncoder[R] {
    def toJsFields(r: R): IList[(String, JsValue)]
  }
  object DerivedJsEncoder {
    def gen[A, R](
      implicit G: LabelledGeneric.Aux[A, R],
      R: Cached[Strict[DerivedJsEncoder[R]]]
    ): JsEncoder[A] = new JsEncoder[A] {
      def toJson(a: A) = JsObject(R.value.value.toJsFields(G.to(a)))
    }

    implicit def hcons[K <: Symbol, H, T <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[T]
    ): DerivedJsEncoder[FieldType[K, H] :: T] =
      new DerivedJsEncoder[A, FieldType[K, H] :: T] {
        private val field = K.value.name
        def toJsFields(ht: FieldType[K, H] :: T) =
          ht match {
            case head :: tail =>
              val rest = T.toJsFields(tail)
              H.value.toJson(head) match {
                case JsNull => rest
                case value  => (field -> value) :: rest
              }
          }
      }

    implicit val hnil: DerivedJsEncoder[HNil] =
      new DerivedJsEncoder[HNil] {
        def toJsFields(h: HNil) = IList.empty
      }

    implicit def ccons[K <: Symbol, H, T <: Coproduct](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[T]
    ): DerivedJsEncoder[FieldType[K, H] :+: T] =
      new DerivedJsEncoder[FieldType[K, H] :+: T] {
        private val hint = ("type" -> JsString(K.value.name))
        def toJsFields(ht: FieldType[K, H] :+: T) = ht match {
          case Inl(head) =>
            H.value.toJson(head) match {
              case JsObject(fields) => hint :: fields
              case v                => IList.single("xvalue" -> v)
            }

          case Inr(tail) => T.toJsFields(tail)
        }
      }

    implicit val cnil: DerivedJsEncoder[CNil] =
      new DerivedJsEncoder[CNil] {
        def toJsFields(c: CNil) = sys.error("impossible")
      }

  }
```

A> Un patrón ha emergido en muchas librerías de derivación para Shapeless que
A> introduceen "pistas" con un `implicit` por defecto
A>
A> {lang="text"}
A> ~~~~~~~~
A>   trait ProductHint[A] {
A>     def nulls(field: String): Boolean
A>     def fieldname(field: String): String
A>   }
A>   object ProductHint {
A>     implicit def default[A]: ProductHint[A] = new ProductHint[A] {
A>       def nulls(field: String)     = false
A>       def fieldname(field: String) = field
A>     }
A>   }
A> ~~~~~~~~
A>
A> Los usuarios deben proporcionar una instancia personalizada de `ProductHint`
A> en sus objetos compañeros u objetos paquetee. Esta es una **mala idea** que
A> depende en el ordenamiento frágil de implícitos y es una fuente de
A> incoherencia de typeclasses: Si derivamos un `JsEncoder[Foo]`, obtendremos un
A> resultado distinto dependiendo de cuál `ProductHint[Foo]` esté en el alcance.
A> Es mejor evitar esta situación.

Shapeless selecciona los caminos de código en tiempo de compilación basándose en
la presencia de anotaciones, lo que puede llevarnos a código más optimizado, a
expensas de repetición de código. Esto significa que el número de anotaciones
con las que estamos lidiando, y sus subtipos debe ser manejable o nos
encontraremos escribiendo una cantidad de código unas diez veces más grande.
Podemos poner nuestras tres anotaciones en una sola que contenga todos los
parámetros de optimización:

```scala
 case class json(
    nulls: Boolean,
    field: Option[String],
    hint: Option[String]
  ) extends Annotation
```

Todos los usuarios de nuestra anotación deben proporcionar los tres valores dado
que los valores por defecto y los métodos de conveniencia no están disponibles
en los constructores de anotaciones. Podemos escribir extractores personalizados
de modo que no tengamos que cambiar nuestro código de Magnolia

```scala
  object json {
    object nulls {
      def unapply(j: json): Boolean = j.nulls
    }
    object field {
      def unapply(j: json): Option[String] = j.field
    }
    object hint {
      def unapply(j: json): Option[String] = j.hint
    }
  }
```

Podemos solicitar `Annotation[json, A]` para una `case class` o un `sealed
trait` para obtener acceso a la anotación, pero entonces debemos escribir un
`hcons` y un `ccons` que lidien con ambos casos debido a que la evidencia no
será generada si no está presente la anotación. Por lo tanto tenemos que
introducir un alcance implícito de prioridad más baja y poner la evidencia de
que no hay anotaciones ahí.

También podríamos solicitar evidencia `Annotations.Aux[json, A, J]` para obtener
un `HList` de la anotación `json` para el tipo `A`. De nuevo, debemos
proporcionar `hcons` y `ccons` que lidian con los casos donde hay y no hay una
anotación.

Para soportar esta anotación, ¡debemos escribir cuatro veces la cantidad de
código que antes!

Empezaremos por reescribir el `JsEncoder`, únicamente manejando código del
usuario que no tenga ninguna anotación. Ahora cualquier código que use la
anotación `@json` fallará en compilar, y esta es una buena garantía de
seguridad.

Debemos agregar tipos `A` y `J` al `DerivedJsEncoder` y pasarlos a través de las
anotaciones en su método `.toJsObject`. Nuestra evidencia `.hcons` y `.ccons`
ahora proporciona instancias para `DerivedJsEEncoder` con una anotación
`None.type` y las movemos a una prioridad más baja de modo que podamos lidiar
con `Annotation[json, A]` en una prioridad más alta.

Note que la evidencia para `J` está listada antes que la correspondiente a `R`.
Esto es importante, dado que el compilador debe primero fijar el tipo de `J`
antes de que pueda resolver el tipo para `R`.

```scala
  sealed trait DerivedJsEncoder[A, R, J <: HList] {
    def toJsFields(r: R, anns: J): IList[(String, JsValue)]
  }
  object DerivedJsEncoder extends DerivedJsEncoder1 {
    def gen[A, R, J <: HList](
      implicit
      G: LabelledGeneric.Aux[A, R],
      J: Annotations.Aux[json, A, J],
      R: Cached[Strict[DerivedJsEncoder[A, R, J]]]
    ): JsEncoder[A] = new JsEncoder[A] {
      def toJson(a: A) = JsObject(R.value.value.toJsFields(G.to(a), J()))
    }

    implicit def hnil[A]: DerivedJsEncoder[A, HNil, HNil] =
      new DerivedJsEncoder[A, HNil, HNil] {
        def toJsFields(h: HNil, a: HNil) = IList.empty
      }

    implicit def cnil[A]: DerivedJsEncoder[A, CNil, HNil] =
      new DerivedJsEncoder[A, CNil, HNil] {
        def toJsFields(c: CNil, a: HNil) = sys.error("impossible")
      }
  }
  private[jsonformat] trait DerivedJsEncoder1 {
    implicit def hcons[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, None.type :: J] =
      new DerivedJsEncoder[A, FieldType[K, H] :: T, None.type :: J] {
        private val field = K.value.name
        def toJsFields(ht: FieldType[K, H] :: T, anns: None.type :: J) =
          ht match {
            case head :: tail =>
              val rest = T.toJsFields(tail, anns.tail)
              H.value.toJson(head) match {
                case JsNull => rest
                case value  => (field -> value) :: rest
              }
          }
      }

    implicit def ccons[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J] =
      new DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J] {
        private val hint = ("type" -> JsString(K.value.name))
        def toJsFields(ht: FieldType[K, H] :+: T, anns: None.type :: J) =
          ht match {
            case Inl(head) =>
              H.value.toJson(head) match {
                case JsObject(fields) => hint :: fields
                case v                => IList.single("xvalue" -> v)
              }
            case Inr(tail) => T.toJsFields(tail, anns.tail)
          }
      }
  }
```

Ahora podemos agregar las firmas de tipo para los seis métodos nuevos, cubriendo
todas las posibilidades para los lugares posibles donde puede ocurrir la
anotación. Note que únicamente soportamos **una** anotación en cada posición. Si
el usuario proporciona múltiples anotaciones, cualquiera después de la primera
será ignorada silenciosamente.

Ya nos estamos quedando sin nombres para las cosas, de modo que llamaremos
arbitrariamente `Annotated` cuando exista una anotación para la `A`, y `Custom`
cuando exista una anotación sobre el campo.

```scala
  object DerivedJsEncoder extends DerivedJsEncoder1 {
    ...
    implicit def hconsAnnotated[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, None.type :: J]

    implicit def cconsAnnotated[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J]

    implicit def hconsAnnotatedCustom[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, Some[json] :: J]

    implicit def cconsAnnotatedCustom[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      A: Annotation[json, A],
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J]
  }
  private[jsonformat] trait DerivedJsEncoder1 {
    ...
    implicit def hconsCustom[A, K <: Symbol, H, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :: T, Some[json] :: J] = ???

    implicit def cconsCustom[A, K <: Symbol, H, T <: Coproduct, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J]
  }
```

En realidad no necesitamos `.hconsAnnotated` o `.hconsAnnotatedCustom` para
nada, dado que una anotación en una `case class` no significa nada para la
codificación de dicho producto, y sólo se usa en `.cconsAnnotated*`. Por lo
tanto, podemos borrar dos métodos.

`.cconsAnnotated` y `.cconsAnnotatedCustom` pueden definirse como

```scala
  new DerivedJsEncoder[A, FieldType[K, H] :+: T, None.type :: J] {
    private val hint = A().field.getOrElse("type") -> JsString(K.value.name)
    def toJsFields(ht: FieldType[K, H] :+: T, anns: None.type :: J) = ht match {
      case Inl(head) =>
        H.value.toJson(head) match {
          case JsObject(fields) => hint :: fields
          case v                => IList.single("xvalue" -> v)
        }
      case Inr(tail) => T.toJsFields(tail, anns.tail)
    }
  }
```

y

```scala
  new DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J] {
    private val hintfield = A().field.getOrElse("type")
    def toJsFields(ht: FieldType[K, H] :+: T, anns: Some[json] :: J) = ht match {
      case Inl(head) =>
        val ann = anns.head.get
        H.value.toJson(head) match {
          case JsObject(fields) =>
            val hint = (hintfield -> JsString(ann.hint.getOrElse(K.value.name)))
            hint :: fields
          case v =>
            val xvalue = ann.field.getOrElse("xvalue")
            IList.single(xvalue -> v)
        }
      case Inr(tail) => T.toJsFields(tail, anns.tail)
    }
  }
```

Podría preocupar un poco el uso de `.head` y `.get` pero recuerde que los tipos
aquí son `::` y `Some`, y por lo tanto estos métodos son totales y seguros de
usarse.

`.hconsCustom` y `.cconsCustom` se escriben

```scala
  new DerivedJsEncoder[A, FieldType[K, H] :: T, Some[json] :: J] {
    def toJsFields(ht: FieldType[K, H] :: T, anns: Some[json] :: J) = ht match {
      case head :: tail =>
        val ann  = anns.head.get
        val next = T.toJsFields(tail, anns.tail)
        H.value.toJson(head) match {
          case JsNull if !ann.nulls => next
          case value =>
            val field = ann.field.getOrElse(K.value.name)
            (field -> value) :: next
        }
    }
  }
```

y

```scala
  new DerivedJsEncoder[A, FieldType[K, H] :+: T, Some[json] :: J] {
    def toJsFields(ht: FieldType[K, H] :+: T, anns: Some[json] :: J) = ht match {
      case Inl(head) =>
        val ann = anns.head.get
        H.value.toJson(head) match {
          case JsObject(fields) =>
            val hint = ("type" -> JsString(ann.hint.getOrElse(K.value.name)))
            hint :: fields
          case v =>
            val xvalue = ann.field.getOrElse("xvalue")
            IList.single(xvalue -> v)
        }
      case Inr(tail) => T.toJsFields(tail, anns.tail)
    }
  }
```

Obviamente, hay mucho código repetitivo y verboso, pero observando de cerca
podemos ver que cada método está implementado tan eficientemente como sea
posible con la información que tiene: los caminos del código se seleccionan en
tiempo de compilación más bien que en tiempo de ejecución.

Los que sean obsesivos con el rendimiento tal vez deseen refactorizar este
código de modo que toda la información de anotaciones esté disponible de
antemano, más bien que inyectada por medio del método `.toJsFields`, con otra
capa de indirección. Para un rendimiento máximo, también podríamos tratar cada
personalización como una anotación separada, pero eso multiplicaría la cantidad
de código que hemos escrito todavía más, con un coste adicional para el tiempo
de compilación requerido a nuestros usuarios. Tales optimizaciones están más
allá del alcance de este libro, pero es posible y las personas las hacen: la
habilidad de mover trabajo de tiempo de ejecución a tiempo de compilación es una
de las cosas más atractivas de la programación genérica.

Una advertencia más de la que tenemos que estar conscientes: [`LabelledGeneric`
no es compatible con
`scalaz.@@`](https://github.com/milessabin/shapeless/issues/309), pero existe
una solución. Digamos que efectivamente deseamos ignorar las etiquetas de modo
que agregamos las siguientes reglas de derivación a los objetos compañeros de
nuestro codificador y decodificador

```scala
  object JsEncoder {
    ...
    implicit def tagged[A: JsEncoder, Z]: JsEncoder[A @@ Z] =
      JsEncoder[A].contramap(Tag.unwrap)
  }
  object JsDecoder {
    ...
    implicit def tagged[A: JsDecoder, Z]: JsDecoder[A @@ Z] =
      JsDecoder[A].map(Tag(_))
  }
```

Esperaríamos ser capaces de derivar un `JsDecoder` para algo como nuestra `TradeTemplate` del Capítulo 5

```scala
  final case class TradeTemplate(
    otc: Option[Boolean] @@ Tags.Last
  )
  object TradeTemplate {
    implicit val encoder: JsEncoder[TradeTemplate] = DerivedJsEncoder.gen
  }
```

Pero obtenemos el siguiente error de compilación

```text
  [error] could not find implicit value for parameter G: LabelledGeneric.Aux[A,R]
  [error]   implicit val encoder: JsEncoder[TradeTemplate] = DerivedJsEncoder.gen
  [error]                                                                     ^
```

El mensaje de error es tan útil como siempre. La solución es introducir
evidencia para `H @@ Z` en el alcance implícito de menor prioridad, y entonces
simplemente invocar el código que el compilador habría encontrado en primer
lugar:

```scala
  object DerivedJsEncoder extends DerivedJsEncoder1 with DerivedJsEncoder2 {
    ...
  }
  private[jsonformat] trait DerivedJsEncoder2 {
    this: DerivedJsEncoder.type =>

    // WORKAROUND https://github.com/milessabin/shapeless/issues/309
    implicit def hconsTagged[A, K <: Symbol, H, Z, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H @@ Z]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H @@ Z] :: T, None.type :: J] = hcons(K, H, T)

    implicit def hconsCustomTagged[A, K <: Symbol, H, Z, T <: HList, J <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsEncoder[H @@ Z]],
      T: DerivedJsEncoder[A, T, J]
    ): DerivedJsEncoder[A, FieldType[K, H @@ Z] :: T, Some[json] :: J] = hconsCustom(K, H, T)
  }
```

Felizmente, sólo es necesario considerar los productos, dado que no es posible
agregar etiquetas a los coproductos.

### `JsDecoder`

El lado de la decodificación es tal como lo esperaríamos basándonos en los
ejemplos previos. Podemos construir una instancia de un `FieldType[K, H` con el
auxiliar `field[K](h: H)`. Soportando únicamente los razonables valores por
defecto significa que escribimos:

```scala
  sealed trait DerivedJsDecoder[A] {
    def fromJsObject(j: JsObject): String \/ A
  }
  object DerivedJsDecoder {
    def gen[A, R](
      implicit G: LabelledGeneric.Aux[A, R],
      R: Cached[Strict[DerivedJsDecoder[R]]]
    ): JsDecoder[A] = new JsDecoder[A] {
      def fromJson(j: JsValue) = j match {
        case o @ JsObject(_) => R.value.value.fromJsObject(o).map(G.from)
        case other           => fail("JsObject", other)
      }
    }

    implicit def hcons[K <: Symbol, H, T <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H]],
      T: DerivedJsDecoder[T]
    ): DerivedJsDecoder[FieldType[K, H] :: T] =
      new DerivedJsDecoder[FieldType[K, H] :: T] {
        private val fieldname = K.value.name
        def fromJsObject(j: JsObject) = {
          val value = j.get(fieldname).getOrElse(JsNull)
          for {
            head  <- H.value.fromJson(value)
            tail  <- T.fromJsObject(j)
          } yield field[K](head) :: tail
        }
      }

    implicit val hnil: DerivedJsDecoder[HNil] = new DerivedJsDecoder[HNil] {
      private val nil               = HNil.right[String]
      def fromJsObject(j: JsObject) = nil
    }

    implicit def ccons[K <: Symbol, H, T <: Coproduct](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H]],
      T: DerivedJsDecoder[T]
    ): DerivedJsDecoder[FieldType[K, H] :+: T] =
      new DerivedJsDecoder[FieldType[K, H] :+: T] {
        private val hint = ("type" -> JsString(K.value.name))
        def fromJsObject(j: JsObject) =
          if (j.fields.element(hint)) {
            j.get("xvalue")
              .into {
                case \/-(xvalue) => H.value.fromJson(xvalue)
                case -\/(_)      => H.value.fromJson(j)
              }
              .map(h => Inl(field[K](h)))
          } else
            T.fromJsObject(j).map(Inr(_))
      }

    implicit val cnil: DerivedJsDecoder[CNil] = new DerivedJsDecoder[CNil] {
      def fromJsObject(j: JsObject) = fail(s"JsObject with 'type' field", j)
    }
  }
```

Agregar las preferencias del usuario usando anotaciones sigue la misma ruta que
en el caso de `DerivedJsEncoder` y es mecánico, de modo que se deja como un
ejercicio al lector.

Algo más falta: valores por defecto para las `case class`. Podríamos solicitar
evidencia, pero el gran problema es que ya no podemos usar el mismo mecanismo de
derivación para los productos y coproductos: la evidencia nunca se crea para
coproductos.

La solución es bastante drástica. Debemos separar nuestro `DerivedJsDecoder` en
`DerivedCoproductJsDecoder` y `DerivedProductJsDecoder`. Nos enfocaremos en el
`DerivedProductJsDecoder`, y mientras es que estamoos en esto usaremos un `Map`
para encontrar más rápidamente los campos:

```scala
  sealed trait DerivedProductJsDecoder[A, R, J <: HList, D <: HList] {
    private[jsonformat] def fromJsObject(
      j: Map[String, JsValue],
      anns: J,
      defaults: D
    ): String \/ R
  }
```

Podemos solicitar evidencia de que existen valores por defecto con
`Default.Aux[A, D]` y duplicar todos los métodos para lidiar con el caso donde
se tiene y no se tiene un valor por defecto. Sin embargo, aquí Shapeless tiene
piedad y proporciona `Default.AsOptions.Aux[A, D]` dejándonos manejar los
valores por defecto en tiempo de ejecución.

```scala
  object DerivedProductJsDecoder {
    def gen[A, R, J <: HList, D <: HList](
      implicit G: LabelledGeneric.Aux[A, R],
      J: Annotations.Aux[json, A, J],
      D: Default.AsOptions.Aux[A, D],
      R: Cached[Strict[DerivedProductJsDecoder[A, R, J, D]]]
    ): JsDecoder[A] = new JsDecoder[A] {
      def fromJson(j: JsValue) = j match {
        case o @ JsObject(_) =>
          R.value.value.fromJsObject(o.fields.toMap, J(), D()).map(G.from)
        case other => fail("JsObject", other)
      }
    }
    ...
  }
```

Debemos mover los métodos `.hcons` y `.hnil` al objeto compañero de la nueva
typeclass sellada, que puede manejar valores por defecto

```scala
  object DerivedProductJsDecoder {
    ...
      implicit def hnil[A]: DerivedProductJsDecoder[A, HNil, HNil, HNil] =
      new DerivedProductJsDecoder[A, HNil, HNil, HNil] {
        private val nil = HNil.right[String]
        def fromJsObject(j: StringyMap[JsValue], a: HNil, defaults: HNil) = nil
      }

    implicit def hcons[A, K <: Symbol, H, T <: HList, J <: HList, D <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H]],
      T: DerivedProductJsDecoder[A, T, J, D]
    ): DerivedProductJsDecoder[A, FieldType[K, H] :: T, None.type :: J, Option[H] :: D] =
      new DerivedProductJsDecoder[A, FieldType[K, H] :: T, None.type :: J, Option[H] :: D] {
        private val fieldname = K.value.name
        def fromJsObject(
          j: StringyMap[JsValue],
          anns: None.type :: J,
          defaults: Option[H] :: D
        ) =
          for {
            head <- j.get(fieldname) match {
                     case Maybe.Just(v) => H.value.fromJson(v)
                     case _ =>
                       defaults.head match {
                         case Some(default) => \/-(default)
                         case None          => H.value.fromJson(JsNull)
                       }
                   }
            tail <- T.fromJsObject(j, anns.tail, defaults.tail)
          } yield field[K](head) :: tail
      }
    ...
  }
```

Ahora ya no podemos usar `@deriving` para productos y coproductos: sólo puede
haber una entrada en el archivo `deriving.conf`.

¡Oh! Y no olvide agregar soporte para `@@`

```scala
  object DerivedProductJsDecoder extends DerivedProductJsDecoder1 {
    ...
  }
  private[jsonformat] trait DerivedProductJsDecoder2 {
    this: DerivedProductJsDecoder.type =>

    implicit def hconsTagged[
      A, K <: Symbol, H, Z, T <: HList, J <: HList, D <: HList
    ](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H @@ Z]],
      T: DerivedProductJsDecoder[A, T, J, D]
    ): DerivedProductJsDecoder[
      A,
      FieldType[K, H @@ Z] :: T,
      None.type :: J,
      Option[H @@ Z] :: D
    ] = hcons(K, H, T)

    implicit def hconsCustomTagged[
      A, K <: Symbol, H, Z, T <: HList, J <: HList, D <: HList
    ](
      implicit
      K: Witness.Aux[K],
      H: Lazy[JsDecoder[H @@ Z]],
      T: DerivedProductJsDecoder[A, T, J, D]
    ): DerivedProductJsDecoder[
      A,
      FieldType[K, H @@ Z] :: T,
      Some[json] :: J,
      Option[H @@ Z] :: D
    ] = hconsCustomTagged(K, H, T)
  }
```

### Derivaciones complicadas

Shapeless nos permite muchos más tipos de derivaciones de las que son posibles
con `scalaz-deriving` o Magnolia. Como un ejemplo de un
codificador/decodificador que es posible con Magnolia, considere este modelo XML
de
[`xmlformat`](https://github.com/scalaz/scalaz-deriving/tree/master/examples/xmlformat)

```scala
  @deriving(Equal, Show, Arbitrary)
  sealed abstract class XNode

  @deriving(Equal, Show, Arbitrary)
  final case class XTag(
    name: String,
    attrs: IList[XAttr],
    children: IList[XTag],
    body: Maybe[XString]
  )

  @deriving(Equal, Show, Arbitrary)
  final case class XAttr(name: String, value: XString)

  @deriving(Show)
  @xderiving(Equal, Monoid, Arbitrary)
  final case class XChildren(tree: IList[XTag]) extends XNode

  @deriving(Show)
  @xderiving(Equal, Semigroup, Arbitrary)
  final case class XString(text: String) extends XNode
```

Dada la naturaleza de XML tiene sentido que se tengan pares de
codificador/decodificador separados para `XChildren` y contendido `XString`.
Podríamos proporcionar una derivación para el `XChildren` con Shapeless pero
queremos campos con uso de mayúsculas y minúsculas basado en la clase de
typeclass que tengan, así como campos con `Option`. Podríamos incluso requerir
que los campos estén anotados con su nombre codificado. Además, cuando se
decodifique desearíamos tener diferentes estrategias para manejar los cuerpos de
los elementos XML, que pueden tener múltiples partes, dependiendo de si su tipo
tiene un `Semigroup`, `Monoid` o ninguno.

A> Muchos desarrolladores creen que XML es simplemente una forma más verbosa de
A> JSON, con corchetes angulares en lugar de llaves. Sin embargo, un intento de
A> escribir un convertidor de ida y regreso entre `XNode` y `JsValue` debería
A> convencernos de que JSON y XML son especies distintas, cuyas conversiones son
A> posibles únicamente sobre la base de caso por caso.

### Ejemplo: `UrlQueryWriter`

De manera similar a `xmlformat`, nuestra aplicación `drone-dynamic-agents` podría beneficiarse de una derivación de typeclasses de la typeclass `UrlQueryWriter`, que está construida a partir de instancias de `UrlEncodedWriter` para cada campo de entrada. No soporta coproductos:

```scala
  @typeclass trait UrlQueryWriter[A] {
    def toUrlQuery(a: A): UrlQuery
  }
  trait DerivedUrlQueryWriter[T] extends UrlQueryWriter[T]
  object DerivedUrlQueryWriter {
    def gen[T, Repr](
      implicit
      G: LabelledGeneric.Aux[T, Repr],
      CR: Cached[Strict[DerivedUrlQueryWriter[Repr]]]
    ): UrlQueryWriter[T] = { t =>
      CR.value.value.toUrlQuery(G.to(t))
    }

    implicit val hnil: DerivedUrlQueryWriter[HNil] = { _ =>
      UrlQuery(IList.empty)
    }
    implicit def hcons[Key <: Symbol, A, Remaining <: HList](
      implicit Key: Witness.Aux[Key],
      LV: Lazy[UrlEncodedWriter[A]],
      DR: DerivedUrlQueryWriter[Remaining]
    ): DerivedUrlQueryWriter[FieldType[Key, A] :: Remaining] = {
      case head :: tail =>
        val first =
          Key.value.name -> URLDecoder.decode(LV.value.toUrlEncoded(head).value, "UTF-8")
        val rest = DR.toUrlQuery(tail)
        UrlQuery(first :: rest.params)
    }
  }
```

Es razonable preguntarse si estas 30 lineas en realidad son una mejora sobre las
8 lineas para las 2 instancias manuales que nuestra aplicación necesita: una
decisión que debe tomarse caso por caso.

En aras de la exhaustividad, la derivación `UrlEncodedWriter` puede escribirse
con Magnolia

```scala
  object UrlEncodedWriterMagnolia {
    type Typeclass[a] = UrlEncodedWriter[a]
    def combine[A](ctx: CaseClass[UrlEncodedWriter, A]) = a =>
      Refined.unsafeApply(ctx.parameters.map { p =>
        p.label + "=" + p.typeclass.toUrlEncoded(p.dereference(a))
      }.toList.intercalate("&"))
    def gen[A]: UrlEncodedWriter[A] = macro Magnolia.gen[A]
  }
```

### El lado oscuro de la derivación

> "Cuidado hay que tener de la derivación automática. Furia, miedo, agresión; el
> lado oscuro de la derivación son. Fluirán ellos fácilmente, rápidos serán para
> unirse a ti en pelea. Si alguna vez comienzas el camino oscuro, por siempre
> dominarán tu compilador, y consumirte logrará."
>
> - un antiguo maestro de Shapeless

Además de todas las advertencias sobre la derivación automática que fueron
mencionadas para Magnolia, Shapeless es **mucho** peor. La derivación de
Shapeless completamente automática no es únicamente [la causa más común de
compilación
lenta](https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html),
también es una fuente dolorosa de errores de coherencia de typeclasses.

La derivación completamente automática ocurre cuando el `def gen` es implícito
de modo que una invocación realizará una recursión por todas las entradas de la
ADT. Debido a la forma en la que funcionan los alcances implícitos, un `implicit
def` importado tendrá una prioridad más alta que las instancias personalizadas
en los objetos compañeros, creando una fuente de incoherencia de typeclasses.
Por ejemplo, considere este código si nuestro `.gen` fuera implícito

```scala
  import DerivedJsEncoder._

  @xderiving(JsEncoder)
  final case class Foo(s: String)
  final case class Bar(foo: Foo)
```

Esperaríamos que la forma codificada derivada de manera completamente automática
se viera como

```json
  {
    "foo":"hello"
  }
```

debido a que hemos usado `xderiving` para `Foo`. Pero podría más bien ser

```json
  {
    "foo": {
      "s":"hello"
    }
  }
```

Es peor aún cuando se agregan métodos al objeto compañero de la typeclass,
resultando en que la typeclass siempre se deriva en el punto de uso y los
usuarios no pueden optar por no realizarla.

Fundamentalmente, cuando escriba programas genéricos, los implícitos pueden
ignorarse por el compilador dependiendo del alcance, ¡resultando en que perdemos
la seguridad en tiempo de compilación que era nuestra motivación para programar
a nivel de tipos desde el principio!

Todo es mucho más simple en el lado claro, donde `implicit` se usa para
typeclasses coherentes, globalmente únicas. El miedo al código repetitivo es el
camino al lado oscuro. El miedo lleva a la furia. La furia lleva al odio. El
odio lleva al sufrimiento.

## Rendimiento

No hay bala plateada en lo que respecta a la derivación de typeclasses. Un eje
que hay que considerar es el del rendimiento: tanto en tiempo de compilación
como en tiempo de ejecución.

### Tiempo de compilación

Cuando se trata de tiempos de compilación, Shapeless es un caso extremo. No es
poco común ver que un proyecto pequeño expanda su tiempo de compilación de un
segundo a un minuto. Para investigar temas de compilación, podemos realizar un
perfilado de código con el plugin `scalac-profiling`

```scala
  addCompilerPlugin("ch.epfl.scala" %% "scalac-profiling" % "1.0.0")
  scalacOptions ++= Seq("-Ystatistics:typer", "-P:scalac-profiling:no-profiledb")
```

Este produce una salida que puede generar un *gráfico de llamas*.

Para el caso de una derivación de Shapeless, obtenemos una gráfica vívida

{width=90%}
![](images/implicit-flamegraph-jsonformat-jmh.png)

casi todo el tiempo de compilación se usa en la resolución implícita. Note que
esto también incluye la compilación de las instancias de `scalaz-deriving`,
Magnolia y manuales, pero los cómputos de Shapeless dominan.

Este es el caso cuando la derivación funciona. Si hay un problema con la
derivación de Shapeless, el compilador puede quedarse en un ciclo infinito y
debe ser terminado.

### Rendimiento en tiempo de ejecución

Si ahora consideramos el rendimiento en tiempo de ejecución, la respuesta
siempre es: *depende*.

Suponiendo que la lógica de derivación ha sido escrita de una manera eficiente,
sólo es posible saber cuál es más rápida a través de la experimentación.

La librería `jsonformat` usa el [Java Microbenchmark Harness
(JMH)](https://openjdk.java.net/projects/code-tools/jmh) sobre modelos que
mapean a GeoJSON, Google Maps, y Twitter, contribuidos por Andriy Plokhotnyuk.
Hay tres pruebas por modelo:

- codificar el `ADT` a un `JsValue`
- una decodificación exitosa del mismo valor `JsValue` al ADT
- una decodificación fallida de un `JsValue` con un error de datos

aplicada a las siguientes implementaciones:

- Magnolia
- Shapeless
- manualmente escrita

con las optimizaciones equivalentes en cada una. Los resultados son en operaciones por segundo (más alto es mejor), en una poderosa computadora de escritorio, usando un único hilo:

```text
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*encode*
  Benchmark                                 Mode  Cnt       Score      Error  Units

  GeoJSONBenchmarks.encodeMagnolia         thrpt    5   70527.223 ±  546.991  ops/s
  GeoJSONBenchmarks.encodeShapeless        thrpt    5   65925.215 ±  309.623  ops/s
  GeoJSONBenchmarks.encodeManual           thrpt    5   96435.691 ±  334.652  ops/s

  GoogleMapsAPIBenchmarks.encodeMagnolia   thrpt    5   73107.747 ±  439.803  ops/s
  GoogleMapsAPIBenchmarks.encodeShapeless  thrpt    5   53867.845 ±  510.888  ops/s
  GoogleMapsAPIBenchmarks.encodeManual     thrpt    5  127608.402 ± 1584.038  ops/s

  TwitterAPIBenchmarks.encodeMagnolia      thrpt    5  133425.164 ± 1281.331  ops/s
  TwitterAPIBenchmarks.encodeShapeless     thrpt    5   84233.065 ±  352.611  ops/s
  TwitterAPIBenchmarks.encodeManual        thrpt    5  281606.574 ± 1975.873  ops/s
```

Vemos que las implementaciones manuales son las que liderean la tabla, seguidas
por Magnolia, y con Shapeless de un 30% a un 70% de rendimiento con respecto a
las instancias manuales. Ahora para la decodificación

```text
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*decode.*Success
  Benchmark                                        Mode  Cnt       Score      Error  Units

  GeoJSONBenchmarks.decodeMagnoliaSuccess         thrpt    5   40850.270 ±  201.457  ops/s
  GeoJSONBenchmarks.decodeShapelessSuccess        thrpt    5   41173.199 ±  373.048  ops/s
  GeoJSONBenchmarks.decodeManualSuccess           thrpt    5  110961.246 ±  468.384  ops/s

  GoogleMapsAPIBenchmarks.decodeMagnoliaSuccess   thrpt    5   44577.796 ±  457.861  ops/s
  GoogleMapsAPIBenchmarks.decodeShapelessSuccess  thrpt    5   31649.792 ±  861.169  ops/s
  GoogleMapsAPIBenchmarks.decodeManualSuccess     thrpt    5   56250.913 ±  394.105  ops/s

  TwitterAPIBenchmarks.decodeMagnoliaSuccess      thrpt    5   55868.832 ± 1106.543  ops/s
  TwitterAPIBenchmarks.decodeShapelessSuccess     thrpt    5   47711.161 ±  356.911  ops/s
  TwitterAPIBenchmarks.decodeManualSuccess        thrpt    5   71962.394 ±  465.752  ops/s
```

Esta es una carrera más cerrada por el segundo lugar, con Shapeless y Magnolia
manteniendo el ritmo. Finalmente, la decodificación a partir de un `JsValue` que
contiene datos inválidos (en una posición intencionalmente torpe)

```text
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*decode.*Error
  Benchmark                                      Mode  Cnt        Score       Error  Units

  GeoJSONBenchmarks.decodeMagnoliaError         thrpt    5   981094.831 ± 11051.370  ops/s
  GeoJSONBenchmarks.decodeShapelessError        thrpt    5   816704.635 ±  9781.467  ops/s
  GeoJSONBenchmarks.decodeManualError           thrpt    5   586733.762 ±  6389.296  ops/s

  GoogleMapsAPIBenchmarks.decodeMagnoliaError   thrpt    5  1288888.446 ± 11091.080  ops/s
  GoogleMapsAPIBenchmarks.decodeShapelessError  thrpt    5  1010145.363 ±  9448.110  ops/s
  GoogleMapsAPIBenchmarks.decodeManualError     thrpt    5  1417662.720 ±  1197.283  ops/s

  TwitterAPIBenchmarks.decodeMagnoliaError      thrpt    5   128704.299 ±   832.122  ops/s
  TwitterAPIBenchmarks.decodeShapelessError     thrpt    5   109715.865 ±   826.488  ops/s
  TwitterAPIBenchmarks.decodeManualError        thrpt    5   148814.730 ±  1105.316  ops/s
```

Justo cuando pensamos que habíamos visto un patrón, en donde Magnolia y
Shapeless ganaban la carrera cuando decodificaban datos inválidos de GeoJSON,
entonces vemos que las instancias manuales ganan los retos de Google Maps y
Twitter.

Deseamos incluir a `scalaz-deriving` en la comparación, de modo que comparamos
una implementación equivalente de `Equal`, probada con dos valores que tienen el
mismo contenido (`True`) y dos valores que tienen contenidos ligeramente
distintos (`False`)

```text
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*equal*
  Benchmark                                     Mode  Cnt        Score       Error  Units

  GeoJSONBenchmarks.equalScalazTrue            thrpt    5   276851.493 ±  1776.428  ops/s
  GeoJSONBenchmarks.equalMagnoliaTrue          thrpt    5    93106.945 ±  1051.062  ops/s
  GeoJSONBenchmarks.equalShapelessTrue         thrpt    5   266633.522 ±  4972.167  ops/s
  GeoJSONBenchmarks.equalManualTrue            thrpt    5   599219.169 ±  8331.308  ops/s

  GoogleMapsAPIBenchmarks.equalScalazTrue      thrpt    5    35442.577 ±   281.597  ops/s
  GoogleMapsAPIBenchmarks.equalMagnoliaTrue    thrpt    5    91016.557 ±   688.308  ops/s
  GoogleMapsAPIBenchmarks.equalShapelessTrue   thrpt    5   107245.505 ±   468.427  ops/s
  GoogleMapsAPIBenchmarks.equalManualTrue      thrpt    5   302247.760 ±  1927.858  ops/s

  TwitterAPIBenchmarks.equalScalazTrue         thrpt    5    99066.013 ±  1125.422  ops/s
  TwitterAPIBenchmarks.equalMagnoliaTrue       thrpt    5   236289.706 ±  3182.664  ops/s
  TwitterAPIBenchmarks.equalShapelessTrue      thrpt    5   251578.931 ±  2430.738  ops/s
  TwitterAPIBenchmarks.equalManualTrue         thrpt    5   865845.158 ±  6339.379  ops/s
```

Como es de esperarse, las instancias manuales están bastante lejos del resto,
con Shapeless liderando las derivaciones automáticas, `scalaz-deriving` hace un
gran esfuerzo para GeoJSON, pero se queda bastante atrás tanto en la prueba de
Google Maps como en la de Twitter. Las pruebas `False` son más de lo mismo:

```text
  > jsonformat/jmh:run -i 5 -wi 5 -f1 -t1 -w1 -r1 .*equal*
  Benchmark                                     Mode  Cnt        Score       Error  Units

  GeoJSONBenchmarks.equalScalazFalse           thrpt    5    89552.875 ±   821.791  ops/s
  GeoJSONBenchmarks.equalMagnoliaFalse         thrpt    5    86044.021 ±  7790.350  ops/s
  GeoJSONBenchmarks.equalShapelessFalse        thrpt    5   262979.062 ±  3310.750  ops/s
  GeoJSONBenchmarks.equalManualFalse           thrpt    5   599989.203 ± 23727.672  ops/s

  GoogleMapsAPIBenchmarks.equalScalazFalse     thrpt    5    35970.818 ±   288.609  ops/s
  GoogleMapsAPIBenchmarks.equalMagnoliaFalse   thrpt    5    82381.975 ±   625.407  ops/s
  GoogleMapsAPIBenchmarks.equalShapelessFalse  thrpt    5   110721.122 ±   579.331  ops/s
  GoogleMapsAPIBenchmarks.equalManualFalse     thrpt    5   303588.815 ±  2562.747  ops/s

  TwitterAPIBenchmarks.equalScalazFalse        thrpt    5   193930.568 ±  1176.421  ops/s
  TwitterAPIBenchmarks.equalMagnoliaFalse      thrpt    5   429764.654 ± 11944.057  ops/s
  TwitterAPIBenchmarks.equalShapelessFalse     thrpt    5   494510.588 ±  1455.647  ops/s
  TwitterAPIBenchmarks.equalManualFalse        thrpt    5  1631964.531 ± 13110.291  ops/s
```

El rendimiento en tiempo de ejecución de `scalaz-deriving`, Magnolia y Shapeless
es normalmente lo suficientemente bueno. Debemos ser realistas: no estamos
escribiendo aplicaciones que deban ser capaces de codificar más de 130,000
valores a JSON, por segundo, en un único núcleo, en la JVM. Si esto es un
problema, probablemente deba usar C++.

Es poco probable que las instancias derivadas sean el cuello de botella de una
aplicación. Incluso si lo fueran, existe la posibilidad de usar las soluciones
manuales, que son más poderosas y por lo tanto más peligrosas: es fácil
introducir errores de dedo, de código, o incluso regresiones de rendimiento por
accidente cuando se escriben instancias manuales.

En conclusión: las derivaciones y las macros no son rivales para una instancia
manual bien escrita.

A> Podríamos pasar una vida con el
A> [`async-profiler`](https://github.com/jvm-profiling-tools/async-profiler)
A> investigando las gráficas de llamas del CPU y de la creación de objetos para
A> hacer cualquiera de estas implementaciones más rápida. Por ejemplo, hay
A> algunas optimizaciones en la base de código real de `jsonformat` que no se
A> reproducen aquí, tales como una búsqueda de campos `JsObject` más optimizada,
A> y la inclusión de `.xmap`, `.map` y `.contramap` en las typeclasses
A> relevantes, pero es justo decir que la base de código se enfoca en la
A> legibilidad sobre la optimización y de todas maneras consigue un rendimiento
A> increíble.

## Resumen

Cuando esté decidiendo en una tecnología para usar con derivación de
typeclasses, la siguiente gráfica puede ayudar

| Feature             | Scalaz | Magnolia | Shapeless        | Manual         |
|---------------------|--------|----------|------------------|----------------|
| `@deriving`         | sí     | sí       | sí               |                |
| Leyes               | sí     |          |                  |                |
| Compilación rápida  | sí     | sí       |                  | sí             |
| Nombres de campos   |        | sí       | sí               |                |
| Anotaciones         |        | sí       | parcialmente     |                |
| Valores por defecto |        | sí       | con advertencias |                |
| Complicada          |        |          | dolorosamente    |                |
| Rendimiento         |        |          |                  | no tengo rival |

Prefiera `scalaz-deriving` de ser posible, usando Magnolia para
codificadores/decodificadores o si el rendimiento es algo que deba considerar,
usando Shapeless únicamente si los tiempos de compilación no son una
preocupación.

Las instancias manuales siempre son una salida de emergencia para casos
especiales y para conseguir el mayor rendimiento. Evite introducir errores de
dedo al usar una herramienta de generación de código.

<!--  LocalWords:  Shapeless scalaz
 -->
