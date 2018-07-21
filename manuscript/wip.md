
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

-   `Equal` having a type parameter in contravariant (parameter) position
-   `Default` having a type parameter in covariant (return) position
-   `Semigroup` having type parameter in both positions (invariant)
-   `JsWriter` having a type parameter in contravariant position and requiring
    access to the names of fields
-   `JsReader` having a type parameter in covariant position and requiring access
    to the names of fields

We have seen all of these typeclasses in previous chapters, except `Default`
which can request a *default* value for a type, which may not exist. For
clarity, all typeclasses are:

{lang="text"}
~~~~~~~~
  @typeclass trait Equal[A]  {
    @op("===") def equal(a1: A, a2: A): Boolean
  }
  
  @typeclass trait Default[A] {
    def default: Maybe[A]
  }
  
  @typeclass trait Semigroup[A] {
    @op("|+|") def append(x: A, y: =>A): A
  }
  
  @typeclass trait JsWriter[T] {
    def toJson(t: T): JsValue
  }
  
  @typeclass trait JsReader[T] {
    def fromJson(j: JsValue): T // FIXME: not total
  }
~~~~~~~~


### Don't Repeat Yourself

The simplest way to derive a typeclass is to reuse one that already exists.

The `Equal` typeclass has an instance of `Contravariant[Equal]` which provides
the `.contramap` method:

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
    def instance[A](d: =>Maybe[A]) = new Default[A] { def default: Maybe[A] = d }
    implicit val string: Default[String] = instance("".just)
  
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


### `fromIso`


### `Divisible` and `Applicative`


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


