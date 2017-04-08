

# Introduction

When hearing of a new paradigm, it is human instinct to be sceptical.
To put some perspective on how far we've come, and the paradigm shifts
we've adopted on the JVM, let's start with a quick recap of the last
25 years.

Java 1.2 introduced the Collections API, allowing us to write methods
that abstracted over mutable collections. It was useful for general
purpose algorithms and became a bedrock of business logic.

But there was a problem, we had to perform runtime casting:

{lang="java"}
~~~~~~~~
public String first(Collection collection) {
  return (String)(collection.get(0));
}
~~~~~~~~

In response, developers defined domain objects that were effectively
`CollectionOfThings`, and the Collection API became implementation
detail.

In 2005, Java 5 introduced Generics, allowing us to define
`Collection<Thing>`, abstracting over the container and its elements.
Generics changed how we wrote Java.

Then Scala arrived with terse syntax and a fusion of object oriented
(OOP) and functional programming (FP). For most developers, FP means
using immutable data structures as much as possible, but mutable state
still needs to be managed by Akka. That's just the way applications
work: they need to keep track of some information that changes over
time.

Scala also brings `Future`, making it easy to write asynchronous
applications. But when a `Future` makes it into a return type,
*everything* needs to be rewritten to accomodate it, including the
tests, which now are subject to arbitrary timeouts. We have a problem
similar to Java 1.0: there is no way of abstracting over execution,
much as we had no way of abstracting over collections.

## Abstracting over Execution

Let's say we want to interact with the user over the command line
interface. We can `read` what the user types and we can `write` a
message to them.

{lang="scala"}
~~~~~~~~
trait TerminalSync {
  def read(): String
  def write(t: String): Unit
}

trait TerminalFuture {
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
parent. We use the *higher kinded types* Scala language feature that
lets us abstract over a type that takes a single type parameter.

{lang="scala"}
~~~~~~~~
trait Terminal[C[_]] {
  def read: C[String]
  def write(t: String): C[Unit]
}
~~~~~~~~

The syntax `C[_]` is often called a *type constructor*. By definining
`Now` to construct to *itself* (a powerful trick) we can implement a
common interface for synchronous and asynchronous:

{lang="scala"}
~~~~~~~~
type Now[+X] = X

object TerminalSync extends Terminal[Now] {
  def read: String = ???
  def write(t: String): Unit = ???
}

object TerminalFuture extends Terminal[Future] {
  def read: Future[String] = ???
  def write(t: String): Future[Unit] = ???
}
~~~~~~~~

If you need a word to associate to `C`, *Context* is a reasonable
analogy because we can say "in the context of executing in the
`Future`" or "in the context of executing `Now`".

But we know nothing about `C` and if we get handed a `C[String]` we
can't get the `String`. However, even though `Now` and `Future` don't
share a common parent, we can depend on a parameterised trait that
will give us methods to call on `C`. What we need is a kind of
execution environment with this signature:

{lang="scala"}
~~~~~~~~
trait Execution[C[_]] {
  def doAndThen[A, B](m: C[A])(f: A => C[B]): C[B]
  def returns[B](b: B): C[B]
}
~~~~~~~~

letting us write (really ugly!) code like:

{lang="scala"}
~~~~~~~~
def echo[C[_]](t: Terminal[C], e: Execution[C]): C[String] =
  e.doAndThen(t.read) { in: String =>
    e.doAndThen(t.write(in)) { _: Unit =>
      e.returns(in)
    }
  }
~~~~~~~~

We can now share the `echo` implementation between synchronous and
asynchronous codepaths! We only need to write an implementation for
`Execution[Now]` and `Execution[Future]` once and we can reuse it
forever, for any method like this. We can trivially write a mock
implementation of `Terminal[Now]` and use it in a test for `echo`.

But the code is horrible. Let's use the `implicit class` Scala
language feature (aka "enriching" or "ops") to give `C` some nicer
methods when there is an implicit `Execution` available. We'll call
these methods `flatMap` and `map` for reasons that will become clearer
in a moment:

{lang="scala"}
~~~~~~~~
object Execution {
  implicit class Ops[A, C[_]](m: C[A])(implicit e: Execution[C]) {
    def flatMap[B](f: A => C[B]): C[B] = e.doAndThen(m)(f)
    def map[B](f: A => B): C[B] = e.doAndThen(m)(f andThen e.returns)
  }
}
~~~~~~~~

cleaning up `echo` a little bit

{lang="scala"}
~~~~~~~~
def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
  t.read.flatMap { in: String =>
    t.write(in).map { _: Unit =>
      in
    }
  }
~~~~~~~~

we can now reveal why we used `flatMap` as the method name: it lets us
use a *for comprehension*, which is just syntax sugar to re-write
nested calls to `flatMap` and `map`.

{lang="scala"}
~~~~~~~~
def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
  for {
    in <- t.read
     _ <- t.write(in)
  } yield in
~~~~~~~~

Now we admit that our `Execution` looks an awfully lot like a trait in
cats called `Monad`, which has the same type signature. We say that
`C` is *monadic* when there is an implicit `Monad[C]` available. And
our `Now` is more commonly known as `Id`.

The takeaway is: if we write methods that operate on monadic types,
then we can write procedural code that abstracts over its execution
context. Here, we've shown an abstraction over synchronous and
asynchronous execution but it can also be for the purpose of more
rigorous error handling (where `C[_]` is `Either[Error, _]`) or
recording / auditing of the session.

## Pure Functional Programming

A pure FP language does not allow side effects: mutating state or
interacting with the world. But in Scala, we do this all the time. A
call to `println` will perform I/O (which is really hard to assert in
a test) and a call to `asString` on a `Http` instance will speak to a
web server. It's fair to say that typical Scala is **not** FP.

But something beautiful happened when we wrote our implementation of
`echo` --- it has no side-effects. Anything that interacts with the
world or mutates state is in the `Terminal` implementation. We are
free to implement `Terminal` without any interactions with a real
console, exactly what we want to do in our tests.

If we write our business logic using FP, we not only get to abstract
over the execution environment, but we also get to dramatically
improve the repeatability - and performance - of our tests.

Of course we cannot write an application devoid of interaction with
the world. In FP we push the code that deals with side effects to the
edges. That kind of code looks much like what you've been writing to
date, and can use battle tested libraries like NIO, Akka and Play.

This book expands on the style introduced in this chapter. Instead of
inventing any more primitives, we're going to use the traits and
classes defined in the *cats* and *fs2* libraries to implement pure FP
streaming applications. We'll also use the *freestyle* and
*simulacrum* developer tooling to eliminate boilerplate, allowing you
to focus on writing pure business logic.


