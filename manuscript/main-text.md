

# Cats

In this chapter we will tour the data types and typeclasses in cats
and its related ecosystem. We don't use everything in
`drone-dynamic-agents` so we will give standalone examples when
appropriate.

There has been criticism of the naming in cats, and functional
programming in general. Cats is a word play on *Category Theory* and
the concepts originate from academia, much like object oriented
programming originated from academic research in the 1960s (with their
high falutin words like *polymorphism*, *subtyping*, *generics* and
*aspects*). We'll use the cats names in this book, but feel free to
set up `type` aliases if you would prefer to use names based on the
primary functionality of the typeclass.

Before we introduce the complete typeclass hierarchy, we will look at
the three most important typeclasses from a control flow perspective:
`Functor`, `Applicative` and `Monad` (each extending the former).

| Typeclass     | Method    | From   |               | To     |
|------------- |--------- |------ |------------- |------ |
| `Functor`     | `map`     | `F[A]` | `(A => B)`    | `F[B]` |
| `Applicative` | `pure`    | `A`    |               | `F[A]` |
| `Monad`       | `flatMap` | `F[A]` | `(A => F[B])` | `F[B]` |

We learnt in Chapter 1 that sequential operations that return a `F[_]`
are formalised by `flatMap`, which lives on the `Monad` typeclass. One
way of looking at a context `F[A]` is to think of it as a *program*
`F[_]` with `A` as the output: `flatMap` allows us to generate new
programs `F[B]` at runtime based on the results of running previous
programs.

`Applicative`, a parent of `Monad`, introduces the `pure` method
allowing a value to become a (trivial) program. We have used `pure`
enough in previous chapters such that its importance should be
self-evident.

But much can be achieved without the full power of a `Monad`. If a
function wishes to transform the result of running a program, that's
just `map`, introduced by `Functor`. In Chapter 3, we ran programs in
parallel by creating a product and mapping over them. In Functional
Programming, parallel computations are considered **less** powerful than
sequential ones: you are encouraged to be as parallel as possible.

When it comes to depending on typeclasses, functional programmers and
object oriented programmers agree: depend on the most general (least
powerful) interface you can. In contrast to data types, where
parameters should be as specific as possible to forbid impossible
states.

## Typeclasses

