
# Data Types


## Containers

-   Disjunction `\/` (Either)
-   These
-   IList
-   DList (difference list)
-   ImmutableArray
-   LazyEither
-   LazyOption
-   LazyTuple
-   OneAnd / OneOr
-   Dequeue
-   EphemeralStream
-   FingerTree
-   Cord
-   Const
-   Coproduct (higher kinded Either)
-   CorecursiveList (huh? see CorecursiveListImpl)
-   Diev (Discrete Interval Encoding Tree)
-   StrictTree
-   Tree
-   Validation
-   Either3
-   Map
-   Maybe
-   ISet
-   NonEmptyList
-   Heap (priority queues)

Dare we tackle Free in this section?


## Refinements

-   Alpha (represents an alphabetic character), maybe save for generators / laws
-   Digit (0-9)
-   CaseInsensitive
-   Endo (just wraps A => A)


## Missed typeclasses

-   Semilattice (added recently, commutative Band)
-   Distributive (dual of Traverse)
-   Zap (Functors that destroy each other)


## weird shit (mostly type tricks)

-   Adjunction (two functors `F` and `G` that give rise to a monad / comonad)
-   Codensity (seems to be a monad of some kind)
-   Zipper / TreeLoc (as discussed earlier)
-   Unapply (workaround for type inference)
-   Kan / Coyoneda / Yoneda / ContravariantCoyoneda
-   Endomorphic / Isomorphism (will need arrows)
-   Forall (universal quantification)
-   Injective (proof stuff)
-   Inject (something about data types a la carte... seems important)
-   Lens / PLens ... tricky, we really want to use monocle
-   MonoidCoproduct lists of disjunction of monoidal things
-   NaturalTransformation
-   NotNothing (mini version of shapeless' equivalent)
-   NullArgument (?=> ... drugged up elvis)
-   NullResult
-   Representable (should probably be a typeclass, X => A implies F[A]: Functor)

-   Tag
-   Tags
-   Dual


## Conversions

-   Alter (utility to get a Monoid rather than the Plus for what it wraps)
-   Ap (Derive a Semigroup or Monoid instance from an Apply or Applicative)
-   Generator / Reducer


## Monad Transformers

-   MonadTrans
-   ComonadTrans

-   Kleisli (or does this need to come earlier?)
-   Cokleisli

-   IdT
-   BijectionT
-   EitherT
-   FreeT
-   IndexedContsT
-   LazyEitherT
-   LazyOptionT
-   ListT
-   MaybeT
-   OptionT
-   ReaderWriterStateT
-   StateT / State
-   StoreT
-   StreamT
-   TheseT
-   TracedT
-   UnwriterT
-   WriterT


## Monads

But these feel like they belong in a chapter about data types...

-   FreeAp
-   Cofree
-   Free


# Advanced Monads

You have to know things like Advanced Monads in order to be
an advanced functional programmer.

incl ComonadStore

incl monad transformers

functor and applicative compose, monad doesn't, it's annoying, one or two detailed examples but mostly just listing what is available.

i.e. Effects

And also the issue of parallelisation of applicatives vs the sequential nature of Monad

<https://www.irccloud.com/pastebin/dx1r05od/>

{lang="text"}
~~~~~~~~
  trait ApMonad[F[_], G[_]] {
    def to[A](fa: F[A]): G[A]
    def from[A](ga: G[A]): F[A]
    implicit val fmonad: Monad[F]
    implicit val gap: Applicative[G]
  }
~~~~~~~~


## Free Monad


### [smock](https://github.com/djspiewak/smock)

-   FIXME this is old text, need to rewrite Chapter 3 using explicit scalaz Free Monad boilerplate

What we've been doing in this chapter is using the *free monad*,
`cats.free.Free`, to build up the definition of our program as a data
type and then we interpret it. Freestyle calls it `FS`, which is just
a type alias to `Free`, hiding an irrelevant type parameter.

The reason why we use `Free` instead of just implementing `cats.Monad`
directly (e.g. for `Id` or `Future`) is an unfortunate consequence of
running on the JVM. Every nested call to `map` or `flatMap` adds to
the stack, eventually resulting in a `StackOverflowError`.

`Free` is a `sealed abstract class` that roughly looks like:

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A] {
    def pure(a: A): Free[S, A] = Pure(a)
    def map[B](f: A => B): Free[S, B] = flatMap(a => Pure(f(a)))
    def flatMap[B](f: A => Free[S, B]): Free[S, B] = FlatMapped(this, f)
  }
  
  final case class Pure[S[_], A](a: A) extends Free[S, A]
  final case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
  final case class FlatMapped[S[_], B, C](
                                    c: Free[S, C],
                                    f: C => Free[S, B]) extends Free[S, B]
