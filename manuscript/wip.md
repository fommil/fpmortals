
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

When an ADT mirrors the arguments of related functions, it is called a *Church
encoding*, or *Continuation Passing Style*.

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
it is just function composition.


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


## Monad Transformer Library

Monad transformers are data structures that wrap an underlying monadic value,
providing a new `Monad` that encapsulates an *effect*, augmenting the control
flow of the program.

For example, in Chapter 2 we used `OptionT` to let us use `F[Option[A]]` in a
`for` comprehension as if it was just a `F[A]`. This gave our program the effect
of an *optional* value:

{lang="text"}
~~~~~~~~
  final case class OptionT[F[_], A](run: F[Option[A]])
  object OptionT {
    implicit def monad[F[_]: Monad]: MonadPlus[OptionT[F, ?]] = ...
    ...
  }
~~~~~~~~

Scalaz has specialisations of `Monad` that generalise some of the transformer
effects. For example, the typeclass corresponding to optionality is `MonadPlus`,
we can call `MonadPlus[F].empty` to short circuit the control flow of an
application, equivalent to `OptionT(None.pure[F])`.

This subset of scalaz is often referred to as the *Monad Transformer Library*
(MTL), summarised below. In this section, we will explain each of the
transformers, why they are useful, and how they work.

| Effect               | Underlying                  | Transformer | Typeclass     |
|-------------------- |--------------------------- |----------- |------------- |
| optionality          | `F[Maybe[A]]`               | `MaybeT`    | `MonadPlus`   |
| errors               | `F[E \/ A]`                 | `EitherT`   | `MonadError`  |
| read configuration   | `A => F[B]`                 | `ReaderT`   | `MonadReader` |
| logging              | `F[(W, A)]`                 | `WriterT`   | `MonadTell`   |
| evolving state       | `S => F[(S, A)]`            | `StateT`    | `MonadState`  |
| keep calm & carry on | `F[E \&/ A]`                | `TheseT`    |               |
| non-determinism      | `F[Step[A, StreamT[F, A]]]` | `StreamT`   |               |
| continuations        | `(A => F[R]) => F[R]`       | `ContT`     |               |
| none                 | `F[A]`                      | `IdT`       |               |


### `MonadTrans`

Each transformer has the general shape `T[F[_], A]`, providing at least an
instance of `Monad` and the `MonadTrans` typeclass:

{lang="text"}
~~~~~~~~
  @typeclass trait MonadTrans[T[_[_], _]] {
    def liftM[F[_]: Monad, A](a: F[A]): T[F, A]
  }
~~~~~~~~

A> `T[_[_], _]` is another example of a higher kinded type. It says that `T` takes
A> two type parameters: the first also takes a type parameter, written `_[_]`, and
A> the second does not take any type parameters, written `_`.

`.liftM` lets us create a monad transformer if we have an `F[A]`. For example,
we can create an `OptionT[IO, String]` by calling `.liftM[OptionT]` on an
`IO[String]`.

Generally, there are three ways to create a monad transformer:

-   from the underlying, using the transformer's constructor
-   from a single value `A`, using `.pure` from the `Monad` syntax
-   from an `F[A]`, using `.liftM` from the `MonadTrans` syntax

Due to the way that type inference works in Scala, this often means that a
complex type parameter must be explicitly written. As a workaround, transformers
provide convenient constructors on their companion that are easier to use.


### `MaybeT`

`OptionT`, `MaybeT` and `LazyOptionT` have similar implementations, providing
optionality through `Option`, `Maybe` and `LazyOption`, respectively. We will
focus on `MaybeT` to avoid repetition.

{lang="text"}
~~~~~~~~
  final case class MaybeT[F[_], A](run: F[Maybe[A]])
  object MaybeT {
    def just[F[_]: Applicative, A](v: =>A): MaybeT[F, A] =
      MaybeT(Maybe.just(v).pure[F])
    def empty[F[_]: Applicative, A]: MaybeT[F, A] =
      MaybeT(Maybe.empty.pure[F])
    ...
  }
~~~~~~~~

providing a `MonadPlus`

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad] = new MonadPlus[MaybeT[F, ?]] {
    def point[A](a: =>A): MaybeT[F, A] = MaybeT.just(a)
    def bind[A, B](fa: MaybeT[F, A])(f: A => MaybeT[F, B]): MaybeT[F, B] =
      MaybeT(fa.run >>= (_.cata(f(_).run, Maybe.empty.pure[F])))
  
    def empty[A]: MaybeT[F, A] = MaybeT.empty
  
    def plus[A](a: MaybeT[F, A], b: =>MaybeT[F, A]): MaybeT[F, A] = ...
  }
~~~~~~~~

This monad looks fiddly, but it is just delegating everything to the `Monad[F]`
and then re-wrapping with a `MaybeT`. It is plumbing.

With this monad we can write logic that handles optionality in the `F[_]`
context, rather than carrying around `Option` or `Maybe`.

