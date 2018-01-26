
# Advanced Monads

You have to know things like Advanced Monads in order to be an advanced
functional programmer. However, we are developers yearning for a simple life,
and our idea of "advanced" is modest. To put it into context:
`scala.concurrent.Future` is more complicated and nuanced than any `Monad` in
this chapter.

In this chapter we will study some of the most important implementations of
`Monad` and explain why `Future` needlessly complicates an application: we will
offer simpler and faster alternatives.


## Always in motion is the `Future`

In Chapter 1, we used `Future` to build sequential applications with `.flatMap`.
But the truth is that `Future` is a terrible `Monad`.

The biggest problem with `Future` is that it eagerly schedules work during
construction. Let's see why that's a problem. Rewriting the Chapter 1 example to
use `Monad`:

{lang="text"}
~~~~~~~~
  def echo[F[_]: Monad](implicit T: Terminal[F]): F[String] =
    for {
      in <- T.read
      _  <- T.write(in)
    } yield in
~~~~~~~~

We can reasonably expect that calling `echo` will not perform any side effects,
because it is pure. However, if we use `Future` as `F[_]` it will start running
immediately, listening to `stdin`:

{lang="text"}
~~~~~~~~
  import ExecutionContext.Implicits._
  val futureEcho: Future[String] = echo[Future]
~~~~~~~~

We have broken purity and are no longer writing FP code: `futureEcho` is not a
definition of the `echo` program that we can rerun or substitute using
referential transparency, but is instead the result of running `echo` once.

`Future` conflates the definition of a program with *interpreting* it (i.e.
running it). As a result, applications built with `Future` are difficult to
reason about. If we wish to use `Future` in FP code, we must avoid performing
any side effects, such as I/O or mutating state.

`Future` is also bad from a performance perspective: every time `.flatMap` is
called, a closure is submitted to an `Executor`, resulting in unnecessary thread
scheduling and context switching. It is not unusual to see 50% of our CPU power
dealing with thread scheduling, instead of doing the work. So much so that
parallelising work with `Future` can often make it *slower*.

Furthermore, `Future.flatMap` requires an `ExecutionContext` to be in implicit
scope: users are forced to think about business logic and execution semantics at
the same time.

A> If `Future` was a Star Wars character, it would be Anakin Skywalker: the fallen
A> chosen one, rushing in and breaking things without thinking.


## Effects and Side Effects

If we can't call side-effecting methods in our business logic, or in `Future`
(or `Id`, or `Either`, or `Const`, etc), **when can** we write them? The answer
is: in a `Monad` that delays execution until it is interpreted at the
application's entrypoint. We can now refer to I/O and mutation as an *effect* on
the world, as opposed to *side-effects*, because they are intentionally
described by the type system.

The simplest implementation of such a `Monad` is `IO`

{lang="text"}
~~~~~~~~
  final class IO[A](val interpret: () => A)
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(() => a)
  
    implicit val Monad: Monad[IO] = new Monad[IO] {
      def point[A](a: =>A): IO[A] = IO(a)
      def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
        IO(f(fa.interpret()).interpret())
    }
  }
~~~~~~~~

Note that `IO` only holds a reference to an impure `() => A`. The caller must
invoke `.interpret()` before the effect will execute, including nested calls to
`.bind` / `.flatMap`.

If we create an interpreter for our `Terminal` algebra using `IO`

{lang="text"}
~~~~~~~~
  implicit val TerminalIO: Terminal[IO] = new Terminal[IO] {
    def read: IO[String]           = IO { io.StdIn.readLine }
    def write(t: String): IO[Unit] = IO { println(t) }
  }
  
  val program: IO[String] = echo[IO]
~~~~~~~~

we can assign `program` to a `val` and reuse it as much as we like to re-run the
effects that it describes. The `.interpret` method is only called once, in the
entrypoint of the application:

{lang="text"}
~~~~~~~~
  def main(args: Array[String]): Unit = program.interpret()
~~~~~~~~

However, there are two big problems with this simple `IO`:

1.  it can stack overflow
2.  it doesn't support parallel computations

Both of these problems will be overcome in this chapter. However, no matter how
complicated the internal implementation of a `Monad`, the principles described
here remain true: we're modularising the definition of a program and its
execution, such that we can capture effects in type signatures, allowing us to
reason about them, and reuse more code.

