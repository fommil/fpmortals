

# Cats

In this chapter we will tour the data types and typeclasses in cats
and its related ecosystem. We don't use all these types in
`drone-dynamic-agents` so we will give standalone usecases.

There has been criticism of the naming in cats, and functional
programming in general. Cats is a word play on *Category Theory* and
many of the concepts originate from academia, much as object oriented
programming originated from academic research in the 1960s (with their
high falutin words like *polymorphism*, *subtyping* and *generics*).
We'll use the cats names in this book, but feel free to set up `type`
aliases if you would prefer to use names based on the primary
functionality of the typeclass.

Before we introduce the complete typeclass hierarchy, we will look at
the three most important typeclasses from a control flow perspective:
`cats.Functor`, `cats.Applicative` and `cats.Monad` (each extending
the former).

In Chapters 1 to 3 we explored how `for` comprehensions can be used to
write nested `flatMap` calls, which allows us to reach into a runtime
context such as a `Future` and sequence operations that call out to an
*algebra* that may interact with the world.

The concept of sequencing operations that depend on runtime values is
formalised by the `Monad` typeclass, which takes data `A` within a
context `F[_]`, and allows us to provide pure functions that return
another value within the same context `F[B]`.

Another way of looking at data within a context `F[A]` is to think of
it as a *program* `F[_]` with `A` as the output: `flatMap` allows us
to generate programs at runtime based on the results of running
previous programs.

However, much functionality can be written without having to resort to
the full power of a `Monad`. If a function wishes to transform the
result of running a program, that's just `map`, and your function need
only request a `Functor`. As we seen in Chapter 3, we can create a
product of programs and run them in parallel by calling their
`Functor.map`.

In Functional Programming, parallel computations are considered **less**
powerful than sequential ones, so you are encouraged to be as parallel
as possible.

That said, an `Applicative` is often required for its `pure` method
which allows lifting a pure value into the context.

| Typeclass     | Method    | From   |               | To     |
|------------- |--------- |------ |------------- |------ |
| `Monad`       | `flatMap` | `F[A]` | `(A => F[B])` | `F[B]` |
| `Applicative` | `pure`    | `A`    |               | `F[A]` |
| `Functor`     | `map`     | `F[A]` | `(A => B)`    | `F[B]` |

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

This may bring back memories of `Numeric` from Chapter 4, which tried
to do too much and was unusable beyond the most basic of number types.
Of course there are implementations of `Group` for all the primitive
numbers, but the generalisation of *combinable* things is useful in
its own right.

Consider the case where business rules for a trading system are
implemented as a collection of partial configurations in a template
database. The default values for a trade are built up by combining
specific templates for that trade, in an order prescribed by the
business as "last rule wins". Here we'll create a simple set of rules,
but keep in mind that a realistic templating system would have
hundreds of parameters in nested `case class`.

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
only need to call `combineAll`. But to call `combineAll` we must have
an instance of `Monoid[TradeTemplate]`. Cats provides generic
instances in the `kittens` library, so we do not need to write one.

However, generic derivation will fail because `Monoid[Option[T]]`
defers to `Monoid[T]` for the case where there are two `Some[T]` to be
combined and we have neither a `Monoid[Currency]` (kittens cannot
derive a `Monoid` for a coproduct) nor a `Monoid[Boolean]` (it could
be implemented with either `&` or `|` logic so cats leaves it to the
user).

For example, `Monoid[Option[Int]]` behaves like this:

{lang="text"}
~~~~~~~~
  scala> Option(2) |+| None
  res: Option[Int] = Some(2)
  scala> Option(2) |+| Option(1)
  res: Option[Int] = Some(3)
~~~~~~~~

and we can see the content's `combine` has been called, which for
`Int` is integer addition. We've used the `|+|` op because it is more
common than the `combine` method. This op is known as the TIE Fighter
operator and there is an Advanced TIE Fighter in the next section,
because awesome.

Our business rules state that the "last rule wins" which means that we
can implement a simple, higher priority, `Monoid[Option[T]]` and use it
during our generic derivation instead of the cats default one:

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
  scala> import cats._
         import cats.implicits._
         import cats.derived._, monoid._, legacy._
         import java.time.LocalDate
  
  scala> implicitly[Monoid[TradeTemplate]]
  res: cats.Monoid[TradeTemplate] = cats.derived.MkMonoid$$anon
  
  scala> val templates = List(
           TradeTemplate(Nil, None, None),
           TradeTemplate(Nil, Some(EUR), None),
           TradeTemplate(List(LocalDate.of(2017, 8, 5)), Some(USD), None),
           TradeTemplate(List(LocalDate.of(2017, 9, 5)), None, Some(true)),
           TradeTemplate(Nil, None, Some(false))
         )
  
         Monoid[TradeTemplate].combineAll(templates)
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

This approach has saved the author from writing **thousands** of lines
of code meeting a related business requirement.

The `Commutative*` variants have an additional requirement / law
imposed upon them that the order of parameters to `combine` does not
matter. Our trading example is most definitely **non** commutative,
since the order of application is important, however this is a useful
property to require if you are building a distributed system where
there are efficiencies when order is not important.

`Band`, `Semilattice` and `BoundedSemilattice` have the additional law
that the `combine` operation of the same two elements is *idempotent*,
i.e. gives the same value. An example is anything defined over a group
that can only be one value, such as `Unit`, or if the `combine` is a
least upper bound. If you ever need use these, you should probably
tweet about it because they exist for niche downstream mathematics
libraries `spire` and `algebird`.

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


