

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
functionality of the typeclass. We'll suggest some aliases as we go.

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

| Typeclass     | Method    | From   |             | To     |
|------------- |--------- |------ |----------- |------ |
| `Monad`       | `flatMap` | `F[A]` | `(A=>F[B])` | `F[B]` |
| `Applicative` | `pure`    | `A`    |             | `F[A]` |
| `Functor`     | `map`     | `F[A]` | `(A=>B)`    | `F[B]` |

When it comes to depending on typeclasses, functional programmers and
object oriented programmers agree: depend on the most general (least
powerful) interface that you can. In contrast to data types, where
parameters should be as specific as possible to forbid impossible
states.

## TODO effects

dare we introduce some basic effects first? reader / writer / state /
error. They are particularly practical.

## TODO typeclasses

Overwhelming, so we'll try to visualise.

Cheat sheet <http://arosien.github.io/scalaz-cheatsheets/typeclasses.pdf>

<https://github.com/tpolecat/cats-infographic>

Foldable being imminently more interesting than the others.

Traversable will need to be discussed, seems to come up a lot.

Use (impure) example of merging two deep configuration ADTs (scala
does not enforce purity so we can choose our own level)

Not enough to implement, must also pass the laws

The most important methods on `Monad` are

-   `pure(a: A)` creates a new program from a value
-   `map[B](f: A => B)` translates the result of running a previous
    program
-   `flatMap[B](f: A => FreeS[F, B])` creates a new program from the
    result of running a previous program

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

# TODO Recursion Schemes

# TODO Optics

not sure what the relevance to this project would be yet.

# TODO Category Theory

Just some of the high level concepts, where to get started if you're interested.
Not needed to write FP but it is needed if you want to read any academic papers.


