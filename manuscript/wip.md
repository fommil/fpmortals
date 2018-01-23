
# Advanced Monads

You have to know things like Advanced Monads in order to be an advanced
functional programmer. However, we are simple developers, yearning for a simpler
time, and our idea of "advanced" is modest. `scala.concurrent.Future` is more
complicated and nuanced than any `Monad` in this chapter.

`Monad` is a powerful typeclass that can interpret an entire program. In this
chapter we will study some of the most important implementations of `Monad` and
explain why `Future` needlessly complicates your programs: we will offer simpler
and faster alternatives.


## Always in motion is the `Future`

In Chapter 1, we used `Future` to build sequential applications with `.flatMap`.
But the truth is that `Future` is a terrible `Monad`.

The biggest problem with `Future` is that it eagerly schedules work on
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

We can reasonably expect that calling `echo` will not perform any side effects
because it is pure. However, if we use `Future` as the `F[_]` it will start
running immediately, listening to `stdin`:

{lang="text"}
~~~~~~~~
  import ExecutionContext.Implicits._
  val futureEcho: Future[String] = echo[Future]
~~~~~~~~

In addition, we cannot reuse `futureEcho`, it is a one-shot side-effecting
method that breaks referential transparency.

`Future` conflates the definition of a program with running it, i.e. its
interpretation. As a result, applications built with `Future` are difficult to
reason about. If we wish to use `Future` in FP code, we must avoid performing
any side effects, such as I/O or mutating state inside it.

`Future` is also bad from a performance perspective: every time `.flatMap` is
called, a closure is submitted to an `Executor`, resulting in a lot of
unnecessary thread scheduling and context switching.

In addition, `Future.flatMap` requires an `ExecutionContext` to be in implicit
scope, meaning that users of the API are forced to think about business logic
and execution semantics at the same time. We'd much rather consider execution
strategies in one place.

A> If `Future` was a Star Wars character, it would be Jar Jar Binks.


## `IO`

If we can't call side-effecting methods in our business logic, or in `Future`
(or `Id`, or `Either`, or `Const`, etc) interpreters for our algebras, **when
can** we write them? The answer is: in a `Monad` that delays execution until it
is interpreted once for the entire application.

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

If we create an interpreter for our `Terminal` algebra using `IO`

{lang="text"}
~~~~~~~~
  implicit val TerminalIO: Terminal[IO] = new Terminal[IO] {
    def read: IO[String]           = IO { io.StdIn.readLine }
    def write(t: String): IO[Unit] = IO { println(t) }
  }
  
  val program: IO[String] = echo[IO]
~~~~~~~~

we can assign `program` to a `val` and reuse it as the definition of the `echo`
program. It is only when we call `.interpret()` that the side effects run. The
`.interpret` method is only called once, in the entrypoint of the application

{lang="text"}
~~~~~~~~
  def main(args: Array[String]): Unit = program.interpret()
~~~~~~~~

We can also provide a `MonadError`, allowing programs that can fail

{lang="text"}
~~~~~~~~
  object IO {
    ...
    def fail[A](t: Throwable): IO[A] = IO(throw t)
  
    implicit val Monad = new MonadError[IO, Throwable] {
      ...
      def raiseError[A](e: Throwable): IO[A] = fail(e)
      def handleError[A](fa: IO[A])(f: Throwable => IO[A]): IO[A] =
        try IO(fa.interpret())
        catch { case t: Throwable => f(t) }
    }
  }
~~~~~~~~

However, there are two big problems with this simple `IO`:

1.  it doesn't implement `BindRec` and can stack overflow
2.  it doesn't support parallel computations

Both of these problems will be overcome in this chapter when we explain how
`scalaz.effect.IO` fixes the stack overflow problem, and how
`scalaz.effect.Task` can be used for parallelisable calculations.

A> The scala compiler will happily allow us to call side effecting methods from
A> unsafe locations, like in a `Future`. The [scalafix](https://scalacenter.github.io/scalafix/) linting tool can ban side
A> effecting methods at compiletime, unless called from inside a deferred `Monad`
A> like `IO`. This `DisableUnless` rule is an essential tool for quality FP in
A> Scala, and can also be used to enforce your team's preferences without requiring
A> manual code review.


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


