
# Advanced Monads

You have to know things like Advanced Monads in order to be an advanced
functional programmer. However, we are simple developers, and our idea of
"advanced" is modest. If you understand how to use `Future`, you already
understand something that is more complex than anything you will need in
functional programming.

`Monad` is a powerful typeclass that is typically used to direct the control
flow of an application. In this chapter we will study some of the most important
implementations of `Monad` and explain why `Future` is a terrible choice that
needlessly complicates your programs: we will offer simpler and faster
alternatives.


## Always in motion is the `Future`

In Chapter 1, we used `Future` to build sequential applications with `.flatMap`.
But the truth is that `Future` is a terrible `Monad`.

A> If `Future` was a Star Wars character, it would be Jar Jar Binks.

The biggest problem with `Future` is that it eagerly schedules work on
construction. In FP we want to separate the description of a program and running
it so that we can control the ordering of the effects and reuse descriptions of
programs for performance reasons.

As a reminder and rewriting Chapter 1 code to use `Monad`:

{lang="text"}
~~~~~~~~
  def echo[F[_]: Monad](implicit T: Terminal[F]): F[String] =
    for {
      in <- T.read
      _  <- T.write(in)
    } yield in
  
  import ExecutionContext.Implicits._
  val futureEcho: Future[String] = echo[Future]
~~~~~~~~

We can reasonably expect that calling `echo` will not perform any side effects
because it is pure. However, if we use `Future` as the `F[_]` it will start
running immediately, listening to `stdin`. In addition, we cannot reuse
`futureEcho`, it only works once and has therefore broken referential
transparency.

`Future` conflates the definition of a program with its interpretation. As a
result, applications built with `Future` are difficult to reason about. If we
wish to use `Future` in FP code, we must avoid performing any side effects, such
as I/O or mutating state.

`Future` is also bad from a performance perspective: every time `.flatMap` is
called, a closure is submitted to the underlying `Executor`, resulting in a lot
of unnecessary thread scheduling and object creation. In addition,
`Future.flatMap` requires an `ExecutionContext` to be in implicit scope. The
`Monad[Future]` implementation takes an `ExecutionContext` during construction,
but it means that `Monad[Future].flatMap` and `Future.flatMap` have subtly
different behaviour, introducing extra cognitive overhead.


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


