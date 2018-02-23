
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
Scala with a stronger type system, immutable data and multiple
inheritance. This brought about a fusion of object oriented (OOP) and
functional programming (FP).

For most developers, FP means using immutable data as much as
possible, but mutable state is still a necessary evil that must be
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
blocking. In either case, it is a lot of boilerplate and we are
fundamentally dealing with different APIs that are not unified.

Let's try to solve the problem like Java 1.2 by introducing a common
parent. To do this, we need to use the *higher kinded types* (HKT)
Scala language feature.

A> **Higher Kinded Types** allow us to use a *type constructor* in our type
A> parameters, which looks like `C[_]`. This is a way of saying that
A> whatever `C` is, it must take a type parameter. For example:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   trait Foo[C[_]] {
A>     def create(i: Int): C[Int]
A>   }
A> ~~~~~~~~
A> 
A> `List` is a type constructor because it takes a type (e.g. `Int`) and
A> constructs a type (`List -> Int -> List[Int]`). We can implement `Foo`
A> using `List`:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   object FooList extends Foo[List] {
A>     def create(i: Int): List[Int] = List(i)
A>   }
A> ~~~~~~~~
A> 
A> We can implement `Foo` for anything with a type parameter hole, e.g.
A> `Either[String, _]`. Unfortunately it is a bit clunky and we have to
A> create a type alias to trick the compiler into accepting it:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   type EitherString[T] = Either[String, T]
A> ~~~~~~~~
A> 
A> Type aliases don't define new types, they just use substitution and
A> don't provide extra type safety. The compiler substitutes
A> `EitherString[T]` with `Either[String, T]` everywhere. This technique
A> can be used to trick the compiler into accepting types with one hole
A> when it would otherwise think there are two, like when we implement
A> `Foo` with `EitherString`:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   object FooEitherString extends Foo[EitherString] {
A>    def create(i: Int): Either[String, Int] = Right(i)
A>   }
A> ~~~~~~~~
A> 
A> Alternatively, the [kind projector](https://github.com/non/kind-projector/) plugin allows us to avoid the `type`
A> alias and use `?` syntax to tell the compiler where the type hole is:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   object FooEitherString extends Foo[Either[String, ?]] {
A>    def create(i: Int): Either[String, Int] = Right(i)
A>   }
A> ~~~~~~~~
A> 
A> Finally, there is this one weird trick we can use when we want to
A> ignore the type constructor. Let's define a type alias to be equal to
A> its parameter:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   type Id[T] = T
A> ~~~~~~~~
A> 
A> Before proceeding, convince yourself that `Id[Int]` is the same thing
A> as `Int`, by substituting `Int` into `T`. Because `Id` is a valid type
A> constructor we can use `Id` in an implementation of `Foo`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   object FooId extends Foo[Id] {
A>     def create(i: Int): Int = i
A>   }
A> ~~~~~~~~

We want to define `Terminal` for a type constructor `C[_]`. By
defining `Now` to construct to its type parameter (like `Id`), we can
implement a common interface for synchronous and asynchronous
terminals:

{lang="text"}
~~~~~~~~
  trait Terminal[C[_]] {
    def read: C[String]
    def write(t: String): C[Unit]
  }
  
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
    def create[B](b: B): C[B]
  }
~~~~~~~~

letting us write:

{lang="text"}
~~~~~~~~
  def echo[C[_]](t: Terminal[C], e: Execution[C]): C[String] =
    e.doAndThen(t.read) { in: String =>
      e.doAndThen(t.write(in)) { _: Unit =>
        e.create(in)
      }
    }
~~~~~~~~

We can now share the `echo` implementation between synchronous and
asynchronous codepaths. We can write a mock implementation of
`Terminal[Now]` and use it in our tests without any timeouts.

Implementations of `Execution[Now]` and `Execution[Future]` are
reusable by generic methods like `echo`.

But the code for `echo` is horrible! Let's clean it up.

The `implicit class` Scala language feature gives `C` some methods.
We'll call these methods `flatMap` and `map` for reasons that will
become clearer in a moment. Each method takes an `implicit
Execution[C]`, but this is nothing more than the `flatMap` and `map`
that you're used to on `Seq`, `Option` and `Future`

{lang="text"}
~~~~~~~~
  object Execution {
    implicit class Ops[A, C[_]](c: C[A]) {
      def flatMap[B](f: A => C[B])(implicit e: Execution[C]): C[B] =
            e.doAndThen(c)(f)
      def map[B](f: A => B)(implicit e: Execution[C]): C[B] =
            e.doAndThen(c)(f andThen e.create)
    }
  }
  
  def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
    t.read.flatMap { in: String =>
      t.write(in).map { _: Unit =>
        in
      }
    }
~~~~~~~~

We can now reveal why we used `flatMap` as the method name: it lets us
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

Our `Execution` has the same signature as a trait in scalaz called
`Monad`, except `doAndThen` is `flatMap` and `create` is `pure`. We
say that `C` is *monadic* when there is an implicit `Monad[C]`
available. In addition, scalaz has the `Id` type alias.

The takeaway is: if we write methods that operate on monadic types,
then we can write sequential code that abstracts over its execution
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
about our code. Caching is easier to understand with determinism and
purity, and input validation is easier to isolate with totality.

The kinds of things that break these properties are *side effects*:
accessing or changing mutable state (e.g. generating random numbers,
maintaining a `var` in a class), communicating with external resources
(e.g. files or network lookup), or throwing exceptions.

But in Scala, we perform side effects all the time. A call to
`log.info` will perform I/O and a call to `asString` on a `Http`
instance will speak to a web server. It is fair to say that typical
Scala is **not** FP.

However, something beautiful happened when we wrote our implementation
of `echo`. Anything that depends on state or external resources is
provided as an explicit input: our functions are deterministic and
pure. We not only get to abstract over execution environment, but we
also get to dramatically improve the repeatability - and performance -
of our tests. We are free to implement `Terminal` without any
interactions with a real console.

Of course we cannot write an application devoid of interaction with
the world. In FP we push the code that deals with side effects to the
edges, using battle-tested libraries like NIO, Akka and Play, isolated
away from the core business logic.

This book expands on the FP style introduced in this chapter. We're
going to use the traits and classes defined in the *scalaz* and *fs2*
libraries to implement streaming applications. We'll also use
developer tooling to eliminate some of the boilerplate we've already
seen in this chapter, allowing you to focus on writing pure business
logic. We'll also discover how to write a pure implementation of
`Terminal` that uses something much better than `Id` or `Future`.


