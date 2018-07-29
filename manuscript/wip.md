
# Typeclass Derivation

Typeclasses provide polymorphic functionality to our applications. But to use a
typeclass we need instances for our business domain objects.

The creation of a typeclass instance from existing instances is known as
*typeclass derivation* and is the topic of this chapter.

There are five approaches to typeclass derivation:

1.  Manual instances for every domain object. This is infeasible for real world
    applications as it results in hundreds of lines of code of boilerplate for
    every line of a `case class`. It is useful only for educational purposes.

2.  Write macros for each typeclass. This is not a maintainable solution because
    the macro API will change in Scala 3, and macros require an advanced and
    experienced developer to write each one.

3.  Many typeclasses (and algebras!) can be abstracted by an existing scalaz
    typeclass, producing automated tests of the typeclass itself and derivations
    for business domain objects. This is the approach of `scalaz-deriving`.

4.  Jon Pretty's [Magnolia](https://github.com/propensive/magnolia) macro provides a convenient API that lets typeclass
    authors support ADTs. It is effectively an abstraction over hand-rolled
    macros, with all the macro maintenance burden in a single shared library.

5.  Via generic programs with the [Shapeless](https://github.com/milessabin/shapeless/) library. The `implicit` mechanism is
    a language within the Scala language and can be used to write programs at the
    type level, incurring a significant compiler performance penalty.

In this chapter we will study increasingly complex typeclasses and their
derivations. We will begin with `scalaz-deriving` as the most principled
mechanism, repeating some lessons from Chapter 5 "Scalaz Typeclasses", then
Magnolia (the easiest to use) for encoder and decoder formats, finishing with
Shapeless (the most powerful) for typeclasses with complex derivation logic.


## Running Examples

This chapter will show how to define derivations for five specific typeclasses.
Each example exhibits a feature that can be generalised:

{lang="text"}
~~~~~~~~
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
~~~~~~~~

A> There is a school of thought that says serialisation formats, such as JSON and
A> XML, should **not** have typeclass encoders and decoders, because it can lead to
A> typeclass decoherence (i.e. more than one encoder or decoder may exist for the
A> same type). The alternative is to use algebras and avoid using the `implicit`
A> language feature entirely.
A> 
A> Although it is possible to apply the techniques in this chapter to either
A> typeclass or algebra derivation, the latter involves a ****lot**** more boilerplate.
A> We therefore consciously choose to restrict our study to encoders and decoders
A> that are coherent. As we will see later in this chapter, use-site automatic
A> derivation with magnolia and shapeless, combined with limitations of the scala
A> compiler's implicit search, commonly leads to typeclass decoherence.


## `scalaz-deriving`

The `scalaz-deriving` library is an extension to Scalaz and can be added to a
project's `build.sbt` with

{lang="text"}
~~~~~~~~
  val derivingVersion = "1.0.0-RC5"
  libraryDependencies += "com.fommil" %% "scalaz-deriving" % derivingVersion
~~~~~~~~

providing new typeclasses, shown below in relation to the core scalaz
typeclasses that are relevant to this chapter:

{width=60%}
![](images/scalaz-deriving-base.png)

A> In scalaz 7.3, `Applicative` and `Divisible` will inherit from `InvariantApplicative`

Before we proceed, here is a quick recap of the core scalaz typeclasses:

{lang="text"}
~~~~~~~~
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
    def apply3[A,B,C,D](fa: =>F[A],fb: =>F[B],fc: =>F[C])(f: (A,B,C) =>D): F[D] = ...
    ...
    def apply12[...]
  }
  
  @typeclass trait Monad[F[_]] extends Functor[F] {
    @op(">>=") def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
  }
  @typeclass trait MonadError[F[_], E] extends Monad[F] {
    def raiseError[A](e: E): F[A]
    def handleError[A](fa: F[A])(f: E => F[A]): F[A]
    def emap[A, B](fa: F[A])(f: A => S \/ B): F[B] = ...
  }
~~~~~~~~


### Don't Repeat Yourself

The simplest way to derive a typeclass is to reuse one that already exists.

The `Equal` typeclass has an instance of `Contravariant[Equal]` which provides
the `.contramap` method, defined by the typeclass author:

{lang="text"}
~~~~~~~~
  object Equal {
    implicit val contravariant = new Contravariant[Equal] {
      def contramap[A, B](fa: Equal[A])(f: B => A): Equal[B] =
        (b1, b2) => fa.equal(f(b1), f(b2))
    }
    ...
  }
~~~~~~~~

As users of `Equal`, we can use `.contramap` for our data types, providing a
function that extracts the underlying value. Recall that instances of
typeclasses go on the companion of our data types so that they are in the
implicit scope:

{lang="text"}
~~~~~~~~
  final case class Foo(s: String)
  object Foo {
    implicit val equal: Equal[Foo] = Equal[String].contramap(_.s)
  }
  
  scala> Foo("hello") === Foo("world")
  false
~~~~~~~~

However, not all typeclasses can have an instance of `Contravariant`. In
particular, typeclasses with type parameters in covariant position may have a
`Functor` instead:

