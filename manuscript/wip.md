
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
the world, captured by the type system, as opposed to having a hidden
*side-effect*.

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
A> compiletime, unless called from inside a deferred `Monad` like `IO`.


## Stack Safety with the `Free` Monad

On the JVM, every method call adds an entry to the call stack of the `Thread`,
like adding to the front of a `List`. When the method completes, the method at
the `head` is thrown away. The maximum length of the call stack is determined by
the `-Xss` flag when starting up `java`. Tail recursive methods are detected by
the scala compiler and do not add an entry. If we hit the limit, by calling too
many chained methods, we get a `StackOverflowError`.

Unfortunately, every nested call to our `IO`'s `.flatMap` adds another method
call to the stack. The easiest way to see this is to repeat an action forever,
and see if it survives for longer than a few seconds. We can use `.forever`,
from `Apply` (a parent of `Monad`):

{lang="text"}
~~~~~~~~
  scala> val hello = IO { println("hello") }
  scala> Apply[IO].forever(hello).interpret()
  
  hello
  hello
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

Scalaz has a typeclass that `Monad` instances can implement if they are stack
safe: `BindRec` requires a constant stack space for recursive `bind`:

{lang="text"}
~~~~~~~~
  @typeclass trait BindRec[F[_]] extends Bind[F] {
    def tailrecM[A, B](f: A => F[A \/ B])(a: A): F[B]
  
    override def forever[A, B](fa: F[A]): F[B] = ...
  }
~~~~~~~~

We don't need `BindRec` for all programs, but it is essential for a general
purpose `Monad` implementation.

The way to achieve stack safety is to convert method calls into references to an
ADT, the `Free` monad:

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A]
  object Free {
    private final case class Return[S[_], A](a: A)     extends Free[S, A]
    private final case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
    private final case class Gosub[S[_], A0, B](
      a: Free[S, A0],
      f: A0 => Free[S, B]
    ) extends Free[S, B] { type A = A0 }
    ...
  }
~~~~~~~~

A> `SUSPEND`, `RETURN` and `GOSUB` are a tip of the hat to the `BASIC` commands of
A> the same name: pausing, completing, and continuing a subroutine, respectively.

The `Free` monad is named because it can be *generated for free* for any `S[_]`.
For example, we could set `S` to be the `Drone` or `Machines` algebras from
Chapter 3 and generate a data structure representation of our program. We'll
return to why this is useful at the end of this chapter.


### `Trampoline`

`Free` is more general than we need for now. Setting the algebra `S[_]` to `()
=> ?`, a deferred calculation or *thunk*, we get `Trampoline` and can implement
a stack safe `Monad`

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

The `Free` ADT is a natural data type representation of the `Monad` interface:

1.  `Return` represents `.point`
2.  `Gosub` represents `.bind` / `.flatMap`

The `BindRec` implementation, `.tailrecM`, runs `.bind` until we get a `B`.
Although this is not technically a `@tailrec` implementation, it uses constant
stack space because each call returns a heap object, with delayed recursion.

A> Called `Trampoline` because every time we `.bind` on the stack, we *bounce* back
A> to the heap.
A> 
A> The only Star Wars reference involving bouncing is Yoda's duel with Dooku. We
A> shall not speak of this again.

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

When we see `Trampoline[A]` in a codebase we can always mentally substitute it
with `A`, because it is simply adding stack safety to the pure computation. We
get the `A` by interpreting `Free`, provided by the `.run` method:

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A] {
    def run(implicit ev: Free[S, A] =:= Trampoline[A]): A = ev(this).go(_())
  
    def go(f: S[Free[S, A]] => Free[S, A])(implicit S: Functor[S]): A = {
      @tailrec def go2(t: Free[S, A]): A = t.resume match {
        case -\/(s) => go2(f(s))
        case \/-(r) => r
      }
      go2(this)
    }
  
    @tailrec def resume(implicit S: Functor[S]): (S[Free[S, A]] \/ A) = this match {
      case Return(a) => \/-(a)
      case Suspend(t) => -\/(t.map(Return(_)))
      case Gosub(Return(a), f) => f(a).resume
      case Gosub(Suspend(t), f) => -\/(t.map(f))
      case Gosub(Gosub(a, g), f) => a >>= (z => g(z) >>= f).resume
    }
    ...
  }
~~~~~~~~

Take a moment to read through the implementation of `resume` to understand how
this evaluates a single layer of the `Free`, and that `go` is running it to
completion. The case that is most likely to cause confusion is when we have
nested `Gosub`: apply the inner function `g` then pass it to the outer one `f`,
it's just function composition.