For example, say we are interfacing with a social media website to count the
number of stars a user has, and we start with a `String` that may or may not
correspond to a user. We have this algebra:

{lang="text"}
~~~~~~~~
  trait Twitter[F[_]] {
    def getUser(name: String): F[Maybe[User]]
    def getStars(user: User): F[Int]
  }
  def T[F[_]](implicit t: Twitter[F]): Twitter[F] = t
~~~~~~~~

We need to call `getUser` followed by `getStars`. If we use `Monad` as our
context, our function is difficult because we have to handle the `Empty` case:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): F[Maybe[Int]] = for {
    maybeUser  <- T.getUser(name)
    maybeStars <- maybeUser.traverse(T.getStars)
  } yield maybeStars
~~~~~~~~

However, if we have a `MonadPlus` as our context, we can suck `Maybe` into the
`F[_]` with `.orEmpty`, and forget about it:

{lang="text"}
~~~~~~~~
  def stars[F[_]: MonadPlus: Twitter](name: String): F[Int] = for {
    user  <- T.getUser(name) >>= (_.orEmpty[F])
    stars <- T.getStars(user)
  } yield stars
~~~~~~~~

However adding a `MonadPlus` requirement can cause problems downstream if the
context does not have one. The solution is to either change the context of the
program to `MaybeT[F, ?]` (lifting the `Monad[F]` into a `MonadPlus`), or to
explicitly use `MaybeT` in the return type, at the cost of slightly more code:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): MaybeT[F, Int] = for {
    user  <- MaybeT(T.getUser(name))
    stars <- T.getStars(user).liftM[MaybeT]
  } yield stars
~~~~~~~~

The decision to require a more powerful `Monad` vs returning a transformer is
something that each team can decide for themselves based on the interpreters
that they plan on using for their program.


### `EitherT`

An optional value is a special case of a value that may be an error, but we
don't know anything about the error. `EitherT` (and the lazy variant
`LazyEitherT`) allows us to use any type we want as the error value, providing
contextual information about what went wrong.

Where `\/` and `Validation` is the FP equivalent of a checked exception,
`EitherT` makes it convenient to both create and ignore errors until something
can be done about it.

`EitherT` is a wrapper around an `F[A \/ B]`

{lang="text"}
~~~~~~~~
  final case class EitherT[F[_], A, B](run: F[A \/ B])
  object EitherT {
    def either[F[_]: Applicative, A, B](d: A \/ B): EitherT[F, A, B] = ...
    def leftT[F[_]: Functor, A, B](fa: F[A]): EitherT[F, A, B] = ...
    def rightT[F[_]: Functor, A, B](fb: F[B]): EitherT[F, A, B] = ...
    def pureLeft[F[_]: Applicative, A, B](a: A): EitherT[F, A, B] = ...
    def pure[F[_]: Applicative, A, B](b: B): EitherT[F, A, B] = ...
    ...
  }
~~~~~~~~

The `Monad` is a `MonadError`

{lang="text"}
~~~~~~~~
  @typeclass trait MonadError[F[_], E] extends Monad[F] {
    def raiseError[A](e: E): F[A]
    def handleError[A](fa: F[A])(f: E => F[A]): F[A]
  }
~~~~~~~~

`.raiseError` and `.handleError` are self-descriptive: the equivalent of `throw`
and `catch` an exception, respectively.

The `MonadError` for `EitherT` is:

