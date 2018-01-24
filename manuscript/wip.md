
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
immediately, listening to `stdin`. We've broken purity and we are no longer
writing FP code:

{lang="text"}
~~~~~~~~
  import ExecutionContext.Implicits._
  val futureEcho: Future[String] = echo[Future]
~~~~~~~~

The `val futureEcho` is no longer a definition of the `echo` program that we can
rerun or substitute using referential transparency, but is instead a cache of
the result of running the side effecting `echo` program at the moment the
runtime choose to initialise `futureEcho`.

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

A> If `Future` was a Star Wars character, it would be Anakin Skywalker: the chosen
A> one, rushing in and breaking things without thinking.


## Effects and Side Effects

If we can't call side-effecting methods in our business logic, or in `Future`
(or `Id`, or `Either`, or `Const`, etc), **when can** we write them? The answer
is: in a `Monad` that delays execution until it is interpreted at the
application's entrypoint. We can now refer to them as *effects*, as opposed to
*side-effects*, because they are intentional and described by the type system.

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

Note that `IO` only holds a reference to a `() => A`, which may perform the
side-effect. The caller must invoke `.interpret()` before anything will execute,
including nested calls to `.bind` / `.flatMap`.

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
entrypoint of the application, invoking the side effects:

{lang="text"}
~~~~~~~~
  def main(args: Array[String]): Unit = program.interpret()
~~~~~~~~

However, there are two big problems with this simple `IO`:

1.  it can stack overflow
2.  it doesn't support parallel computations

Both of these problems will be overcome in this chapter. However, no matter how
complicated the internal implementation of a `Monad`, the principles described
here remain true: we're simply splitting up the definition of a program from its
execution, such that we can capture effects in type signatures, allowing us to
reason about them, and reuse more code.

A> The scala compiler will happily allow us to call side effecting methods from
A> unsafe code blocks. The [scalafix](https://scalacenter.github.io/scalafix/) linting tool can ban side effecting methods at
A> compiletime, unless called from inside a deferred `Monad` like `IO`. This
A> `DisableUnless` rule is an essential tool for writing FP in Scala.


## TODO Trampolines and the `Free` Monad

On the JVM, every runtime method call adds an entry to the call stack. When the
method completes, it returns to the previous entry. The maximum depth of the
call stack is determined by the `-Xss` flag when starting up `java`. Tail
recursive methods are detected by the scala compiler and do not add an entry to
the stack. If we hit the limit, by calling too many chained methods, we get
a `StackOverflowException`.

FIXME: Work in Progress


### BindRec

`BindRec` is a `Bind` that must use constant stack space when doing
recursive `bind`. i.e. it's stack safe and can loop `forever` without
blowing up the stack:

{lang="text"}
~~~~~~~~
  trait BindRec[F[_]] extends Bind[F] {
    def tailrecM[A, B](f: A => F[A \/ B])(a: A): F[B]
  
    override def forever[A, B](fa: F[A]): F[B] = ...
  }
~~~~~~~~

Arguably `forever` should only be introduced by `BindRec`, not `Apply`
or `Bind`.

This is what we need to be able to implement the "loop forever" logic
of our application.

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A] {
  object Free {
    type Trampoline[A] = Free[() => ?, A]
  
    private case class Return[S[_], A](a: A) extends Free[S, A]
    private case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
    private case class Gosub[S[_], A0, B](
      a: Free[S, A0],
      f: A0 => Free[S, B]
    ) extends Free[S, B] { type A = A0 }
    ...
  }
~~~~~~~~


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