### Example: Stack Safe `DList`

In the previous chapter we described the data type `DList` as

{lang="text"}
~~~~~~~~
  final case class DList[A](f: IList[A] => IList[A]) {
    def toIList: IList[A] = f(IList.empty)
    def ++(as: DList[A]): DList[A] = DList(xs => f(as.f(xs)))
    ...
  }
~~~~~~~~

However, the actual implementation looks more like:

{lang="text"}
~~~~~~~~
  final case class DList[A](f: IList[A] => Trampoline[IList[A]]) {
    def toIList: IList[A] = f(IList.empty).run
    def ++(as: =>DList[A]): DList[A] = DList(xs => suspend(as.f(xs) >>= f))
    ...
  }
~~~~~~~~

Instead of applying nested calls to `f` we use a suspended `Trampoline`. We
interpret the trampoline with `.run` only when needed, e.g. in `toIList`. The
changes are minimal, but we now have a stack safe `DList` that can rearrange the
concatenation of a large number lists without blowing the stack!


### Stack Safe `IO`

Similarly, our `IO` can be made stack safe thanks to `Trampoline`:

{lang="text"}
~~~~~~~~
  final class IO[A](val tramp: Trampoline[A]) {
    def unsafePerformIO(): A = tramp.run
  }
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(Trampoline.delay(a))
  
    implicit val Monad: Monad[IO] with BindRec[IO] =
      new Monad[IO] with BindRec[IO] {
        def point[A](a: =>A): IO[A] = IO(a)
        def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
          new IO(fa.tramp >>= (a => f(a).tramp))
        def tailrecM[A, B](f: A => IO[A \/ B])(a: A): IO[B] = ...
      }
  }
~~~~~~~~

A> We heard you like `Monad`, so we made you a `Monad` out of a `Monad`, so you can
A> monadically bind when you are monadically binding.

The interpreter, `.unsafePerformIO()`, has an intentionally scary name to
discourage using it except in the entrypoint of the application.

This time, we don't get a stack overflow error:

{lang="text"}
~~~~~~~~
  scala> val hello = IO { println("hello") }
  scala> Apply[IO].forever(hello).unsafePerformIO()
  
  hello
  hello
  hello
  ...
  hello
~~~~~~~~

Using a `Trampoline` typically introduces a performance regression vs a regular
reference. It is `Free` in the sense of *freely generated*, not *free as in
beer*.

A> Always benchmark instead of accepting sweeping statements about performance: it
A> may well be the case that the garbage collector performs better for your
A> application when using `Free` because of the reduced size of retained objects in
A> the stack.


## Effects and `Monad` Transformers

In this section, we introduce the most important effects, what is their
fundamental purpose --- or *effect* --- and how they work. This subset of scalaz
is often referred to as the *Monad Transformer Library* (MTL).

Scalaz typically has a typeclass with the ability to use an effect. A monad
transformer provides the simplest implementation.

| Effect          | Underlying                  | Transformer           | Typeclass              |
|--------------- |--------------------------- |--------------------- |---------------------- |
| none            | `F[A]`                      | `IdentityT[F[_], A]`  |                        |
| read config     | `R => F[A]`                 | `ReaderT[F[_], S, A]` | `MonadReader[F[_], R]` |
| logging         | `F[(W, A)]`                 | `WriterT[F[_], W, A]` | `MonadTell[F[_], S]`   |
| evolving state  | `S => F[(S, A)]`            | `StateT[F[_], S, A]`  | `MonadState[F[_], S]`  |
| errors          | `F[Either[E,A]]`            | `EitherT[F[_], E, A]` | `MonadError[F[_], E]`  |
| optionality     | `F[Maybe[A]]`               | `MaybeT[F[_], A]`     | `MonadPlus[F[_]]`      |
| non-determinism | `F[Step[A, StreamT[F, A]]]` | `StreamT[F[_], A]`    |                        |
| continuations   | `(A => F[R]) => F[R]`       | `ContT[F[_], R, A]`   |                        |


### TODO `MonadTrans`


### TODO `IdentityT`


### TODO `ReaderT`


### TODO `WriterT`


### TODO `StateT`


### TODO `EitherT`


### TODO `MaybeT`


### TODO `StreamT`


### TODO `ContT`

Specialisations of ContT like Condensity also have their own nice use cases e.g.
reassociating binds to make them linear rather than quadratic or abstract over
bracketed functions like withFile (ResourceT, Managed)


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