{lang="text"}
~~~~~~~~
  object Default {
    def instance[A](d: =>String \/ A) = new Default[A] { def default = d }
    implicit val string: Default[String] = instance("".right)
  
    implicit val functor: Functor[Default] = new Functor[Default] {
      def map[A, B](fa: Default[A])(f: A => B): Default[B] =
        instance(fa.default.map(f))
    }
    ...
  }
~~~~~~~~

We can now derive a `Default[Foo]`

{lang="text"}
~~~~~~~~
  object Foo {
    implicit val default: Default[Foo] = Default[String].map(Foo(_))
    ...
  }
~~~~~~~~

If a typeclass has parameters in both covariant and contravariant position, as
is the case with `Semigroup`, it may provide an `InvariantFunctor`

{lang="text"}
~~~~~~~~
  object Semigroup {
    implicit val invariant = new InvariantFunctor[Semigroup] {
      def xmap[A, B](ma: Semigroup[A], f: A => B, g: B => A) = new Semigroup[B] {
        def append(x: B, y: =>B): B = f(ma.append(g(x), g(y)))
      }
    }
    ...
  }
~~~~~~~~

and we can call `.xmap`

{lang="text"}
~~~~~~~~
  object Foo {
    implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
    ...
  }
~~~~~~~~

It is simpler to use `.xmap` from the `InvariantFunctor` parent:

{lang="text"}
~~~~~~~~
  object Foo {
    implicit val equal: Equal[Foo]         = Equal[String].xmap(Foo(_), _.s)
    implicit val default: Default[Foo]     = Default[String].xmap(Foo(_), _.s)
    implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
  }
~~~~~~~~

A> The `@xderiving` annotation automatically inserts `.xmap` boilerplate. Add the
A> following to `build.sbt`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   addCompilerPlugin("com.fommil" %% "deriving-plugin" % derivingVersion)
A>   libraryDependencies += "com.fommil" %% "deriving-macro" % derivingVersion % "provided"
A> ~~~~~~~~
A> 
A> and use it as
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   @xderiving(Equal, Default, Semigroup)
A>   final case class Foo(s: String)
A> ~~~~~~~~


### `MonadError`

Typically things that *write* out a polymorphic value have a `Contravariant`,
and things that *read* into a polymorphic value have a `Functor`. However, it is
very much expected that reading a value can fail. For example, if we have a
default `String` it does not mean that we can simply derive a default `String
Refined NonEmpty` from it

{lang="text"}
~~~~~~~~
  import eu.timepit.refined.refineV
  import eu.timepit.refined.api._
  import eu.timepit.refined.collection._
  
  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].map(refineV[NonEmpty](_))
~~~~~~~~

fails to compile with

{lang="text"}
~~~~~~~~
  [error] default.scala:41:32: polymorphic expression cannot be instantiated to expected type;
  [error]  found   : Either[String, String Refined NonEmpty]
  [error]  required: String Refined NonEmpty
  [error]     Default[String].map(refineV[NonEmpty](_))
  [error]                                          ^
~~~~~~~~

Recall from Chapter 4.1 that `refineV` returns an `Either`, as the compiler
has now reminded us.

As the typeclass author of `Default`, we can do better than `Functor` and
provide a `MonadError[Default, String]`:

{lang="text"}
~~~~~~~~
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
~~~~~~~~

Now we have access to `.emap` syntax and can derive our refined type

{lang="text"}
~~~~~~~~
  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].emap(refineV[NonEmpty](_).disjunction)
~~~~~~~~

In fact, we can provide a derivation rule for all refined types

{lang="text"}
~~~~~~~~
  implicit def refined[A: Default, P](
    implicit V: Validate[A, P]
  ): Default[A Refined P] = Default[A].emap(refineV[P](_).disjunction)
~~~~~~~~

where `Validate` is from the refined library and is required by `refineV`.

That said, we probably want to provide a custom instance of `Default[String
Refined NonEmpty]` that succeeds if it is something we need.

