

# TODO Cats

## TODO typeclasses

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

# TODO Recursion Schemes

# TODO Optics

not sure what the relevance to this project would be yet.

# TODO Category Theory

Just some of the high level concepts, where to get started if you're interested.
Not needed to write FP but it is needed if you want to read any academic papers.