{lang="text"}
~~~~~~~~
  implicit def monad[F[_]: Monad, E] = new MonadError[EitherT[F, E, ?], E] {
    def monad[F[_]: Monad, E] = new MonadError[EitherT[F, E, ?], E] {
    def bind[A, B](fa: EitherT[F, E, A])
                  (f: A => EitherT[F, E, B]): EitherT[F, E, B] =
      EitherT(fa.run >>= (_.fold(_.left[B].pure[F], b => f(b).run)))
    def point[A](a: =>A): EitherT[F, E, A] = EitherT.pure(a)
  
    def raiseError[A](e: E): EitherT[F, E, A] = EitherT.pureLeft(e)
    def handleError[A](fa: EitherT[F, E, A])
                      (f: E => EitherT[F, E, A]): EitherT[F, E, A] =
      EitherT(fa.run >>= {
        case -\/(e) => f(e).run
        case right => right.pure[F]
      })
  }
~~~~~~~~

It should be of no surprise that we can rewrite the `MonadPlus` example with
`MonadError`, inserting informative error messages:

{lang="text"}
~~~~~~~~
  def stars[F[_]: Twitter](name: String)
                          (implicit F: MonadError[F, String]): F[Int] = for {
    user  <- T.getUser(name) >>= (_.orError[F, String](s"user '$name' not found"))
    stars <- T.getStars(user)
  } yield stars
~~~~~~~~

where we have provided custom syntax

{lang="text"}
~~~~~~~~
  implicit class HelperOps[A](m: Maybe[A]) {
    def orError[F[_], E](e: E)(implicit F: MonadError[F, E]): F[A] =
      m.cata(F.point(_), F.raiseError(e))
  }
~~~~~~~~

A> It is common to use `implicit` parameter blocks instead of context bounds when
A> the signature of the typeclass has more than one parameter.
A> 
A> It is also common practice to name the implicit parameter after the primary
A> type, in this case `F`.

The version using `EitherT` directly looks like

{lang="text"}
~~~~~~~~
  def stars[F[_]: Monad: Twitter](name: String): EitherT[F, String, Int] = for {
    user <- EitherT(T.getUser(name).map(_ \/> s"user '$name' not found"))
    stars <- EitherT.rightT(T.getStars(user))
  } yield stars
~~~~~~~~

The simplest instance of `MonadError` is for `\/`, perfect for testing business
logic that requires a `MonadError`. For example,

{lang="text"}
~~~~~~~~
  final class MockTwitter extends Twitter[String \/ ?] {
    def getUser(name: String): String \/ Maybe[User] =
      if (name.contains(" ")) Maybe.empty.right
      else if (name === "wobble") "connection error".left
      else User(name).just.right
  
    def getStars(user: User): String \/ Int =
      if (user.name.startsWith("w")) 10.right
      else "stars have been replaced by hearts".left
  }
~~~~~~~~

Our unit tests for `.stars` might cover these cases:

{lang="text"}
~~~~~~~~
  scala> stars("wibble")
  \/-(10)
  
  scala> stars("wobble")
  -\/(connection error)
  
  scala> stars("i'm a fish")
  -\/(user 'i'm a fish' not found)
  
  scala> stars("fommil")
  -\/(stars have been replaced by hearts)
~~~~~~~~

As we've now seen several times, we can focus on testing the pure business logic
without distraction.


#### Choosing an error type

The community is undecided on the best strategy for the error type `E` in
`MonadError`.

One school of thought says that we should pick something general, like a
`String`. The other school says that an application should have an ADT of
errors, allowing different errors to be reported or handled differently. An
unprincipled gang prefers using `Throwable` for maximum JVM compatibility.

There are two problems with an ADT of errors on the application level:

-   it is very awkward to create a new error. One file becomes a monolithic
    repository of errors, aggregating the ADTs of individual subsystems.
-   no matter how granular the errors are, the resolution is often the same: log
    it and try it again, or give up. We don't need an ADT for this.

Using a simple error type like `String` has the problem that it is difficult to
find where the problem originated: there is no stacktrace. However, using
[`sourcecode` by Li Haoyi](https://github.com/lihaoyi/sourcecode/), we can include contextual information as metadata in
our errors:

{lang="text"}
~~~~~~~~
  final case class Meta(fqn: String, file: String, line: Int)
  object Meta {
    implicit def gen(implicit fqn: sourcecode.FullName,
                              file: sourcecode.File,
                              line: sourcecode.Line): Meta =
      new Meta(fqn.value, file.value, line.value)
  }
  
  final case class Err(msg: String)(implicit val meta: Meta)
~~~~~~~~

With `Err` as our error type we get referentially transparent metadata:

{lang="text"}
~~~~~~~~
  scala> val err = Err("hello world")
  scala> println(err)
  hello world
  scala> println(err.meta)
  Meta(com.acme.main,<console>,10)
~~~~~~~~

Although we no longer have a stacktrace, it is rare that a full stacktrace would
have been relevant anyway. In pure code, all the context that is needed to fully
explain an error message is contained in the inputs: there is no reliance on
hidden state.

A nice compromise between an error ADT and a `String` is an intermediary format.
JSON is a good choice as it can be understood by most logging and monitoring
frameworks.


#### `IO` and `Throwable`

`IO` does not have a `MonadError` but instead implements something similar to
`throw`, `catch` and `finally` for `Throwable` errors:

{lang="text"}
~~~~~~~~
  final class IO[A](val tramp: Trampoline[A]) {
    ...
    // catch
    def except(f: Throwable => IO[A]): IO[A] = ...
    // finally
    def ensuring[B](f: IO[B]): IO[A] = ...
    def onException[B](f: IO[B]): IO[A] = ...
  }
  object IO {
    // throw
    def throwIO[A](e: Throwable): IO[A] = IO(throw e)
    ...
  }
~~~~~~~~

Much as `IO` is used to make the type system aware of interactions with the
operating system, it captures exceptions from legacy, non-total, code. The
developer has access to the full stacktrace for debugging, or may handle the
exception according to the ancient rules of the legacy system's API.


# The Infinite Sadness

You've reached the end of this Early Access book. Please check the
website regularly for updates.

You can expect to see chapters covering the following topics:

-   Advanced Monads (more to come)
-   Typeclass Derivation
-   Type Refinement
-   Property Testing
-   Optics
-   Functional Streams
-   Bluffing Haskell

while continuing to build out the example application.