A> The `refined-scalaz` extension to `refined` provides support for automatically
A> deriving all typeclasses for refined types with the following import
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   import eu.timepit.refined.scalaz._
A> ~~~~~~~~
A> 
A> if there is a `Contravariant` or `MonadError[?, String]` in the implicit scope.
A> 
A> However, due to [limitations of the scala compiler](https://github.com/scala/bug/issues/10753) it rarely works in practice
A> and we must write `implicit def refined` derivations for each typeclass.

Similarly we can use `.emap` to derive an `Int` decoder from a `Long`, with
protection around the non-total `.toInt` stdlib method.

{lang="text"}
~~~~~~~~
  implicit val long: Default[Long] = instance(0L.right)
  implicit val int: Default[Int] = Default[Long].emap {
    case n if (Int.MinValue <= n && n <= Int.MaxValue) => n.toInt.right
    case big => s"$big does not fit into 32 bits".left
  }
~~~~~~~~


### `.fromIso`

All of the typeclasses in scalaz have a method on their companion with a
signature similar to the following:

{lang="text"}
~~~~~~~~
  object Equal {
    def fromIso[F, G: Equal](D: F <=> G): Equal[F] = ...
    ...
  }
  
  object Monad {
    def fromIso[F[_], G[_]: Monad](D: F <~> G): Monad[F] = ...
    ...
  }
~~~~~~~~

These mean that if we have a type `F`, and a way to convert it into a `G` that
has an instance, we can call `Equal.fromIso` to obtain an instance for `F`.

For example, as typeclass users, if we have a data type `Bar` we can define an
isomorphism to `(String, Int)`

{lang="text"}
~~~~~~~~
  import Isomorphism._
  
  final case class Bar(s: String, i: Int)
  object Bar {
    val iso: Bar <=> (String, Int) = IsoSet(b => (b.s, b.i), t => Bar(t._1, t._2))
  }
~~~~~~~~

and then derive `Equal[Bar]` because there is already a `Equal` for all tuples:

{lang="text"}
~~~~~~~~
  object Bar {
    ...
    implicit val equal: Equal[Bar] = Equal.fromIso(iso)
  }
~~~~~~~~

The `.fromIso` mechanism can also assist us as typeclass authors. Consider
`Default` which has a core type signature of the form `Unit => F[A]`. Our
`default` method is in fact isomorphic to `Kleisli[F, Unit, A]`, the `ReaderT`
monad transformer.

Since `Kleisli` already provides a `MonadError` (if `F` has one), we can derive
`MonadError[Default, String]` by creating an isomorphism between `Default` and
`Kleisli`:

{lang="text"}
~~~~~~~~
  private type Sig[a] = Unit => String \/ a
  private val iso = Kleisli.iso(
    λ[Sig ~> Default](s => instance(s(()))),
    λ[Default ~> Sig](d => _ => d.default)
  )
  implicit val monad: MonadError[Default, String] = MonadError.fromIso(iso)
~~~~~~~~

giving us the `.map`, `.xmap` and `.emap` that we've been making use of so far,
effectively for free.


### `Divisible` and `Applicative`

To derive the `Equal` for our case class with two parameters, we reused the
instance that scalaz provides for tuples. But where did the tuple instance come
from?

A more specific typeclass than `Contravariant` is `Divisible`, and `Equal`
provides an instance:

{lang="text"}
~~~~~~~~
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
~~~~~~~~

A> When implementing `Divisible` the compiler will require us to implement
A> `contramap`, which we can do with the following derived combinator:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   override def contramap[A, B](fa: F[A])(f: B => A): F[B] =
A>     divide2(conquer[Unit], fa)(c => ((), f(c)))
A> ~~~~~~~~
A> 
A> This has been added to `Divisible` in scalaz 7.3.

And from `divide2`, `Divisible` is able to build up derivations all the way to
`divide22`. We can call these methods directly for our data types:

{lang="text"}
~~~~~~~~
  final case class Bar(s: String, i: Int)
  object Bar {
    implicit val equal: Equal[Bar] =
      Divisible[Equal].divide2(Equal[String], Equal[Int])(b => (b.s, b.i))
  }
~~~~~~~~

The equivalent for type parameters in covariant position is `Applicative`:

{lang="text"}
~~~~~~~~
  object Bar {
    ...
    implicit val default: Default[Bar] =
      Applicative[Default].apply2(Default[String], Default[Int])(Bar(_, _))
  }
~~~~~~~~

But we must be careful that we do not break the typeclass laws when we implement
`Divisible` or `Applicative`. In particular, it is easy to break the *law of
composition* which says that the following two codepaths must yield exactly the
same output

-   `divide2(divide2(a1, a2)(dupe), a3)(dupe)`
-   `divide2(a1, divide2(a2, a3)(dupe))(dupe)`
-   for any `dupe: A => (A, A)`

with similar laws for `Applicative`.

Consider `JsEncoder` and a proposed instance of `Divisible`

{lang="text"}
~~~~~~~~
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
~~~~~~~~

On one side of the composition laws, for a `String` input, we get

{lang="text"}
~~~~~~~~
  JsArray([JsArray([JsString(hello),JsString(hello)]),JsString(hello)])
~~~~~~~~

and on the other

{lang="text"}
~~~~~~~~
  JsArray([JsString(hello),JsArray([JsString(hello),JsString(hello)])])
~~~~~~~~

which are different. We could experiment with variations of the `divide`
implementation, but it will never satisfy the laws for all inputs.

We therefore cannot provide a `Divisible[JsEncoder]`, even though we can write
one down, because it breaks the mathematical laws and invalidates all the
assumptions that users of `Divisible` rely upon.

To aid in testing laws, scalaz typeclasses contain the codified versions of
their laws on the typeclass itself. We can write an automated test, asserting
that the law fails, to remind us of this fact:

{lang="text"}
~~~~~~~~
  val D: Divisible[JsEncoder] = ...
  val S: JsEncoder[String] = JsEncoder[String]
  val E: Equal[JsEncoder[String]] = (p1, p2) => p1.toJson("hello") === p2.toJson("hello")
  assert(!D.divideLaw.composition(S, S, S)(E))
~~~~~~~~

On the other hand, a similar `JsDecoder` test meets the `Applicative` composition laws

{lang="text"}
~~~~~~~~
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
~~~~~~~~

for some test data

{lang="text"}
~~~~~~~~
  composeTest(JsObject(IList("a" -> JsString("hello"), "b" -> JsInteger(1))))
  composeTest(JsNull)
  composeTest(JsObject(IList("a" -> JsString("hello"))))
  composeTest(JsObject(IList("b" -> JsInteger(1))))
~~~~~~~~

Now we are reasonably confident that our derived `MonadError` is lawful.

However, just because we have a test that passes for a small set of data does
not prove that the laws are satisfied. We must also reason through the
implementation to convince ourselves that it **should** satisfy the laws, and try
to propose corner cases where it could fail.

One way of generating a wide variety of test data is to use the [scalacheck](https://github.com/rickynils/scalacheck)
library, which provides an `Arbitrary` typeclass that integrates with most
testing frameworks to repeat a test with randomly generated data.

The `jsonformat` library provides an `Arbitrary[JsValue]` (everybody should
provide an `Arbitrary` for their ADTs!) allowing us to make use of scalatest's
`forAll` feature:

{lang="text"}
~~~~~~~~
  forAll(SizeRange(10))((j: JsValue) => composeTest(j))
~~~~~~~~

This test gives us even more confidence that our typeclass meets the
`Applicative` composition laws. By checking all the laws on `Divisible` and
`MonadError` we also get **a lot** of smoke tests for free.

A> We must restrict `forAll` to have a `SizeRange` of `10`, which limits both
A> `JsObject` and `JsArray` to a maximum size of 10 elements. This avoids stack
A> overflows as larger numbers can generate gigantic JSON documents.


### `Decidable` and `Alt`

Where `Divisible` and `Applicative` give us typeclass derivation for products
(built of tuples), `Decidable` and `Alt` give us the coproducts (built of nested
disjunctions):

{lang="text"}
~~~~~~~~
  @typeclass trait Alt[F[_]] extends Applicative[F] with InvariantAlt[F] {
    def alt[A](a1: =>F[A], a2: =>F[A]): F[A]
  
    def altly1[Z, A1](a1: =>F[A1])(f: A1 => Z): F[Z] = ...
    def altly2[Z, A1, A2](a1: =>F[A1], a2: =>F[A2])(f: A1 \/ A2 => Z): F[Z] = ...
    def altly3 ...
    def altly4 ...
    ...
  }
  
  trait Decidable[F[_]] extends Divisible[F] with InvariantAlt[F] {
    def choose1[Z, A1](a1: =>F[A1])(f: Z => A1): F[Z] = ...
    def choose2[Z, A1, A2](a1: =>F[A1], a2: =>F[A2])(f: Z => A1 \/ A2): F[Z] = ...
    def choose3 ...
    def choose4 ...
    ...
  }
~~~~~~~~

The four core typeclasses have symmetric signatures:

| Typeclass     | method    | given          | signature         | returns |
|------------- |--------- |-------------- |----------------- |------- |
| `Applicative` | `apply2`  | `F[A1], F[A2]` | `(A1, A2) => Z`   | `F[Z]`  |
| `Alt`         | `altly2`  | `F[A1], F[A2]` | `(A1 \/ A2) => Z` | `F[Z]`  |
| `Divisible`   | `divide2` | `F[A1], F[A2]` | `Z => (A1, A2)`   | `F[Z]`  |
| `Decidable`   | `choose2` | `F[A1], F[A2]` | `Z => (A1 \/ A2)` | `F[Z]`  |

supporting covariant products; covariant coproducts; contravariant products;
contravariant coproducts.

We can write a `Decidable[Equal]`, letting us derive `Equal` for any ADT!

{lang="text"}
~~~~~~~~
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
~~~~~~~~

For an ADT

{lang="text"}
~~~~~~~~
  sealed abstract class Darth { def widen: Darth = this }
  final case class Vader(s: String, i: Int) extends Darth
  final case class Maul(i: Int, s: String) extends Darth
~~~~~~~~

where the products (`Vader` and `Maul`) have an `Equal`

{lang="text"}
~~~~~~~~
  object Vader {
    private val g: Vader => (String, Int) = d => (d.s, d.i)
    implicit val equal: Equal[Vader] = Divisible[Equal].divide2(Equal[String], Equal[Int])(g)
  }
  object Maul {
    private val g: Maul => (Int, String) = d => (d.i, d.s)
    implicit val equal: Equal[Maul] = Divisible[Equal].divide2(Equal[Int], Equal[String])(g)
  }
~~~~~~~~

we can derive the equal for the whole ADT

{lang="text"}
~~~~~~~~
  object Darth {
    private def g(t: Darth): Vader \/ Maul = t match {
      case p @ Vader(_, _) => -\/(p)
      case p @ Maul(_, _)  => \/-(p)
    }
    implicit val equal: Equal[Darth] = Decidable[Equal].choose2(Equal[Vader], Equal[Maul])(g)
  }
  
  scala> Vader("hello").widen === Maul(1).widen
  false
~~~~~~~~

A> Scalaz 7.2 does not provide a `Decidable[Equal]` out of the box, because it was
A> a late addition. We must provide one. In the next section is a more convenient
A> solution that means we won't need to do this in practice.

Typeclasses that have an `Applicative` can be eligible for an `Alt`. If we want
to use our `Kleisli.iso` trick, we have to extend `IsomorphismMonadError`
instead of `MonadError.fromIso`, and mix in `Alt`. Let's upgrade our
`MonadError[Default, String]` to have an `Alt[Default]`:

{lang="text"}
~~~~~~~~
  private type K[a] = Kleisli[String \/ ?, Unit, a]
  implicit val monad = new IsomorphismMonadError[Default, K, String] with Alt[Default] {
    override val G = MonadError[K, String]
    override val iso = ...
  
    def alt[A](a1: =>Default[A], a2: =>Default[A]): Default[A] = instance(a1.default)
  }
~~~~~~~~

A> The primitive of `Alt` is `alt`, much as the primitive of `Applicative` is `ap`,
A> but it often makes more sense to use `altly2` and `apply2` as the primitives
A> with the following overrides:
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

Letting us derive our `Default[Darth]`

{lang="text"}
~~~~~~~~
  object Darth {
    ...
    private def f(e: Vader \/ Maul): Darth = e.merge
    implicit val default: Default[Darth] = Alt[Default].altly2(Default[Vader], Default[Maul])(f)
  }
  object Vader {
    ...
    private val f: (String, Int) => Vader = Vader(_, _)
    implicit val default: Default[Vader] = Alt[Default].apply2(Default[String], Default[Int])(f)
  }
  object Maul {
    ...
    private val f: (Int, String) => Maul = Maul(_, _)
    implicit val default: Default[Maul] = Alt[Default].apply2(Default[Int], Default[String])(f)
  }
  
  scala> Default[Darth].default
  \/-(Vader())
~~~~~~~~

Returning to the `scalaz-deriving` typeclasses, the invariant parents of `Alt`
and `Decidable`:

{lang="text"}
~~~~~~~~
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
~~~~~~~~

mean that we can instead write consistent boilerplate for all derivations

{lang="text"}
~~~~~~~~
  object Darth {
    ...
    implicit val equal: Equal[Darth] =
      InvariantAlt[Equal].xcoproduct2(Equal[Vader], Equal[Maul])(f, g)
    implicit val default: Default[Darth] =
      InvariantAlt[Default].xcoproduct2(Default[Vader], Default[Maul])(f, g)
  }
  object Vader {
    ...
    implicit val equal: Equal[Vader] =
      InvariantApplicative[Equal].xproduct2(Equal[String], Equal[Int])(f, g)
    implicit val default: Default[Vader] =
      InvariantApplicative[Default].xproduct2(Default[String], Default[Int])(f, g)
  }
  object Maul {
    ...
    implicit val equal: Equal[Maul] =
      InvariantApplicative[Equal].xproduct2(Equal[Int], Equal[String])(f, g)
    implicit val default: Default[Maul] =
      InvariantApplicative[Default].xproduct2(Default[Int], Default[String])(f, g)
  }
~~~~~~~~

This boilerplate also works when we have a typeclass like `Semigroup` that can
only provide an `InvariantApplicative`, not an `Applicative` or even an
`InvariantAlt`.


### Arbitrary Arity and `@deriving`

There are two problems with `InvariantApplicative` and `InvariantAlt`:

1.  they only support products of four fields and coproducts of four entries.
2.  there is a **lot** of boilerplate on the data type companions.

In this final section about `scalaz-deriving` we will solve both problems.

There are additional typeclasses introduced by `scalaz-deriving`

{width=75%}
![](images/scalaz-deriving.png)

Effectively, our four central typeclasses `Applicative`, `Divisible`, `Alt` and
`Decidable` all get extended to arbitrary arity using the [iotaz](https://github.com/frees-io/iota) library, hence
the `z` postfix.

The iotaz library has three main types:

-   `TList` which describes arbitrary length chains of types
-   `Prod[A <: TList]` for products
-   `Cop[A <: TList]` for coproducts

By way of example, a `TList` representation of `Darth` from the previous
section is

{lang="text"}
~~~~~~~~
  import iotaz._, TList._
  
  type DarthT = Vader :: Maul :: TNil
  type VaderT = String :: Int :: TNil
  type MaulT  = Int :: String :: TNil
~~~~~~~~

which can be instantiated:

{lang="text"}
~~~~~~~~
  val vader: Prod[VaderT] = Prod("hello", 1)
  val maul: Prod[MaulT]  = Prod(1, "hello")
  
  val VaderI = Cop.Inject[Vader, Cop[DarthT]]
  val darth: Cop[DarthT] = VaderI.inj(Vader("hello", 1))
~~~~~~~~

To be able to use the `scalaz-deriving` API, we need an `Isomorphism` between
our ADTs and the `iotaz` generic representation. It's a lot of boilerplate, but
it pays off (note that we also have to provide the names of all the fields and
the type itself):

{lang="text"}
~~~~~~~~
  object Darth {
    private type Repr   = Vader :: Maul :: TNil
    private type Labels = String :: String :: TNil
    private val VaderI  = Cop.Inject[Vader, Cop[Repr]]
    private val MaulI   = Cop.Inject[Maul, Cop[Repr]]
    private val iso     = CopGen[Darth, Repr, Labels](
      {
        case d: Vader => VaderI.inj(d)
        case d: Maul  => MaulI.inj(d)
      }, {
        case VaderI(d) => d
        case MaulI(d)  => d
      },
      Prod("Vader", "Maul"),
      "Darth"
    )
    ...
  }
  
  object Vader {
    private type Repr   = String :: Int :: TNil
    private type Labels = String :: String :: TNil
    private val iso     = ProdGen[Vader, Repr, Labels](
      d => Prod(d.s, d.i),
      p => Vader(p.head, p.tail.head),
      Prod("s", "i"),
      "Vader"
    )
    ...
  }
  
  object Maul {
    private type Repr   = Int :: String :: TNil
    private type Labels = String :: String :: TNil
    private val iso     = ProdGen[Maul, Repr, Labels](
      d => Prod(d.i, d.s),
      p => Maul(p.head, p.tail.head),
      Prod("i", "s"),
      "Maul"
    )
    ...
  }
~~~~~~~~

With that out of the way we can call the `Deriving` API for `Equal`

{lang="text"}
~~~~~~~~
  object Darth {
    ...
    implicit val equal: Equal[Darth] = Deriving[Equal].xcoproductz(
      Prod(Need(Equal[Vader]), Need(Equal[Maul])),
      iso.labels, iso.name)(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val equal: Equal[Vader] = Deriving[Equal].xproductz(
      Prod(Need(Equal[String]), Need(Equal[Int])),
      iso.labels, iso.name)(iso.to, iso.from)
  }
  object Maul {
    ...
    implicit val equal: Equal[Maul] = Deriving[Equal].xproductz(
      Prod(Need(Equal[Int]), Need(Equal[String])),
      iso.labels, iso.name)(iso.to, iso.from)
  }
~~~~~~~~

A> Typeclasses in the `Deriving` API are wrapped in `Need` (recall `Name` from
A> Chapter 6), which allows lazy construction, avoiding unnecessary work if the
A> typeclass is not needed, and avoiding stack overflows for recursive ADTs.

`scalaz-deriving` provides an optimised instance of `Deriving[Equal]`. To be
able to do the same for our `Default` typeclass, we need to provide an instance.
Luckily it's just a case of wrapping our existing `Alt` with a helper

{lang="text"}
~~~~~~~~
  object Default {
    ...
    implicit val deriving: Deriving[Default] = ExtendedInvariantAlt(monad)
  }
~~~~~~~~

and then calling it from the companions

{lang="text"}
~~~~~~~~
  object Darth {
    ...
    implicit val default: Default[Darth] = Deriving[Default].xcoproductz(
      Prod(Need(Default[Vader]), Need(Default[Maul])),
      iso.labels, iso.name)(iso.to, iso.from)
  }
  object Vader {
    ...
    implicit val default: Default[Vader] = Deriving[Default].xproductz(
      Prod(Need(Default[String]), Need(Default[Int])),
      iso.labels, iso.name)(iso.to, iso.from)
  }
  object Maul {
    ...
    implicit val default: Default[Maul] = Deriving[Default].xproductz(
      Prod(Need(Default[Int]), Need(Default[String])),
      iso.labels, iso.name)(iso.to, iso.from)
  }
~~~~~~~~

We have solved the problem of arbitrary arity, but we have introduced even more
boilerplate.

The punchline is that the `@deriving` annotation, which comes from
`deriving-plugin`, generates all the boilerplate automatically and only needs to
be applied at the top level of an ADT. We can collapse all the boilerplate down
to:

{lang="text"}
~~~~~~~~
  @deriving(Equal, Default)
  sealed abstract class Darth { def widen: Darth = this }
  final case class Vader(s: String, i: Int) extends Darth
  final case class Maul(i: Int, s: String) extends Darth
~~~~~~~~

Also included in `scalaz-deriving` are instances for `Order`, `Show`,
`Semigroup`, `Monoid` and `Arbitrary`.

You're welcome.


### Examples

We finish our study of `scalaz-deriving` with fully worked implementations for
all the example typeclasses. Before we do that we need to know about a new data
types: `/~\` (the "snake in the road") aliased to `APair`. This is useful for
containing two higher kinded structures that are both tied to the same type:

{lang="text"}
~~~~~~~~
  sealed abstract class /~\[A[_], B[_]] {
    type T
    def a: A[T]
    def b: B[T]
  }
  object /~\ {
    type APair[A[_], B[_]]  = A /~\ B
    @inline final def unapply[A[_], B[_]](p: A /~\ B): Some[(A[p.T], B[p.T])] = ...
    @inline final def apply[A[_], B[_], Z](az: =>A[Z], bz: =>B[Z]): A /~\ B = ...
  }
~~~~~~~~

We typically use this in the context of `Id /~\ TC` where `TC` is our typeclass,
meaning that we have a value, and an instance of a typeclass for that value,
without knowing anything about the value.

In addition, all the methods on the `Deriving` API have implicit evidence of the
form `NameF ƒ A ↦ TC`, allowing the `iotaz` library to be able to perform
`.zip`, `.traverse`, and other operations on `Prod` and `Cop`. We can ignore
these parameters, as we don't use them directly.


#### `Equal`

As with `Default` we could define a regular fixed-arity `Decidable` and wrap it
with `ExtendedInvariantAlt` (the simplest approach), but we choose to implement
`Decidablez` directly for the performance benefit. We make two additional
optimisations:

1.  perform instance equality `.eq` before applying the `Equal.equal`, allowing
    for shortcut equality between identical values.
2.  `.foldRight` allowing early exit when any field is `false`. e.g. if the first
    fields don't match, we don't even request the `Equal` for remaining values.

{lang="text"}
~~~~~~~~
  new Decidablez[Equal] {
    @inline private final def quick(a: Any, b: Any): Boolean =
      a.asInstanceOf[AnyRef].eq(b.asInstanceOf[AnyRef])
  
    def dividez[Z, A <: TList, TC <: TList](
      tcs: Prod[TC]
    )(
      g: Z => Prod[A]
    )(
      implicit ev: NameF ƒ A ↦ TC
    ): Equal[Z] = (z1, z2) =>
      (g(z1), g(z2)).zip(tcs).foldRight(true) {
        case ((a1, a2) /~\ fa, acc) => (quick(a1, a2) || fa.value.equal(a1, a2)) && acc
      }
  
    def choosez[Z, A <: TList, TC <: TList](
      tcs: Prod[TC]
    )(
      g: Z => Cop[A]
    )(
      implicit ev: NameF ƒ A ↦ TC
    ): Equal[Z] = (z1, z2) =>
      (g(z1), g(z2)).zip(tcs) match {
        case -\/(_)               => false
        case \/-((a1, a2) /~\ fa) => quick(a1, a2) || fa.value.equal(a1, a2)
      }
  }
~~~~~~~~


#### `Default`

We've already seen how to define an `Alt` and lift it to a `Deriving` with the
`ExtendedInvariantAlt` helper. However, for completeness, say we wish to define
an `Altz` directly.

Unfortunately, the `iotaz` API for `.traverse` (and its analogy, `.coptraverse`)
requires us to define natural transformations, which have a clunky syntax, even
with the `kind-projector` plugin.

{lang="text"}
~~~~~~~~
  private type K[a] = Kleisli[String \/ ?, Unit, a]
  new IsomorphismMonadError[Default, K, String] with Altz[Default] {
    type Sig[a] = Unit => String \/ a
    override val G = MonadError[K, String]
    override val iso = Kleisli.iso(
      λ[Sig ~> Default](s => instance(s(()))),
      λ[Default ~> Sig](d => _ => d.default)
    )
  
    val extract = λ[NameF ~> (String \/ ?)](a => a.value.default)
    def applyz[Z, A <: TList, TC <: TList](tcs: Prod[TC])(
      f: Prod[A] => Z
    )(
      implicit ev1: NameF ƒ A ↦ TC
    ): Default[Z] = instance(tcs.traverse(extract).map(f))
  
    val always = λ[NameF ~> Maybe](a => a.value.default.toMaybe)
    def altlyz[Z, A <: TList, TC <: TList](tcs: Prod[TC])(
      f: Cop[A] => Z
    )(
      implicit ev1: NameF ƒ A ↦ TC
    ): Default[Z] = instance {
      tcs.coptraverse[A, NameF, Id](always).map(f).headMaybe \/> "not found"
    }
  }
~~~~~~~~


#### `Semigroup`

It is not possible to define a `Semigroup` for general coproducts, however it is
possible to do so for products with the arbitrary arity extension of
`InvariantApplicative` (not `InvariantAlt`). `Semigroup` has type parameters in
both covariant and contravariant position so we must make use of both `f` and
`g`:

{lang="text"}
~~~~~~~~
  new InvariantApplicativez[Semigroup] {
    type L[a] = ((a, a), NameF[a])
    val appender = λ[L ~> Id] { case ((a1, a2), fa) => fa.value.append(a1, a2) }
  
    override def xproductz[Z, A <: TList, TC <: TList](
      tcs: Prod[TC]
    )(
      f: Prod[A] => Z,
      g: Z => Prod[A]
    )(
      implicit ev1: NameF ƒ A ↦ TC
    ): Semigroup[Z] = new Semigroup[Z] {
      def append(z1: Z, z2: =>Z): Z = f(tcs.ziptraverse2(g(z1), g(z2), appender))
    }
  }
~~~~~~~~


#### `JsEncoder`

We have already noted that a lawful `Divisible[JsEncoder]` is not possible, but
we can implement `Deriving` directly, which has no laws, and also provides
access to field names.

We have some choices to make with regards to JSON serialisation:

1.  Should we include `null` values? We do not include fields if the value is a
    `JsNull`.
2.  How do we encode the name of a coproduct? We use a special field `"type"` to
    disambiguate coproducts.
3.  How do we deal with coproducts that are not `JsObject`? We put such values
    into a special field `"xvalue"`.

A> If the default derivation is not palatable to a user of the API, that user can
A> provide a custom instance on the companion of their data type. More complicated
A> JSON libraries may offer mechanisms for customising the derivation, but those
A> approaches tend to trade convenience for typeclass coherence, which is not
A> something we are willing to compromise.

There is a lot of ceremony involved in the type signature of `Deriving` because
it is the most general of all the APIs, but the implementation is
straightforward:

{lang="text"}
~~~~~~~~
  new Deriving[JsEncoder] {
  
    def xproductz[Z, A <: TList, TC <: TList, L <: TList](
      tcs: Prod[TC],
      labels: Prod[L],
      name: String
    )(
      f: Prod[A] => Z,
      g: Z => Prod[A]
    )(
      implicit
      ev1: NameF ƒ A ↦ TC,
      ev2: Label ƒ A ↦ L
    ): JsEncoder[Z] = { z =>
      val fields = g(z).zip(tcs, labels).flatMap {
        case (label, a) /~\ fa =>
          fa.value.toJson(a) match {
            case JsNull => Nil
            case value  => (label -> value) :: Nil
          }
      }
      JsObject(fields.toIList)
    }
  
    def xcoproductz[Z, A <: TList, TC <: TList, L <: TList](
      tcs: Prod[TC],
      labels: Prod[L],
      name: String
    )(
      f: Cop[A] => Z,
      g: Z => Cop[A]
    )(
      implicit
      ev1: NameF ƒ A ↦ TC,
      ev2: Label ƒ A ↦ L
    ): JsEncoder[Z] = { z =>
      g(z).zip(tcs, labels) match {
        case (label, a) /~\ fa =>
          val hint = "type" -> JsString(label)
          fa.value.toJson(a) match {
            case JsObject(fields) => JsObject(hint :: fields)
            case other            => JsObject(IList(hint, "xvalue" -> other))
          }
      }
    }
  
  }
~~~~~~~~


#### `JsDecoder`

The decoder is paired to the encoder's design choices so we must not forget that
missing fields are to be decoded as `JsNull` and coproduct `xvalue` is
special-cased.

{lang="text"}
~~~~~~~~
  new Deriving[JsDecoder] {
    type LF[a] = (String, NameF[a])
  
    def xproductz[Z, A <: TList, TC <: TList, L <: TList](
      tcs: Prod[TC],
      labels: Prod[L],
      name: String
    )(
      f: Prod[A] => Z,
      g: Z => Prod[A]
    )(
      implicit
      ev1: NameF ƒ A ↦ TC,
      ev2: Label ƒ A ↦ L
    ): JsDecoder[Z] = {
      case obj @ JsObject(_) =>
        val each = λ[LF ~> (String \/ ?)] {
          case (label, fa) =>
            val value = obj.get(label).getOrElse(JsNull)
            fa.value.fromJson(value)
        }
        tcs.traverse(each, labels).map(f)
  
      case other => fail("JsObject", other)
    }
  
    def xcoproductz[Z, A <: TList, TC <: TList, L <: TList](
      tcs: Prod[TC],
      labels: Prod[L],
      name: String
    )(
      f: Cop[A] => Z,
      g: Z => Cop[A]
    )(
      implicit
      ev1: NameF ƒ A ↦ TC,
      ev2: Label ƒ A ↦ L
    ): JsDecoder[Z] = {
      case obj @ JsObject(_) =>
        obj.get("type") match {
          case \/-(JsString(hint)) =>
            val xvalue = obj.get("xvalue")
            val each = λ[LF ~> Maybe] {
              case (label, fa) =>
                if (hint == label) fa.value.fromJson(xvalue.getOrElse(obj)).toMaybe
                else Maybe.empty
            }
            tcs
              .coptraverse[A, L, NameF, Id](each, labels)
              .headMaybe
              .map(f) \/> s"a valid $hint"
  
          case _ => fail("JsObject with type", obj)
        }
      case other => fail("JsObject", other)
    }
  
  }
~~~~~~~~


## TODO Magnolia


## TODO Shapeless


## TODO Summary

TODO: table comparison of features
TODO: performance (compiletime and runtime)


# The Infinite Sadness

You've reached the end of this Early Access book. Please check the website
regularly for updates.

You can expect to see chapters covering the following topics:

-   Typeclass Derivation (to be completed)
-   Appendix: Haskell

As well as a chapter pulling everything together for the example application.


