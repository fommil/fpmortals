

# Data and Functionality

From OOP we are used to thinking about data and functionality
together: class hierarchies carry methods, and traits can demand that
data fields exist. Polymorphism of an object is in terms of "is a"
relationships, requiring classes to inherit from common interfaces.
This can get messy as a codebase grows. Simple data structures become
obscured by hundreds of lines of methods, trait mixins suffer from
order initialisation errors, and testing / mocking of highly coupled
components becomes a chore.

FP takes a different approach, defining data and functionality
separately. In this chapter, we'll cover the basics of data structures
and the advantages of constrainting ourselves to a subset of the Scala
language. We will also discover *type classes* as an alternative for
polymorphism: thinking about data in terms of "has a" rather than "is
a" relationships.

## Data

In FP we make data structures explicit, rather than hidden as
implementation detail.

If you know the details of the Java or Scala standard library
Collections implementations, you might think about them in terms of
their data structures rather than the functionality they provide:
`HashMap` vs `TreeMap`, `LinkedList` vs `ArrayList`, `List` vs
`Vector`. This is useful to reason about memory footprint and
performance.

The fundamental building blocks of data structures are

-   `final case class` also known as *products*
-   `case object` also known as *singletons*
-   `sealed trait` also known as *coproducts*

In fact singletons are just products in disguise, so we can think
about data in terms of *products* and *coproducts*.

The collective name for products and coproducts is *Algebraic Data
Type* (ADT), an unfortunate and unrelated name collision with the
algebras that we seen in the previous chapter. In this case, we
compose data structures out of the `AND` and `XOR` algebra: a product
(`case class`) always has every field, but a coproduct (`sealed
trait`) must only be one of the possible implementations.

It is important that we use `sealed trait`, not just `trait`, when
defining a data structure. Sealing a trait means that all
implementations must be defined in the same file, allowing the
compiler to know about them in pattern match exhaustivity checks and
in macros to eliminate boilerplate.

When we introduce a type parameter into a `sealed trait` or `case
class`, we call it a *Generalised Algebraic Data Type*. `List` is a
GADT:

{lang="text"}
~~~~~~~~
  sealed trait List[+T]
  case object Nil extends List[Nothing]
  final case class ::[+T](head: T, tail: List[T]) extends List[T]
~~~~~~~~

If an ADT refers to itself, we call it a *recursive type*. Scala's
`List` is recursive because the `::` (pronounced "cons" as in
constructor) contains a reference to a `List`.

A `sealed trait` containing only singletons is equivalent to a simple
`enum` in Java and is a more descriptive replacement for a `Boolean`
or `Int` feature flag.

A> **Exhaustivity caveats**
A> 
A> We add `-Xfatal-warnings` to our compiler flags and are forced to
A> update any code that matches over a `sealed trait` when somebody adds
A> an extra implementation.
A> 
A> Although the scala compiler will perform exhaustivity checks when
A> matching on sealed traits, e.g.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> sealed trait Foo
A>          final case class Bar(flag: Boolean) extends Foo
A>          final case object Baz extends Foo
A>   
A>   scala> def thing(foo: Foo) = foo match {
A>            case Bar(_) => true
A>          }
A>   <console>:14: error: match may not be exhaustive.
A>   It would fail on the following input: Baz
A>          def thing(foo: Foo) = foo match {
A>                                ^
A> ~~~~~~~~
A> 
A> the compiler will not perform exhaustivity checking if there are
A> guards, e.g.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> def thing(foo: Foo) = foo match {
A>            case Bar(flag) if flag => true
A>          }
A>   
A>   scala> thing(Baz)
A>   scala.MatchError: Baz (of class Baz$)
A>     at .thing(<console>:15)
A> ~~~~~~~~
A> 
A> Note that the implementations of `Foo` are not the only concern here:
A> this will fail at runtime if we pass a `Bar(false)`.
A> 
A> Guards should not be used when matching on a `sealed trait`, and when
A> used on a `case class` should always include a `case _ =>` catch-all
A> with a default value unless you have proven that it cannot occur.

### Convey Information

Besides being a container for necessary business information, data
structure can be used to encode constraints. For example,

{lang="text"}
~~~~~~~~
  final case class NonEmptyList[+T](head: T, tail: List[T])
~~~~~~~~

can never be empty because it is impossible to construct an empty
instance. This makes `cats.data.NonEmptyList` a useful data structure
despite containing the same information as `List`.

In addition, wrapping a class can convey information such as if a
class contains valid entries. Instead of breaking *totality* by
throwing an exception

{lang="text"}
~~~~~~~~
  final case class Person(name: String, age: Int) {
    require(name.nonEmpty && age > 0) // breaks totality, don't do this
  }
~~~~~~~~

we can use the `scala.Either` data type to provide `Right[Person]`
instances and protect invalid instances from propagating:

{lang="text"}
~~~~~~~~
  final case class Person private(name: String, age: Int)
  object Person {
    def apply(name: String, age: Int): Either[String, Person] = {
      if (name.nonEmpty && age > 0) Right(new Person(name, age))
      else Left(s"bad input: $name, $age")
    }
  }
  
  def welcome(person: Person): String =
    s"${person.name} you look wonderful at ${person.age}!"
  
  for {
    person <- Person("", -1)
  } yield welcome(person)
~~~~~~~~

We will see an even better way of reporting validation errors when we
introduce `cats.data.Validation` in the next chapter.

### Counting Complexity

The complexity of a data type is the number of instances that can
exist.

-   `Boolean` has two values
-   `Int` has 2<sup>32</sup> - 1 values
-   `String` has effectively infinite values

To find the complexity of a product, synonymous with a tuple, we
multiply the complexity of each part.

-   `(Boolean, Boolean)` has 4 values (`2*2`)
-   `(Boolean, Boolean, Boolean)` has 8 values (`2*2*2`)

To find the complexity of a coproduct, synonymous with nested
`Either`, we add the complexity of each part.

-   `Either[Boolean, Boolean]` has 4 values (`2+2`)
-   `Either[Boolean, Either[Boolean, Boolean]]` has 6 values (`2+2+2`)

To find the complexity of a GADT, multiply by the complexity of the
type parameter for each entry:

-   `Option[Boolean]` has 3 values, `Some[Boolean]` and `None` (`2+1`)

We should always strive for simplicity over complexity. Let's say we
have three mutually exclusive configuration parameters `wobble`,
`quiver` and `fish`. The product `(wobble: Boolean, quiver: Boolean,
fish: Boolean)` has complexity 6 whereas the coproduct

{lang="text"}
~~~~~~~~
  sealed trait Wibble
  object Wibble {
    case object Wobble extends Wibble
    case object Quiver extends Wibble
    case object Fish extends Wibble
  }
~~~~~~~~

has a complexity of 3. It would be far better to model these
configuration parameters as a coproduct rather than allowing 3 invalid
states to exist.

1.  TODO arbitrary / testing

### TODO Optimisations

### TODO Generic Representation

## TODO Functionality

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

# TODO Typeclass Derivation

<https://github.com/propensive/magnolia>

# TODO Recursion Schemes

# TODO Optics

not sure what the relevance to this project would be yet.

# TODO Category Theory

Just some of the high level concepts, where to get started if you're interested.
Not needed to write FP but it is needed if you want to read any academic papers.


