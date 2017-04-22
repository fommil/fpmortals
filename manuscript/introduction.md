

# Introduction

It is human instinct to be sceptical of a new paradigm. To put some
perspective on how far we have come, and the shifts we have already
accepted on the JVM, let's start with a quick recap of the last 20
years.

Java 1.2 introduced the Collections API, allowing us to write methods
that abstracted over mutable collections. It was useful for writing
general purpose algorithms and was the bedrock of our codebases.

But there was a problem, we had to perform runtime casting:

{lang="text"}
~~~~~~~~
public String first(Collection collection) {
  return (String)(collection.get(0));
}
~~~~~~~~

In response, developers defined domain objects in their business logic
that were effectively `CollectionOfThings`, and the Collection API
became implementation detail.

In 2005, Java 5 introduced *generics*, allowing us to define
`Collection<Thing>`, abstracting over the container **and** its
elements. Generics changed how we wrote Java.

The author of the Java generics compiler, Martin Odersky, then created
Scala with a stronger type system, immutable data structures and
multiple inheritance. This brought about a fusion of object oriented
(OOP) and functional programming (FP).

For most developers, FP means using immutable data structures as much
as possible, but mutable state is still a necessary evil that must be
isolated and managed, e.g. with Akka actors or `synchronized` classes.
This style of FP results in simpler programs that are easier to
parallelise and distribute, an improvement over Java. But it is only
scratching the surface of the benefits of FP, as we'll discover in
this book.

Scala also brings `Future`, making it easy to write asynchronous
applications. But when a `Future` makes it into a return type,
*everything* needs to be rewritten to accomodate it, including the
tests, which are now subject to arbitrary timeouts.

We have a problem similar to Java 1.0: there is no way of abstracting
over execution, much as we had no way of abstracting over collections.

## Abstracting over Execution

Let's say we want to interact with the user over the command line
interface. We can `read` what the user types and we can `write` a
message to them.

{lang="text"}
~~~~~~~~
trait TerminalSync {
  def read(): String
  def write(t: String): Unit
}

trait TerminalAsync {
  def read(): Future[String]
  def write(t: String): Future[Unit]
}
~~~~~~~~

But how do we write generic code that does something as simple as echo
the user's input synchronously or asynchronously depending on our
runtime implementation?

We could write a synchronous version and wrap it with `Future` but now
we have to worry about which thread pool we should be using for the
work, or we could `Await.result` on the `Future` and introduce thread
blocking. In either case, it's a lot of boilerplate and we are
fundamentally dealing with different APIs that are not unified.

Let's try to solve the problem like Java 1.2 by introducing a common
parent. To do this, we need to use the *higher kinded types* Scala
language feature.

A> **Higher Kinded Types** allow us to use a *type constructor* in our type
A> parameters, which looks like `C[_]`. This is a way of saying that
A> whatever `C` is, it must take a type parameter. For example:
A> 
A> {lang="text"}
A> ~~~~~~~~
A> trait Foo[C[_]] {
A>   def wrap(i: Int): C[Int]
A> }
A> ~~~~~~~~
A> 
A> A type constructor is syntax for a type that takes a type to
A> constructs another type. `List` is a type constructor because it takes
A> a type (e.g. `Int`), and constructs the type `List[Int]`. We can
A> implement `Foo` using `List`:
A> 
A> {lang="text"}
A> ~~~~~~~~
A> object FooList extends Foo[List] {
A>   def wrap(i: Int): List[Int] = List(s)
A> }
A> ~~~~~~~~
A> 
A> We can also implement `Foo` for anything with a type parameter hole,
A> e.g. `Either[String, _]`. Unfortunately it is a bit clunky and we have
A> to create a type alias:
A> 
A> {lang="text"}
A> ~~~~~~~~
A> type EitherString[T] = Either[String, T]
A> object FooEitherString extends Foo[EitherString] {
A>  def wrap(i: Int): Either[String, Int] = Right(s)
A> }
A> ~~~~~~~~
A> 
A> There is an interesting trick we can use when we want to ignore the
A> type constructor. Recall that type aliases don't define any new types,
A> they just use substitution so we can create convenient names. Let's
A> define a type alias to be equal to its parameter:
A> 
A> {lang="text"}
A> ~~~~~~~~
A> type Id[T] = T
A> ~~~~~~~~
A> 
A> Before proceeding, convince yourself that `Id[Int]` is the same thing
A> as `Int`, by substituting `Int` into `T`. But `Id` is a valid type
A> constructor, so we can use `Id` in an implementation of `Foo`:
A> 
A> {lang="text"}
A> ~~~~~~~~
A> object FooId extends Foo[Id] {
A>   def wrap(i: Int): Int = s
A> }
A> ~~~~~~~~

In our case, we want to define `Terminal` for a type constructor
`C[_]` allowing us to put `C[String]` and `C[Unit]` in our method
signatures:

{lang="text"}
~~~~~~~~
trait Terminal[C[_]] {
  def read: C[String]
  def write(t: String): C[Unit]
}
~~~~~~~~