A> The scala compiler will happily allow us to call side-effecting methods from
A> unsafe code blocks. The [scalafix](https://scalacenter.github.io/scalafix/) linting tool can ban side-effecting methods at
A> compiletime, unless called from inside a deferred `Monad` like `IO`. The
A> `DisableUnless` rule is essential for writing safe FP programs in Scala.


## Stack Safety with the `Free` Monad

On the JVM, every method call adds an entry to the call stack of the `Thread`,
like adding to the front of a `List`. When the method completes, the method at
the `head` is thrown away. The maximum length of the call stack is determined by
the `-Xss` flag when starting up `java`. Tail recursive methods are detected by
the scala compiler and do not add an entry. If we hit the limit, by calling too
many chained methods, we get a `StackOverflowException`.

Unfortunately, every call to our `IO`'s `.flatMap` results in another method
call to the stack. The easiest way to see this is to repeat an action forever,
and see if it survives for longer than a few seconds. We can use `.forever`,
from `Apply` (a parent of `Monad`):

{lang="text"}
~~~~~~~~
  scala> val hello = IO { println("hello") }
  scala> Apply[IO].forever(hello).interpret()
  
  hello
  ...
  hello
  java.lang.StackOverflowError
      at java.io.FileOutputStream.write(FileOutputStream.java:326)
      at ...
      at monadio.IO$$anon$1.$anonfun$bind$1(monadio.scala:18)
      at monadio.IO$$anon$1.$anonfun$bind$1(monadio.scala:18)
      at ...
~~~~~~~~

Scalaz has a typeclass that `Monad` instances can implement if they provide a
stack safe implementation: `BindRec` uses constant stack space for recursive
`bind`:

{lang="text"}
~~~~~~~~
  trait BindRec[F[_]] extends Bind[F] {
    def tailrecM[A, B](f: A => F[A \/ B])(a: A): F[B]
  
    override def forever[A, B](fa: F[A]): F[B] = ...
  }
~~~~~~~~

We don't need `BindRec` for all programs, but it is essential for a general
purpose `Monad` implementation. The solution to stack safety is to convert stack
calls into references to objects that live on the heap, i.e. by encoding the
method calls with an ADT, called the `Free` monad:

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A]
  object Free {
    private case class Return[S[_], A](a: A)     extends Free[S, A]
    private case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
    private case class Gosub[S[_], A0, B](
      a: Free[S, A0],
      f: A0 => Free[S, B]
    ) extends Free[S, B] { type A = A0 }
    ...
  }
~~~~~~~~

A> `SUSPEND`, `RETURN` and `GOSUB` are a tip of the hat to the `BASIC` commands of
A> the same name: pausing, completing, and continuing a subroutine, respectively.

However, `Free` is more general than we need for now. Setting the first type
parameter to `() => ?` we get `Trampoline` and can implement a stack safe
`Monad`

{lang="text"}
~~~~~~~~
  object Free {
    type Trampoline[A] = Free[() => ?, A]
    implicit val trampoline: Monad[Trampoline] with BindRec[Trampoline] =
      new Monad[Trampoline] with BindRec[Trampoline] {
        def point[A](a: =>A): Trampoline[A] = Return(a)
        def bind[A, B](fa: Trampoline[A])(f: A => Trampoline[B]): Trampoline[B] =
          Gosub(fa, f)
        def tailrecM[A, B](f: A => Trampoline[A \/ B])(a: A): Trampoline[B] =
          bind(f(a)) {
            case -\/(a) => tailrecM(f)(a)
            case \/-(b) => point(b)
          }
      }
    ...
  }
~~~~~~~~

The implementation makes it clear that the `Free` ADT is a natural data type
representation of the `Monad` interface:

1.  `Return` represents `.point`
2.  `Gosub` represents `.bind` / `.flatMap`

The `BindRec` implementation, `.tailrecM`, runs `.bind` until we get a `B`.
Although this is not technically a `@tailrec` implementation, it uses constant
stack space because each call returns a heap object, with delayed recursion.

A> Called `Trampoline` because every time we `.bind` on the stack, we *bounce* back
A> to the heap.
A> 
A> The only Star Wars reference involving bouncing is Yoda's duel with Dooku.
A> 
A> So, let's stick with `Trampoline` then...

Convenient functions are provided to create a `Trampoline` eagerly (`.done`) or
by-name (`.delay`). We can also create a `Trampoline` from a by-name
`Trampoline` (`.suspend`):

{lang="text"}
~~~~~~~~
  object Trampoline {
    def done[A](a: A): Trampoline[A]                  = Return(a)
    def delay[A](a: =>A): Trampoline[A]               = suspend(done(a))
    def suspend[A](a: =>Trampoline[A]): Trampoline[A] = unit >> a
  
    private val unit: Trampoline[Unit] = Suspend(() => done(()))
  }
~~~~~~~~

-   TODO interpreter
-   TODO example
-   TODO `Free` has two type parameters. The `S[_]` can be an algebra and in fact

`Free` can be used to implement `Terminal` directly.


# The Infinite Sadness

You've reached the end of this Early Access book. Please check the
website regularly for updates.

You can expect to see chapters covering the following topics:

-   Advanced Monads (more to come)
-   Typeclass Derivation
-   Optics
-   Type Refinement
-   Recursion Schemes
-   Dependent Types
-   Functional Streams
-   Category Theory
-   Bluffing Haskell

while continuing to build out the example application.


