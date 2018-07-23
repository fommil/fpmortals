
# Typeclass Derivation

Typeclasses provide polymorphic functionality to our applications. But to use a
typeclass we need instances for our business domain objects.

The creation of a typeclass instance from existing instances is known as
*typeclass derivation* and is the topic of this chapter.

There are five approaches to typeclass derivation:

1.  Manual instances for every domain object. This is infeasible for real world
    applications as it results in hundreds of lines of code of boilerplate for
    every line of a `case class`. It is useful only for educational purposes.

2.  Write macros for each typeclass. This is not a maintainable solution, since
    the macro API is known to change with Scala major releases, and in any case
    requires an advanced and experienced developer to write each macro.

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


## `scalaz-deriving`

The `scalaz-deriving` library is an extension to Scalaz and can be added to a
project's `build.sbt` with

{lang="text"}
~~~~~~~~
  libraryDependencies += "com.fommil" %% "scalaz-deriving" % "1.0.0-RC1"
~~~~~~~~

providing three new typeclasses: `Decidable`, `Alt` and `InvariantAlt`. Shown
below in relation to the typeclasses that are relevant to this chapter:

{width=60%}
![](images/scalaz-deriving-base.png)


### Running Examples

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
A> XML, should ****not**** have typeclass encoders and decoders, because it can lead to
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
        def append(x: B, y: => B): B = f(ma.append(g(x), g(y)))
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

Recall that `Functor` and `Contravariant` extend `InvariantFunctor` and have
derived `.xmap` implementations. It can be easier to always use `.xmap` instead
of having to remember if we should use `.map` or `.contramap`:

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

Typically things that *write* a polymorphic value have a `Contravariant`, and
things that *read* a polymorphic value have a `Functor`. However, it is very
much expected that reading a value can fail. For example, if we have a default
`String` it does not mean that we can simply derive a default `String Refined
NonEmpty`

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
  [error]  found   : [P]Either[String, String Refined P]
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
  implicit val monaderr = new MonadError[Default, String] {
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
    case big                                           => big.toString.left
  }
~~~~~~~~


### `.fromIso`

All of the typeclasses in scalaz have a method on their companion with a signature similar to the following:

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

These mean that if we have a type `F`, and a way to convert it into a `G`, that
has the typeclass we care about, then we can simply call `Equal.fromIso` to
obtain an instance.

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

and then derive `Equal[Bar]` because there is already a `Equal[(String, Int)]`
in scalaz (and for all tuples up to 22 parameters):

{lang="text"}
~~~~~~~~
  object Bar {
    ...
    implicit val equal: Equal[Bar] = Equal.fromIso(iso)
  }
~~~~~~~~

A> A compiler plugin to automatically generate `Isomorphism` for data types would
A> be a welcome addition to the ecosystem.

The `.fromIso` mechanism can also assist us as typeclass authors. Consider
`Default` which has a core type signature of the form `Unit => F[A]`, the same
as `Kleisli[F, Unit, ?]` !

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
  implicit val monaderr: MonadError[Default, String] = MonadError.fromIso(iso)
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
    def divide2[A1, A2, Z](a1: =>Equal[A1], a2: =>Equal[A2])(
      f: Z => (A1, A2)
    ): Equal[Z] = { (z1, z2) =>
      val (s1, s2) = f(z1)
      val (t1, t2) = f(z2)
      a1.equal(s1, t1) && a2.equal(s2, t2)
    }
    def conquer[A]: Equal[A] = (_, _) => true
  }
~~~~~~~~

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

The analogy for typeclass with type parameters in covariant position is
`Applicative`. We already have for `Default` through our `MonadError`, so we can
call it

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
    def contramap[A, B](fa: JsEncoder[A])(f: B => A): JsEncoder[B] =
      b => fa.toJson(f(b))
  
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
  def composeTest(j: JsValue) = {
    val A: Applicative[JsDecoder] = JsDecoder.monad // from MonadError.fromIso
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
testing frameworks to repeat a test with randomly generated data. e.g. with
scalatest we can use `forAll` if we mix in `GeneratorDrivenPropertyChecks`

{lang="text"}
~~~~~~~~
  forAll(SizeRange(10))((j: JsValue) => composeTest(j))
~~~~~~~~

giving us even more confidence that our typeclass meets the `Applicative`
composition laws. By checking all the laws on `Divisible` and `MonadError` we
also get **a lot** of smoke tests for free.


### `Decidable` and `Alt`


### Arbitrary Arity and `@deriving`


## TODO Magnolia


## TODO Shapeless


# The Infinite Sadness

You've reached the end of this Early Access book. Please check the
website regularly for updates.

You can expect to see chapters covering the following topics:

-   Typeclass Derivation (to be completed)
-   Appendix: Scala for Beginners
-   Appendix: Haskell

As well as a chapter pulling everything together for the example application
(and getting the repository into a working state).