There is an overwhelming number of typeclasses, so we will [visualise
the clusters](https://github.com/tpolecat/cats-infographic) and discuss, with simplified definitions. We'll gloss
over the more esoteric typeclasses.

### Combinable Things

{width=60%}
![](images/cats-monoid.png)

Defined roughly as:

{lang="text"}
~~~~~~~~
  /** A set with an associative operation. */
  @typeclass trait Semigroup {
    @op("|+|") def combine(x: A, y: A): A
  }
  
  /** A Semigroup with an identity. */
  @typeclass trait Monoid extends Semigroup[A] {
    def empty: A
  
    def combineAll(as: Seq[A]): A = as.foldLeft(empty)(combine)
  }
  
  /** A monoid where each element has an inverse. */
  @typeclass trait Group extends Monoid[A] {
    def inverse(a: A): A
  
    @op("|-|") def remove(a: A, b: A): A = combine(a, inverse(b))
  }
~~~~~~~~

It is common to use `|+|` instead of `combine`, known as the TIE
Fighter operator. There is an Advanced TIE Fighter in the next
section, which is very exciting.

*Associative* means that the order of applications should not matter,
i.e.

{lang="text"}
~~~~~~~~
  (a |+| b) |+| c == a |+| (b |+| c)
~~~~~~~~

*Identity* means that combining the `empty` with any other `a` should
give `a`. For example, integer addition's `identity` is `0` so it is
also common to refer to `empty` as `zero`.

{lang="text"}
~~~~~~~~
  a |+| 0 == a
~~~~~~~~

*Inverse* means that adding any element and its inverse should give
the identity, e.g.

{lang="text"}
~~~~~~~~
  a |-| a == 0
~~~~~~~~

For numbers, `remove` is synonymous with `minus`.

This is probably bringing back memories of `Numeric` from Chapter 4,
which tried to do too much and was unusable beyond the most basic of
number types. Of course there are implementations of `Group` for all
the primitive numbers, but the generalisation of *combinable* things
is useful beyond numbers.

As a realistic example, consider a trading system that has a large
database of reusable trade templates. Creating the default values for
a new trade involves picking a sequence of off-the-shelf templates and
combining them with a "last rule wins" merge policy in case of
conflict.

We'll create a simple template to demonstrate the principle, but keep
in mind that a realistic system would have hundreds of parameters
within nested `case class`.

{lang="text"}
~~~~~~~~
  sealed abstract class Currency
  case object EUR extends Currency
  case object USD extends Currency
  
  final case class TradeTemplate(
    payments: Seq[java.time.LocalDate],
    ccy: Option[Currency],
    otc: Option[Boolean]
  )
~~~~~~~~

If we write a method that takes `templates: Seq[TradeTemplate]`, we
only need to call `combineAll` and our job is done!

But to call `combineAll` we must have an instance of
`Monoid[TradeTemplate]`. Cats provides generic instances in the with
the right imports, so we do not need to write one manually.

{lang="text"}
~~~~~~~~
  import cats._
  import cats.implicits._
  import cats.derived._, monoid._, legacy._
  import java.time.LocalDate
~~~~~~~~

However, generic derivation will fail because `Monoid[Option[T]]`
defers to `Monoid[T]` and we have neither a `Monoid[Currency]`
(kittens cannot derive a `Monoid` for a coproduct) nor a
`Monoid[Boolean]` (inclusive or exclusive logic must be explicitly
chosen).

To explain what we mean by "defers to", consider
`Monoid[Option[Int]]`:

{lang="text"}
~~~~~~~~
  scala> Option(2) |+| None
  res: Option[Int] = Some(2)
  scala> Option(2) |+| Option(1)
  res: Option[Int] = Some(3)
~~~~~~~~

We can see the content's `combine` has been called, which for `Int` is
integer addition.

But our business rules state that we use "last rule wins" on
conflicts, so we introduce a higher priority, implicit
`Monoid[Option[T]]` instance and use it during our generic derivation
instead of the default one:

{lang="text"}
~~~~~~~~
  implicit def lastWins[A]: Monoid[Option[A]] = new Monoid[Option[A]] {
    def combine(x: Option[A], y: Option[A]): Option[A] = (x, y) match {
      case (Some(_)     , winner@Some(_)) => winner
      case (only@Some(_),              _) => only
      case (           _,   only@Some(_)) => only
      case _                              => None
    }
    def empty: Option[A] = None
  }
~~~~~~~~

Let's try it out...

{lang="text"}
~~~~~~~~
  scala> val templates = List(
           TradeTemplate(Nil,                            None,      None),
           TradeTemplate(Nil,                            Some(EUR), None),
           TradeTemplate(List(LocalDate.of(2017, 8, 5)), Some(USD), None),
           TradeTemplate(List(LocalDate.of(2017, 9, 5)), None,      Some(true)),
           TradeTemplate(Nil,                            None,      Some(false))
         )
  
  scala> Monoid[TradeTemplate].combineAll(templates)
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

All we needed to do was implement one piece of business logic and
`Monoid` took care of everything else for us! Can you imagine doing
this without using a generically derived typeclass? It could easily be
thousands of lines of code to do it manually for realistic sized
cases.

Note that the list of `payments` are concatenated. This is because the
default `Monoid[Seq]` uses concatenation of elements and happens to be
the desired behaviour. If the business requirement was different, it
would be a simple case of providing a custom `Monoid[Seq[LocalDate]]`.
Recall from Chapter 4 that with compiletime polymorphism we can have a
different implementation of `combine` depending on the `E` in
`Seq[E]`, not just the base runtime class `Seq`.

1.  Esoterics

    The `Commutative*` variants have an additional requirement / law
    imposed upon them that the order of parameters to `combine` does not
    matter, i.e.
    
    {lang="text"}
    ~~~~~~~~
      a |+| b == b |+| a
    ~~~~~~~~
    
    Our trading example is most definitely **non** commutative, since the
    order of application is important, however this is a useful property
    to require if you are building a distributed system where there are
    efficiencies to be gained by reordering your calculations.
    
    `Band`, `Semilattice` and `BoundedSemilattice` have the additional law
    that the `combine` operation of the same two elements is *idempotent*,
    i.e. gives the same value. An example is anything defined over a group
    that can only be one value, such as `Unit`, or if the `combine` is a
    least upper bound. These exist so that niche downstream mathematics
    libraries `spire` and `algebird` can share common definitions.

### Mappable Things

{width=60%}
![](/images/cats-mappable.png)

This is only a partial view of the full typeclass hierarchy. We're
focussing on things that can be "mapped over" in some sense.

1.  Functor

    {lang="text"}
    ~~~~~~~~
      @typeclass trait Functor[F[_]] {
        def map[A, B](fa: F[A])(f: A => B): F[B]
      
        def widen[A, B >: A](fa: F[A]): F[B] = fa.asInstanceOf[F[B]]
        def lift[A, B](f: A => B): F[A] => F[B] = map(_)(f)
        def void[A](fa: F[A]): F[Unit] = map(fa)(_ => ())
        def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)] = map(fa)(a => a -> f(a))
      
        def as[A, B](fa: F[A], b: B): F[B] = map(fa)(_ => b)
        def tupleLeft[A, B](fa: F[A], b: B): F[(B, A)] = map(fa)(a => (b, a))
        def tupleRight[A, B](fa: F[A], b: B): F[(A, B)] = map(fa)(a => (a, b))
      }
    ~~~~~~~~
    
    The only abstract method is `map`, and it must *compose*, i.e. mapping
    with `f` and then again with `g` is the same as mapping once with the
    composition of `f` and `g`:
    
    {lang="text"}
    ~~~~~~~~
      fa.map(f).map(g) == fa.map(f.andThen(g))
    ~~~~~~~~
    
    The `map` should also perform a no-op if the provided function is `identity` from the Scala standard library
    
    {lang="text"}
    ~~~~~~~~
      fa.map(x => x) = fa
    ~~~~~~~~
    
    `Functor` defines some convenience methods around `map`. The
    implementation of `widen` is just a safe upcast that can safely use
    `asInstanceOf` for performance thanks to the no-op identity law.
    
    The documentation has been intentionally omitted in the above
    definitions to encourage reading the type signatures to understand
    what they do. You are encouraged to spend a moment studying `lift`,
    `void` and `fproduct` before reading further.
    
    `lift` takes a `A => B` and returns a `F[A] => F[B]`, or in English,
    it takes a function over the contents of an `F[A]` and returns a
    function that operates **on** the `F[A]` directly, perhaps to meet a
    type requirement elsewhere.
    
    `void` takes an instance of the `F[A]` and always returns a
    `F[Unit]`, so it must erase all the values whilst preserving
    structure.
    
    `fproduct` takes the same input as `map` but returns `F[(A, B)]`,
    therefore it must keep the contents of `F[A]` and tuple them with the
    result of applying the function.
    
    `as`, `tupleLeft` and `tupleRight` are different ways of sticking a
    constant into the output. For example, a parser may wish to do
    something like
    
    {lang="text"}
    ~~~~~~~~
      string("foo").as(true)
    ~~~~~~~~
    
    for a format that uses keyword existence to set a `Boolean` flag.