By defining `Now` to construct to its type parameter (like `Id`), we
can implement a common interface for synchronous and asynchronous
terminals:

{lang="text"}
~~~~~~~~
type Now[X] = X

object TerminalSync extends Terminal[Now] {
  def read: String = ???
  def write(t: String): Unit = ???
}

object TerminalAsync extends Terminal[Future] {
  def read: Future[String] = ???
  def write(t: String): Future[Unit] = ???
}
~~~~~~~~

You can think of `C` as a *Context* because we say "in the context of
executing `Now`" or "in the `Future`".

But we know nothing about `C` and we can't do anything with a
`C[String]`. What we need is a kind of execution environment that lets
us call a method returning `C[T]` and then be able to do something
with the `T`, including calling another method on `Terminal`. We also
need a way of wrapping a value as a `C[_]`. This signature works well:

{lang="text"}
~~~~~~~~
trait Execution[C[_]] {
  def doAndThen[A, B](c: C[A])(f: A => C[B]): C[B]
  def wrap[B](b: B): C[B]
}
~~~~~~~~

letting us write:

{lang="text"}
~~~~~~~~
def echo[C[_]](t: Terminal[C], e: Execution[C]): C[String] =
  e.doAndThen(t.read) { in: String =>
    e.doAndThen(t.write(in)) { _: Unit =>
      e.wrap(in)
    }
  }
~~~~~~~~

We can now share the `echo` implementation between synchronous and
asynchronous codepaths!

We only need to write an implementation for `Execution[Now]` and
`Execution[Future]` once and we can reuse it forever, for any method
like `echo`. We can trivially write a mock implementation of
`Terminal[Now]` and use it in our tests.

But the code is horrible! Let's use the `implicit class` Scala
language feature (aka "enriching", "ops" or "syntax") to give `C` some
nicer methods when there is an implicit `Execution` available. We'll
call these methods `flatMap` and `map` for reasons that will become
clearer in a moment:

{lang="text"}
~~~~~~~~
object Execution {
  implicit class Ops[A, C[_]](val c: C[A]) extends AnyVal {
    def flatMap[B](f: A => C[B])(implicit e: Execution[C]): C[B] =
          e.doAndThen(c)(f)
    def map[B](f: A => B)(implicit e: Execution[C]): C[B] =
          e.doAndThen(c)(f andThen e.wrap)
  }
}
~~~~~~~~

which cleans up `echo` a little bit

{lang="text"}
~~~~~~~~
def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
  t.read.flatMap { in: String =>
    t.write(in).map { _: Unit =>
      in
    }
  }
~~~~~~~~

we can now reveal why we used `flatMap` as the method name: it lets us
use a *for comprehension*, which is just syntax sugar over nested
`flatMap` and `map`.

{lang="text"}
~~~~~~~~
def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
  for {
    in <- t.read
     _ <- t.write(in)
  } yield in
~~~~~~~~

Our `Execution` has the same signature as a trait in the cats library
called `Monad` (except `doAndThen` is `flatMap` and `wrap` is `pure`).
We say that `C` is *monadic* when there is an implicit `Monad[C]`
available. In addition, cats has the `Id` type alias.

The takeaway is: if we write methods that operate on monadic types,
then we can write procedural code that abstracts over its execution
context. Here, we have shown an abstraction over synchronous and
asynchronous execution but it can also be for the purpose of more
rigorous error handling (where `C[_]` is `Either[Error, _]`), managing
access to volatile state, performing I/O, or auditing of the session.

## Pure Functional Programming

FP functions have three key properties:

-   **Totality** return a value for every possible input
-   **Determinism** return the same value for the same input
-   **Purity** the only effect is the computation of a return value.

Together, these properties give us an unprecedented ability to reason
about our code. For example, caching is easier to understand with
determinism and purity, and input validation is easier to isolate with
totality.

The kinds of things that break these properties are *side effects*:
accessing or changing mutable state (e.g. generating random numbers,
maintaining a `var` in a class), communicating with external resources
(e.g. files or network lookup), or throwing exceptions.

But in Scala, we perform side effects all the time. A call to
`log.info` will perform I/O and a call to `asString` on a `Http`
instance will speak to a web server. It's fair to say that typical
Scala is **not** FP.

However, something beautiful happened when we wrote our implementation
of `echo`. Anything that depends on state or external resources is
provided as an explicit input: our functions are deterministic and
pure. We not only get to abstract over execution environment, but we
also get to dramatically improve the repeatability - and performance -
of our tests. For example, we are free to implement `Terminal` without
any interactions with a real console.

Of course we cannot write an application devoid of interaction with
the world. In FP we push the code that deals with side effects to the
edges. That kind of code can use battle-tested libraries like NIO,
Akka and Play, isolated away from the core business logic.

This book expands on the FP style introduced in this chapter. We're
going to use the traits and classes defined in the *cats* and *fs2*
libraries to implement streaming applications. We'll also use the
*freestyle* and *simulacrum* developer tooling to eliminate
boilerplate some of the boilerplate we've already seen in this
chapter, allowing you to focus on writing pure business logic.


