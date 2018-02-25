
### TODO `WriterT`

including `UnwriterT`


### TODO `StateT`

`ReaderWriterStateT`


### TODO `TheseT`


### TODO `StreamT`


### TODO `ContT`

Specialisations of ContT like Condensity also have their own nice use cases e.g.
reassociating binds to make them linear rather than quadratic or abstract over
bracketed functions like withFile (ResourceT, Managed)

-   `IndexedContsT`
-   `ResourceT`

Also research MonadBracket


### TODO `IdT`


### TODO Others

-   `BijectionT` ??? (a bit weird)
-   `StoreT` (or leave to lenses)

Probably ignore...

-   `Cokleisli`
-   `ComonadStore`
-   `TracedT` (comonad tranformer)


## TODO Parallelism and `Task`

The issue of parallelisation of applicatives vs the sequential nature of Monad.

Can use this to motivate the scalaz8 typeclass design.


## TODO The `Free Monad`

`Free` as the algebra for Terminal.

`Free[S[_], A]` can be *generated freely* for any choice of `S`, hence
the name. However, from a practical point of view, there needs to be a
`Monad[S]` in order to interpret it --- so it is more like an interest
only mortgage where you still have to buy the house at the end.

introduce `FreeAp` and `Cofree`

Trampolining is expensive, what does it buy us? Move onto Drone / Machines and
optimise for bulk read / write. Analogous to bytecode instrumentation.

And for testing: [smock](https://github.com/djspiewak/smock)


## TODO Initial vs Final Encoding

Then (much more complicated) optimisation for final encoding of our drone app.

Try this with the `act` in our example app. It is tricky because we have two algebras.

{lang="text"}
~~~~~~~~
  trait Program[Alg[_[_]], A] {
    def apply[F[_]: Applicative](interpreter: Alg[F]) : F[A]
  }
  
  trait Optimizer[Alg[_[_]], F[_]] {
    type M
  
    def monoidM: Monoid[M]
    def monadF: Monad[F]
  
    def extract: Alg[Const[M, ?]]
    def rebuild(m: M, interpreter: Alg[F]): F[Alg[F]]
  
    def optimize[A](p: Program[Alg, Applicative, A]): Alg[F] => F[A] = { interpreter =>
      implicit val M: Monoid[M] = monoidM
      implicit val F: Monad[F] = monadF
  
      val m: M = p(extract).getConst
  
      rebuild(m, interpreter).flatMap(interp => p(interp))
    }
  }
~~~~~~~~


# TODO Utilities

I'm not sure about this chapter. It may be the case that everything listed here
is not really worth mentioning, except for a few things.


## TODO stdlib helpers


## TODO Refinements

(kick to later)

-   Alpha (represents an alphabetic character), maybe save for generators / laws
-   Digit (0-9)
-   CaseInsensitive
-   Endo (just wraps A => A)


## TODO Missed typeclasses

-   Semilattice (added recently, commutative Band)
-   Distributive (dual of Traverse)
-   Zap (Functors that destroy each other)


## TODO weird shit (mostly type tricks)

-   Adjunction (two functors `F` and `G` that give rise to a monad / comonad)
-   Codensity (seems to be a monad of some kind)
-   Zipper / TreeLoc (as discussed earlier)
-   Unapply (workaround for type inference) ... maybe one for data types?
-   Kan / Coyoneda / Yoneda / ContravariantCoyoneda
-   Endomorphic / Isomorphism (will need arrows)
-   Forall (universal quantification)
-   Injective (proof stuff)
-   Inject (something about data types a la carte... seems important)
-   Lens / PLens ... tricky, we really want to use monocle
-   MonoidCoproduct lists of disjunction of monoidal things
-   NotNothing (mini version of shapeless' equivalent)
-   NullArgument (?=> ... drugged up elvis)
-   NullResult
-   Representable (should probably be a typeclass, X => A implies F[A]: Functor)
-   Dual


## TODO Conversions

-   Alter (utility to get a Monoid rather than the Plus for what it wraps)
-   Ap (Derive a Semigroup or Monoid instance from an Apply or Applicative)
-   Generator


# TODO Typeclass Derivation

-   scalaz-deriving
-   magnolia
-   shapeless
-   macros
-   export-hook


# TODO Refinement

-   contextual vs refined
-   Consider using refined's `String Refined Url` as an alternative to my contextual `Url`

<https://github.com/fthomas/refined/blob/master/modules/core/jvm/src/test/scala/eu/timepit/refined/StringValidateSpecJvm.scala>
 <https://github.com/fthomas/refined/blob/master/modules/jsonpath/jvm/src/main/scala/eu/timepit/refined/jsonpath/string.scala>


# TODO Testing / Laws

needs to come after scalaz-deriving, to understand `Arbitrary`


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


# TODO Dependent Types

Jons talks are usually good for this <https://www.youtube.com/watch?v=a1whaMzrtsY>


# TODO Type Refinement

instead of needing those `error` calls in the first place, just don't
allow them to happen at your layer if you can get away with it.

Protect yourself from mistyping


# TODO Recursion Schemes


# TODO Optics

not sure what the relevance to this project would be yet.


# TODO Implementing the Application

Pad out the application implementation with everything we've learnt.

May need union types, see <https://github.com/propensive/totalitarian>

Will probably be a big chapter. Maybe best to leave it for a final
part of the book?


## TODO Spotting patterns, refactoring

Note that some of our algebras are actually common things and can be
rewritten: reader / writer / state / error / indexed monad. It is ok
that this is a step you can do later.


### RESEARCH perf numbers


# TODO Category Theory

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