~~~~~~~~

Its definition of `pure` / `map` / `flatMap` do not do any work, they
just build up data types that live on the heap. Work is delayed until
Free is *interpreted*. This technique of using heap objects to
eliminate stack growth is known as *trampolining*.

When we use the `@free` annotation, a `sealed abstract class` data
type is generated for each of our algebras, with a `final case class`
per method, allowing trampolining. When we write a `Handler`,
Freestyle is converting pattern matches over heap objects into method
calls.


### Free as in Monad

`Free[S[_], A]` can be *generated freely* for any choice of `S`, hence
the name. However, from a practical point of view, there needs to be a
`Monad[S]` in order to interpret it --- so it's more like an interest
only mortgage where you still have to buy the house at the end.


# Utilities


## Other

e.g. conversion utilities between things


## Laws


# FS2

Task, Stream

The basics, and covering the Effect, which can be our free monad.

Why streams are so awesome. I'd like a simple example here of reading
from a huge data source, doing parallel work and then writing out in
order to a (slower) device to demonstrate backpressure and constant
memory overhead. Maybe compare this vs hand rolled and akka streams
for a perf test?

Rewrite our business logic to be streaming, convert our GET api into a
`Stream` by polling.


# Implementing the Application

Pad out the application implementation with everything we've learnt.

May need union types, see <https://github.com/propensive/totalitarian>

Will probably be a big chapter. Maybe best to leave it for a final
part of the book?


## Spotting patterns, refactoring

Note that some of our algebras are actually common things and can be
rewritten: reader / writer / state / error / indexed monad. It's ok
that this is a step you can do later.


### perf numbers


# Dependent Types

Jons talks are usually good for this <https://www.youtube.com/watch?v=a1whaMzrtsY>


# Type Refinement

instead of needing those `error` calls in the first place, just don't
allow them to happen at your layer if you can get away with it.

Protect yourself from mistyping


# Generic Programming

-   a mini Shapeless for Mortals
-   typeclass derivation (UrlEncoding, QueryEncoding)
-   scalacheck-shapeless
-   cachedImplicit into a val
-   downside is compile time speeds for ADTs of 50+
-   alternative is <https://github.com/propensive/magnolia>
-   export-hook
-   some advanced cases, e.g. spray-json-shapeless stuff, typeclass
    hierarchy / ambiguities
-   <https://issues.scala-lang.org/browse/SI-2509>
-   gotchas with nested `object` and knownSubclasses
-   semi-auto


# Recursion Schemes


# Optics

not sure what the relevance to this project would be yet.


# Category Theory

Just some of the high level concepts, where to get started if you're interested.
Not needed to write FP but it is needed if you want to read any academic papers.

<https://mobile.twitter.com/ctford/status/887216797421842433>

"These dynamic langs are so sloppy. We should be more rigorous, like maths."
"Cool! What does maths use to indicate types?"
"Fonts, mostly." -- Chris Ford


## Reality Check

In this chapter we've experienced some of the practical benefits of FP
when designing and testing applications:

1.  clean separation of components
2.  isolated, fast and reproducible tests of business logic: extreme mocking
3.  easy parallelisation

However, even if we look past the learning curve of FP, there are
still some real challenges that remain:

1.  trampolining has a performance impact due to increased memory churn
    and garbage collection pressure.
2.  there is not always IDE support for the advanced language features,
    macros or compiler plugins.
3.  implementation details --- as we have already seen with `for`
    syntax sugar, `@module`, and `Free` --- can introduce mental
    overhead and become a blocker when they don't work.
4.  the distinction between pure / side-effecting code, or stack-safe /
    stack-unsafe, is not enforced by the scala compiler. This requires
    developer discipline.
5.  the developer community is still small. Getting help from the
    community can often be a slow process.

As with any new technology, there are rough edges that will be fixed
with time. Most of the problems are because there is a lack of
commercially-funded tooling in FP scala. If you see the benefit of FP,
you can help out by getting involved.

Although FP Scala cannot be as fast as streamlined Java using
mutation, the performance impact is unlikely to affect you if you're
already considering targetting the JVM. Measure the impact before
making a decision if it is important to you.

In the following chapters we are going to learn some of the vast
library of functionality provided by the ecosystem, how it is
organised and how you can find what you need (e.g. how did we know to
use `foldM` or `traverse` when we implemented `act`?). This will allow
us to complete the implementation of our application by building
additional layers of `@module`, use better alternatives to `Future`,
and remove redundancy that we've accidentally introduced.