2.  Foldable

3.  TODO Traversable

4.  TODO Reducible

5.  TODO Esoteric

    -   FunctorFilter
    -   TraverseFilter
    -   CoflatMap
    -   Comonad

## Variance

<http://typelevel.org/blog/2016/02/04/variance-and-functors.html>

A `Functor` is documented in cats as a "covariant functor". This seems
immediately contradictory since it inherits from `Invariant`. TODO

Fabio Labella: the sub typing relationship does make sense, functors answer the question: "what do I need, to go from an F[A] to an F[B]" ?
1.  invariant: you need both an A => B, and a B => A

2 ) covariant: you only need an A => B
1.  contravariant: you only need a B => A

The requirements for 2) are a subset of the requirements for 1), so 2) is a subtype of 1)
The requirements for 3) are a subset of the requirements for 1) so 3) is a subtype of 1)

sealed trait Foo[A]
case class Bar[A](a: A) extends Foo[A]
case class Baz[A](f: A => Int) extends Foo[A]


-   Invariant

## NOTES 

Overwhelming, so we'll try to visualise.

Cheat sheet <http://arosien.github.io/scalaz-cheatsheets/typeclasses.pdf>

<https://github.com/tpolecat/cats-infographic>

Foldable being imminently more interesting than the others.

Traversable will need to be discussed, seems to come up a lot.

Use (impure) example of merging two deep configuration ADTs (scala
does not enforce purity so we can choose our own level)

Not enough to implement, must also pass the laws

examples that are not necessarily pure, such as ApplicativeError and
the Monoid usecase with exceptions.

kittens

allows overriding with different implementations (e.g. the "merge business rules" example)
we don't always get to choose our APIs, and sometimes our customers ask us to throw an exception

\### Ancestors
java/lang/Object.java:37: java.lang.Object
cats/FlatMap.scala:1: cats.FlatMap
cats/Applicative.scala:1: cats.Applicative

\### Inheritors
cats/free/FreeT.scala:1: cats.free.FreeTMonad
cats/derived/monad.scala:1: cats.derived.MkMonad
cats/derived/monad.scala:1: cats.derived.MkMonad1$UnsafeTailRecM
cats/data/EitherT.scala:1: cats.data.EitherTMonad
cats/Bimonad.scala:1: cats.Bimonad
cats/MonadState.scala:1: cats.MonadState
cats/MonadError.scala:1: cats.MonadError
cats/data/WriterT.scala:1: cats.data.WriterTMonad
cats/MonadFilter.scala:1: cats.MonadFilter
cats/data/IdT.scala:1: cats.data.IdTMonad
cats/data/StateT.scala:1: cats.data.StateTMonad
cats/MonadReader.scala:1: cats.MonadReader
cats/data/OptionT.scala:1: cats.data.OptionTMonad
cats/data/Prod.scala:1: cats.data.ProdMonad
cats/MonadWriter.scala:1: cats.MonadWriter

## TODO data types

# TODO Effects

# TODO FS2

Task, Stream

The basics, and covering the Effect, which can be our free monad.

Why streams are so awesome. I'd like a simple example here of reading
from a huge data source, doing parallel work and then writing out in
order to a (slower) device to demonstrate backpressure and constant
memory overhead. Maybe compare this vs hand rolled and akka streams
for a perf test?

Rewrite our business logic to be streaming, convert our GET api into a
`Stream` by polling.

# TODO Implementing the Application

Pad out the application implementation with everything we've learnt.

May need union types, see <https://github.com/propensive/totalitarian>

Will probably be a big chapter. Maybe best to leave it for a final
part of the book?

## TODO Spotting patterns, refactoring

Note that some of our algebras are actually common things and can be
rewritten: reader / writer / state / error / indexed monad. It's ok
that this is a step you can do later.

### RESEARCH perf numbers

# TODO Dependent Types

Jons talks are usually good for this <https://www.youtube.com/watch?v=a1whaMzrtsY>

# TODO Type Refinement

instead of needing those `error` calls in the first place, just don't
allow them to happen at your layer if you can get away with it.

Protect yourself from mistyping

# TODO Generic Programming

-   a mini Shapeless for Mortals
-   typeclass derivation (UrlEncoding, QueryEncoding)
-   scalacheck-shapeless
-   cachedImplicit into a val
-   downside is compile time speeds for ADTs of 50+
-   alternative is <https://github.com/propensive/magnolia>
-   some advanced cases, e.g. spray-json-shapeless stuff, typeclass
    hierarchy / ambiguities and Not
-   <https://issues.scala-lang.org/browse/SI-2509>

# TODO Recursion Schemes

# TODO Optics

not sure what the relevance to this project would be yet.

# TODO Category Theory

Just some of the high level concepts, where to get started if you're interested.
Not needed to write FP but it is needed if you want to read any academic papers.


