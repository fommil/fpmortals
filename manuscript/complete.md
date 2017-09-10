
# For Comprehensions

Scala's `for` comprehension is the ideal FP abstraction for sequential
programs that interact with the world. Since we'll be using it a lot,
we're going to relearn the principles of `for` and how scalaz can help
us to write cleaner code.

This chapter doesn't try to write pure programs and the techniques are
applicable to non-FP codebases.


## Syntax Sugar

Scala's `for` is just a simple rewrite rule, also called *syntax
sugar*, that doesn't have any contextual information.

To see what a `for` comprehension is doing, we use the `show` and
`reify` feature in the REPL to print out what code looks like after
type inference.

{lang="text"}
~~~~~~~~
  scala> import scala.reflect.runtime.universe._
  scala> val a, b, c = Option(1)
  scala> show { reify {
           for { i <- a ; j <- b ; k <- c } yield (i + j + k)
         } }
  
  res:
  $read.a.flatMap(
    ((i) => $read.b.flatMap(
      ((j) => $read.c.map(
        ((k) => i.$plus(j).$plus(k)))))))
~~~~~~~~

There is a lot of noise due to additional sugarings (e.g. `+` is
rewritten `$plus`, etc). We'll skip the `show` and `reify` for brevity
when the REPL line is `reify>`, and manually clean up the generated
code so that it doesn't become a distraction.

{lang="text"}
~~~~~~~~
  reify> for { i <- a ; j <- b ; k <- c } yield (i + j + k)
  
  a.flatMap {
    i => b.flatMap {
      j => c.map {
        k => i + j + k }}}
~~~~~~~~

The rule of thumb is that every `<-` (called a *generator*) is a
nested `flatMap` call, with the final generator a `map` containing the
`yield` body.


### Assignment

We can assign values inline like `ij = i + j` (a `val` keyword is not
needed).

{lang="text"}
~~~~~~~~
  reify> for {
           i <- a
           j <- b
           ij = i + j
           k <- c
         } yield (ij + k)
  
  a.flatMap {
    i => b.map { j => (j, i + j) }.flatMap {
      case (j, ij) => c.map {
        k => ij + k }}}
~~~~~~~~

A `map` over the `b` introduces the `ij` which is flat-mapped along
with the `j`, then the final `map` for the code in the `yield`.

Unfortunately we cannot assign before any generators. It has been
requested as a language feature but has not been implemented:
<https://github.com/scala/bug/issues/907>

{lang="text"}
~~~~~~~~
  scala> for {
           initial = getDefault
           i <- a
         } yield initial + i
  <console>:1: error: '<-' expected but '=' found.
~~~~~~~~

We can workaround the limitation by defining a `val` outside the `for`

{lang="text"}
~~~~~~~~
  scala> val initial = getDefault
  scala> for { i <- a } yield initial + i
~~~~~~~~

or create an `Option` out of the initial assignment

{lang="text"}
~~~~~~~~
  scala> for {
           initial <- Option(getDefault)
           i <- a
         } yield initial + i
~~~~~~~~

A> `val` doesn't have to assign to a single value, it can be anything
A> that works as a `case` in a pattern match.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> val (first, second) = ("hello", "world")
A>   first: String = hello
A>   second: String = world
A>   
A>   scala> val list: List[Int] = ...
A>   scala> val head :: tail = list
A>   head: Int = 1
A>   tail: List[Int] = List(2, 3)
A> ~~~~~~~~
A> 
A> The same is true for assignment in `for` comprehensions
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> val maybe = Option(("hello", "world"))
A>   scala> for {
A>            entry <- maybe
A>            (first, _) = entry
A>          } yield first
A>   res: Some(hello)
A> ~~~~~~~~
A> 
A> But be careful that you don't miss any cases or you'll get a runtime
A> exception (a *totality* failure).
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> val a :: tail = list
A>   caught scala.MatchError: List()
A> ~~~~~~~~


### Filter

It is possible to put `if` statements after a generator to filter
values by a predicate

{lang="text"}
~~~~~~~~
  reify> for {
           i  <- a
           j  <- b
           if i > j
           k  <- c
         } yield (i + j + k)
  
  a.flatMap {
    i => b.withFilter {
      j => i > j }.flatMap {
        j => c.map {
          k => i + j + k }}}
~~~~~~~~

Older versions of scala used `filter`, but `Traversable.filter`
creates new collections for every predicate, so `withFilter` was
introduced as the more performant alternative.

We can accidentally trigger a `withFilter` by providing type
information: it's actually interpreted as a pattern match.

{lang="text"}
~~~~~~~~
  reify> for { i: Int <- a } yield i
  
  a.withFilter {
    case i: Int => true
    case _      => false
  }.map { case i: Int => i }
~~~~~~~~

Like in assignment, a generator can use a pattern match on the left
hand side. But unlike assignment (which throws `MatchError` on
failure), generators are *filtered* and will not fail at runtime.
However, there is an inefficient double application of the pattern.


### For Each

Finally, if there is no `yield`, the compiler will use `foreach`
instead of `flatMap`, which is only useful for side-effects.

{lang="text"}
~~~~~~~~
  reify> for { i <- a ; j <- b } println(s"$i $j")
  
  a.foreach { i => b.foreach { j => println(s"$i $j") } }
~~~~~~~~


### Summary

The full set of methods supported by `for` comprehensions do not share
a common super type; each generated snippet is independently compiled.
If there were a trait, it would roughly look like:

{lang="text"}
~~~~~~~~
  trait ForComprehensible[C[_]] {
    def map[A, B](f: A => B): C[B]
    def flatMap[A, B](f: A => C[B]): C[B]
    def withFilter[A](p: A => Boolean): C[A]
    def foreach[A](f: A => Unit): Unit
  }
~~~~~~~~

If the context (`C[_]`) of a `for` comprehension doesn't provide its
own `map` and `flatMap`, all is not lost. If an implicit
`scalaz.Bind[T]` is available for `T`, it will provide `map` and
`flatMap`.

A> It often surprises developers when inline `Future` calculations in a
A> `for` comprehension do not run in parallel:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   import scala.concurrent._
A>   import ExecutionContext.Implicits.global
A>   
A>   for {
A>     i <- Future { expensiveCalc() }
A>     j <- Future { anotherExpensiveCalc() }
A>   } yield (i + j)
A> ~~~~~~~~
A> 
A> This is because the `flatMap` spawning `anotherExpensiveCalc` is
A> strictly **after** `expensiveCalc`. To ensure that two `Future`
A> calculations begin in parallel, start them outside the `for`
A> comprehension.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   val a = Future { expensiveCalc() }
A>   val b = Future { anotherExpensiveCalc() }
A>   for { i <- a ; j <- b } yield (i + j)
A> ~~~~~~~~
A> 
A> `for` comprehensions are fundamentally for defining sequential
A> programs. We will show a far superior way of defining parallel
A> computations in a later chapter.


## Unhappy path

So far we've only looked at the rewrite rules, not what is happening
in `map` and `flatMap`. Let's consider what happens when the `for`
context decides that it can't proceed any further.

In the `Option` example, the `yield` is only called when `i,j,k` are
all defined.

{lang="text"}
~~~~~~~~
  for {
    i <- a
    j <- b
    k <- c
  } yield (i + j + k)
~~~~~~~~

If any of `a,b,c` are `None`, the comprehension short-circuits with
`None` but it doesn't tell us what went wrong.

A> How often have you seen a function that takes `Option` parameters but
A> requires them all to exist? An alternative to throwing a runtime
A> exception is to use a `for` comprehension, giving us totality (a
A> return value for every input):
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def namedThings(
A>     someName  : Option[String],
A>     someNumber: Option[Int]
A>   ): Option[String] = for {
A>     name   <- someName
A>     number <- someNumber
A>   } yield s"$number ${name}s"
A> ~~~~~~~~
A> 
A> but this is verbose, clunky and bad style. If a function requires
A> every input then it should make its requirement explicit, pushing the
A> responsibility of dealing with optional parameters to its caller ---
A> don't use `for` unless you need to.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def namedThings(name: String, num: Int) = s"$num ${name}s"
A> ~~~~~~~~

If we use `Either`, then a `Left` will cause the `for` comprehension
to short circuit with extra information, much better than `Option` for
error reporting:

{lang="text"}
~~~~~~~~
  scala> val a = Right(1)
  scala> val b = Right(2)
  scala> val c: Either[String, Int] = Left("sorry, no c")
  scala> for { i <- a ; j <- b ; k <- c } yield (i + j + k)
  
  Left(sorry, no c)
~~~~~~~~

And lastly, let's see what happens with a `Future` that fails:

{lang="text"}
~~~~~~~~
  scala> import scala.concurrent._
  scala> import ExecutionContext.Implicits.global
  scala> for {
           i <- Future.failed[Int](new Throwable)
           j <- Future { println("hello") ; 1 }
         } yield (i + j)
  scala> Await.result(f, duration.Duration.Inf)
  caught java.lang.Throwable
~~~~~~~~

The `Future` that prints to the terminal is never called because, like
`Option` and `Either`, the `for` comprehension short circuits.

Short circuiting for the unhappy path is a common and important theme.
`for` comprehensions cannot express resource cleanup: there is no way
to `try` / `finally`. This is good, in FP it puts a clear ownership of
responsibility for unexpected error recovery and resource cleanup onto
the context (which is usually a `Monad` as we'll see later), not the
business logic.


## Gymnastics

Although it's easy to rewrite simple sequential code as a `for`
comprehension, sometimes we'll want to do something that appears to
require mental summersaults. This section collects some practical
examples and how to deal with them.


### Fallback Logic

Let's say we are calling out to a method that returns an `Option` and
if it's not successful we want to fallback to another method (and so
on and so on), like when we're using a cache:

{lang="text"}
~~~~~~~~
  def getFromRedis(s: String): Option[String]
  def getFromSql(s: String): Option[String]
  
  getFromRedis(key) orElse getFromSql(key)
~~~~~~~~

If we have to do this for an asynchronous version of the same API

{lang="text"}
~~~~~~~~
  def getFromRedis(s: String): Future[Option[String]]
  def getFromSql(s: String): Future[Option[String]]
~~~~~~~~

then we have to be careful not to do extra work because

{lang="text"}
~~~~~~~~
  for {
    cache <- getFromRedis(key)
    sql   <- getFromSql(key)
  } yield cache orElse sql
~~~~~~~~

will run both queries. We can pattern match on the first result but
the type is wrong

{lang="text"}
~~~~~~~~
  for {
    cache <- getFromRedis(key)
    res   <- cache match {
               case Some(_) => cache !!! wrong type !!!
               case None    => getFromSql(key)
             }
  } yield res
~~~~~~~~

We need to create a `Future` from the `cache`

{lang="text"}
~~~~~~~~
  for {
    cache <- getFromRedis(key)
    res   <- cache match {
               case Some(_) => Future.successful(cache)
               case None    => getFromSql(key)
             }
  } yield res
~~~~~~~~

`Future.successful` creates a new `Future`, much like an `Option` or
`List` constructor.

If functional programming was like this all the time, it'd be a
nightmare. Thankfully these tricky situations are the corner cases.


### Early Exit

Let's say we have some condition that should exit early.

If we want to exit early as an error we can use the context's
shortcut, e.g. synchronous code that throws an exception

{lang="text"}
~~~~~~~~
  def getA: Int = ...
  
  val a = getA
  require(a > 0, s"$a must be positive")
  a * 10
~~~~~~~~

can be rewritten as async

{lang="text"}
~~~~~~~~
  def getA: Future[Int] = ...
  def error(msg: String): Future[Nothing] =
    Future.failed(new RuntimeException(msg))
  
  for {
    a <- getA
    b <- if (a <= 0) error(s"$a must be positive")
         else Future.successful(a)
  } yield b * 10
~~~~~~~~

But if we want to exit early with a successful return value, we have
to use a nested `for` comprehension, e.g.

{lang="text"}
~~~~~~~~
  def getA: Int = ...
  def getB: Int = ...
  
  val a = getA
  if (a <= 0) 0
  else a * getB
~~~~~~~~

is rewritten asynchronously as

{lang="text"}
~~~~~~~~
  def getA: Future[Int] = ...
  def getB: Future[Int] = ...
  
  for {
    a <- getA
    c <- if (a <= 0) Future.successful(0)
         else for { b <- getB } yield a * b
  } yield c
~~~~~~~~

A> If there is an implicit `Monad[T]` for `T[_]` (i.e. `T` is monadic)
A> then scalaz lets us create a `T[A]` from a value `a:A` by calling
A> `a.pure[T]`.
A> 
A> Scalaz provides `Monad[Future]` and `.pure[Future]` simply calls
A> `Future.successful`. Besides `pure` being slightly shorter to type, it
A> is a general concept that works beyond `Future`, and is therefore
A> recommended.
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   for {
A>     a <- getA
A>     c <- if (a <= 0) 0.pure[Future]
A>          else for { b <- getB } yield a * b
A>   } yield c
A> ~~~~~~~~


## Incomprehensible

The context we're comprehending over must stay the same: we can't mix
contexts.

{lang="text"}
~~~~~~~~
  scala> def option: Option[Int] = ...
  scala> def future: Future[Int] = ...
  scala> for {
           a <- option
           b <- future
         } yield a * b
  <console>:23: error: type mismatch;
   found   : Future[Int]
   required: Option[?]
           b <- future
                ^
~~~~~~~~

Nothing can help us mix arbitrary contexts in a `for` comprehension
because the meaning is not well defined.

But when we have nested contexts the intention is usually obvious yet
the compiler still doesn't accept our code.

{lang="text"}
~~~~~~~~
  scala> def getA: Future[Option[Int]] = ...
  scala> def getB: Future[Option[Int]] = ...
  scala> for {
           a <- getA
           b <- getB
         } yield a * b
  <console>:30: error: value * is not a member of Option[Int]
         } yield a * b
                   ^
~~~~~~~~

Here we want `for` to take care of the outer context and let us write
our code on the inner `Option`. Hiding the outer context is exactly
what a *monad transformer* does, and scalaz provides implementations
for `Option` and `Either` named `OptionT` and `EitherT` respectively.

The outer context can be anything that normally works in a `for`
comprehension, but it needs to stay the same throughout.

We create an `OptionT` from each method call. This changes the context
of the `for` from `Future[Option[_]]` to `OptionT[Future, _]`.

{lang="text"}
~~~~~~~~
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
         } yield a * b
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

`.run` returns us to the original context

{lang="text"}
~~~~~~~~
  scala> result.run
  res: Future[Option[Int]] = Future(<not completed>)
~~~~~~~~

Alternatively, `OptionT[Future, Int]` has `getOrElse` and `getOrElseF`
methods, taking `Int` and `Future[Int]` respectively, returning a
`Future[Int]`.

The monad transformer also allows us to mix `Future[Option[_]]` calls
with methods that just return plain `Future` via `.liftM[OptionT]`
(provided by scalaz when an implicit `Monad` is available):

{lang="text"}
~~~~~~~~
  scala> def getC: Future[Int] = ...
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
           c <- getC.liftM[OptionT]
         } yield a * b / c
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

and we can mix with methods that return plain `Option` by wrapping
them in `Future.successful` (`.pure[Future]`) followed by `OptionT`

{lang="text"}
~~~~~~~~
  scala> def getD: Option[Int] = ...
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
           c <- getC.liftM[OptionT]
           d <- OptionT(getD.pure[Future])
         } yield (a * b) / (c * d)
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

It is messy again, but it's better than writing nested `flatMap` and
`map` by hand. We can clean it up with a DSL that handles all the
required conversions into `OptionT[Future, _]`

{lang="text"}
~~~~~~~~
  def liftFutureOption[A](f: Future[Option[A]]) = OptionT(f)
  def liftFuture[A](f: Future[A]) = f.liftM[OptionT]
  def liftOption[A](o: Option[A]) = OptionT(o.pure[Future])
  def lift[A](a: A)               = liftOption(Some(a))
~~~~~~~~

combined with the *thrush operator* `|>`, which applies the function
on the right to the value on the left, to visually separate the logic
from the transformers

{lang="text"}
~~~~~~~~
  scala> val result = for {
           a <- getA       |> liftFutureOption
           b <- getB       |> liftFutureOption
           c <- getC       |> liftFuture
           d <- getD       |> liftOption
           e <- 10         |> lift
         } yield e * (a * b) / (c * d)
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

This approach also works for `EitherT` (and others) as the inner
context, but their lifting methods are more complex and require
parameters. Scalaz provides monad transformers for a lot of its own
types, so it's worth checking if one is available.

Implementing a monad transformer is an advanced topic. Although
`ListT` exists, it should be avoided because it can unintentionally
reorder `flatMap` calls according to
<https://github.com/scalaz/scalaz/issues/921>. A better alternative is
`StreamT`, which we will visit later.


# Application Design

In this chapter we will write the business logic and tests for a
purely functional server application.


## Specification

Our application will manage a just-in-time build farm on a shoestring
budget. It will listen to a [Drone](https://github.com/drone/drone) Continuous Integration server, and
spawn worker agents using [Google Container Engine](https://cloud.google.com/container-engine/) (GKE) to meet the
demand of the work queue.

{width=60%}
![](images/architecture.png)

Drone receives work when a contributor submits a github pull request
to a managed project. Drone assigns the work to its agents, each
processing one job at a time.

The goal of our app is to ensure that there are enough agents to
complete the work, with a cap on the number of agents, whilst
minimising the total cost. Our app needs to know the number of items
in the *backlog* and the number of available *agents*.

Google can spawn *nodes*, each can host multiple drone agents. When an
agent starts up, it registers itself with drone and drone takes care
of the lifecycle (including keep-alive calls to detect removed
agents).

GKE charges a fee per minute of uptime, rounded up to the nearest hour
for each node. One does not simply spawn a new node for each job in
the work queue, we must re-use nodes and retain them until their 59th
minute to get the most value for money.

Our app needs to be able to start and stop nodes, as well as check
their status (e.g. uptimes, list of inactive nodes) and to know what
time GKE believes it to be.

In addition, there is no API to talk directly to an *agent* so we do
not know if any individual agent is performing any work for the drone
server. If we accidentally stop an agent whilst it is performing work,
it is inconvenient and requires a human to restart the job.

Contributors can manually add agents to the farm, so counting agents
and nodes is not equivalent. We don't need to supply any nodes if
there are agents available.

The failure mode should always be to take the least costly option.

Both Drone and GKE have a JSON over REST API with OAuth 2.0
authentication.


## Interfaces / Algebras

Let's codify the architecture diagram from the previous section.

In FP, an *algebra* takes the place of an `interface` in Java, or the
set of valid messages for an `Actor` in Akka. This is the layer where
we define all side-effecting interactions of our system.

There is tight iteration between writing the business logic and the
algebra: it is a good level of abstraction to design a system.

{lang="text"}
~~~~~~~~
  package algebra
  
  import java.time.ZonedDateTime
  import scalaz.NonEmptyList
  
  trait Drone[F[_]] {
    def getBacklog: F[Int]
    def getAgents: F[Int]
  }
  
  final case class MachineNode(id: String)
  trait Machines[F[_]] {
    def getTime: F[ZonedDateTime]
    def getManaged: F[NonEmptyList[MachineNode]]
    def getAlive: F[Map[MachineNode, ZonedDateTime]] // with start zdt
    def start(node: MachineNode): F[MachineNode]
    def stop(node: MachineNode): F[MachineNode]
  }
~~~~~~~~

We've used `NonEmptyList`, easily created by calling `.toNel` on the
stdlib's `List` (returning an `Option[NonEmptyList]`), otherwise
everything should be familiar.

A> It is good practice in FP to encode constraints in parameters **and**
A> return types --- it means we never need to handle situations that are
A> impossible. However, this often conflicts with the *Effective Java*
A> wisdom of unconstrained parameters and specific return types.
A> 
A> Although we agree that parameters should be as general as possible, we
A> do not agree that a function should take `Seq` unless it can handle
A> empty `Seq`, otherwise the only course of action would be to
A> exception, breaking totality and causing a side effect. We prefer
A> `NonEmptyList`, not because it is a `List`, but because of its
A> non-empty property.


## Business Logic

Now we write the business logic that defines the application's
behaviour, considering only the happy path.

First, the imports

{lang="text"}
~~~~~~~~
  package logic
  
  import java.time.ZonedDateTime
  import java.time.temporal.ChronoUnit
  
  import scala.concurrent.duration._
  
  import scalaz._
  import Scalaz._
  
  import algebra._
~~~~~~~~

We need a `WorldView` class to hold a snapshot of our knowledge of the
world. If we were designing this application in Akka, `WorldView`
would probably be a `var` in a stateful `Actor`.

`WorldView` aggregates the return values of all the methods in the
algebras, and adds a *pending* field to track unfulfilled requests.

{lang="text"}
~~~~~~~~
  final case class WorldView(
    backlog: Int,
    agents: Int,
    managed: NonEmptyList[MachineNode],
    alive: Map[MachineNode, ZonedDateTime],
    pending: Map[MachineNode, ZonedDateTime], // requested at zdt
    time: ZonedDateTime
  )
~~~~~~~~

Now we are ready to write our business logic, but we need to indicate
that we depend on `Drone` and `Machines`.

We create a *module* to contain our main business logic. A module is
pure and depends only on other modules, algebras and pure functions.

{lang="text"}
~~~~~~~~
  final class DynAgents[F[_]](implicit
                              M: Monad[F],
                              d: Drone[F],
                              m: Machines[F]) {
~~~~~~~~

The implicit `Monad[F]` means that `F` is *monadic*, allowing us to
use `map`, `pure` and, of course, `flatMap` via `for` comprehensions.

We have access to the algebra of `Drone` and `Machines` as `d` and
`m`, respectively. Declaring injected dependencies this way should be
familiar if you've ever used Spring's `@Autowired`.

Our business logic will run in an infinite loop (pseudocode)

{lang="text"}
~~~~~~~~
  state = initial()
  while True:
    state = update(state)
    state = act(state)
~~~~~~~~

We must write three functions: `initial`, `update` and `act`, all
returning an `F[WorldView]`.


### initial

In `initial` we call all external services and aggregate their results
into a `WorldView`. We default the `pending` field to an empty `Map`.

{lang="text"}
~~~~~~~~
  def initial: F[WorldView] = for {
    db <- d.getBacklog
    da <- d.getAgents
    mm <- m.getManaged
    ma <- m.getAlive
    mt <- m.getTime
  } yield WorldView(db, da, mm, ma, Map.empty, mt)
~~~~~~~~

Recall from Chapter 1 that `flatMap` (i.e. when we use the `<-`
generator) allows us to operate on a value that is computed at
runtime. When we return an `F[_]` we are returning another program to
be interpreted at runtime, that we can then `flatMap`. This is how we
safely chain together sequential side-effecting code, whilst being
able to provide a pure implementation for tests. FP could be described
as Extreme Mocking.


### update

`update` should call `initial` to refresh our world view, preserving
known `pending` actions.

If a node has changed state, we remove it from `pending` and if a
pending action is taking longer than 10 minutes to do anything, we
assume that it failed and forget that we asked to do it.

{lang="text"}
~~~~~~~~
  def update(old: WorldView): F[WorldView] = for {
    snap <- initial
    changed = symdiff(old.alive.keySet, snap.alive.keySet)
    pending = (old.pending -- changed).filterNot {
      case (_, started) => timediff(started, snap.time) >= 10.minutes
    }
    update = snap.copy(pending = pending)
  } yield update
  
  private def symdiff[T](a: Set[T], b: Set[T]): Set[T] =
    (a union b) -- (a intersect b)
  
  private def timediff(from: ZonedDateTime, to: ZonedDateTime): FiniteDuration =
    ChronoUnit.MINUTES.between(from, to).minutes
~~~~~~~~

Note that we use assignment for pure functions like `symdiff`,
`timediff` and `copy`. Pure functions don't need test mocks, they have
explicit inputs and outputs, so you could move all pure code into
standalone methods on a stateless `object`, testable in isolation.
We're happy testing only the public methods, preferring that our
business logic is easy to read.


### act

The `act` method is slightly more complex, so we'll split it into two
parts for clarity: detection of when an action needs to be taken,
followed by taking action. This simplification means that we can only
perform one action per invocation, but that is reasonable because we
can control the invocations and may choose to re-run `act` until no
further action is taken.

We write the scenario detectors as extractors for `WorldView`, which
is nothing more than an expressive way of writing `if` / `else`
conditions.

We need to add agents to the farm if there is a backlog of work, we
have no agents, we have no nodes alive, and there are no pending
actions. We return a candidate node that we would like to start:

{lang="text"}
~~~~~~~~
  private object NeedsAgent {
    def unapply(world: WorldView): Option[MachineNode] = world match {
      case WorldView(backlog, 0, managed, alive, pending, _)
           if backlog > 0 && alive.isEmpty && pending.isEmpty
             => Option(managed.head)
      case _ => None
    }
  }
~~~~~~~~

If there is no backlog, we should stop all nodes that have become
stale (they are not doing any work). However, since Google charge per
hour we only shut down machines in their 58th+ minute to get the most
out of our money. We return the non-empty list of nodes to stop.

As a financial safety net, all nodes should have a maximum lifetime of
5 hours.

{lang="text"}
~~~~~~~~
  private object Stale {
    def unapply(world: WorldView): Option[NonEmptyList[MachineNode]] =
      world match {
        case WorldView(backlog, _, _, alive, pending, time) if alive.nonEmpty =>
          (alive -- pending.keys).collect {
            case (n, started)
                if backlog == 0 && timediff(started, time).toMinutes % 60 >= 58 =>
              n
            case (n, started) if timediff(started, time) >= 5.hours => n
          }.toList.toNel
  
        case _ => None
      }
  }
~~~~~~~~

Now that we have detected the scenarios that can occur, we can write
the `act` method. When we schedule a node to be started or stopped, we
add it to `pending` noting the time that we scheduled the action.

{lang="text"}
~~~~~~~~
  def act(world: WorldView): F[WorldView] = world match {
    case NeedsAgent(node) =>
      for {
        _ <- m.start(node)
        update = world.copy(pending = Map(node -> world.time))
      } yield update
  
    case Stale(nodes) =>
      nodes.foldLeftM(world) { (world, n) =>
        for {
          _ <- m.stop(n)
          update = world.copy(pending = world.pending + (n -> world.time))
        } yield update
      }
  
    case _ => world.pure[F]
  }
~~~~~~~~

Because `NeedsAgent` and `Stale` do not cover all possible situations,
we need a catch-all `case _` to do nothing. Recall from Chapter 2 that
`.pure` creates the `for`'s (monadic) context from a value.

`foldLeftM` is like `foldLeft` over `nodes`, but each iteration of the
fold may return a monadic value. In our case, each iteration of the
fold returns `F[WorldView]`.

The `M` is for Monadic and you will find more of these *lifted*
methods that behave as one would expect, taking monadic values in
place of values.


## Unit Tests

The FP approach to writing applications is a designer's dream: you can
delegate writing the implementations of algebras to your team members
while focusing on making your business logic meet the requirements.

Our application is highly dependent on timing and third party
webservices. If this was a traditional OOP application, we'd create
mocks for all the method calls, or test actors for the outgoing
mailboxes. FP mocking is equivalent to providing an alternative
implementation of dependency algebras. The algebras already isolate
the parts of the system that need to be mocked --- everything else is
pure.

We'll start with some test data

{lang="text"}
~~~~~~~~
  object Data {
    val node1   = MachineNode("1243d1af-828f-4ba3-9fc0-a19d86852b5a")
    val node2   = MachineNode("550c4943-229e-47b0-b6be-3d686c5f013f")
    val managed = NonEmptyList(node1, node2)
  
    import ZonedDateTime.parse
    val time1 = parse("2017-03-03T18:07:00.000+01:00[Europe/London]")
    val time2 = parse("2017-03-03T18:59:00.000+01:00[Europe/London]") // +52 mins
    val time3 = parse("2017-03-03T19:06:00.000+01:00[Europe/London]") // +59 mins
    val time4 = parse("2017-03-03T23:07:00.000+01:00[Europe/London]") // +5 hours
  
    val needsAgents = WorldView(5, 0, managed, Map.empty, Map.empty, time1)
  }
  import Data._
~~~~~~~~

We implement algebras by creating *handlers* that extend `Drone` and
`Machines` with a specific monadic context, `Id` being the simplest.

Our "mock" implementations simply play back a fixed `WorldView`. We've
isolated the state of our system, so we can use `var` to store the
state (but this is not threadsafe).

{lang="text"}
~~~~~~~~
  class StaticHandlers(state: WorldView) {
    var started, stopped: Int = 0
  
    implicit val drone: Drone[Id] = new Drone[Id] {
      def getBacklog: Int = state.backlog
      def getAgents: Int = state.agents
    }
  
    implicit val machines: Machines[Id] = new Machines[Id] {
      def getAlive: Map[MachineNode, ZonedDateTime] = state.alive
      def getManaged: NonEmptyList[MachineNode] = state.managed
      def getTime: ZonedDateTime = state.time
      def start(node: MachineNode): MachineNode = { started += 1 ; node }
      def stop(node: MachineNode): MachineNode = { stopped += 1 ; node }
    }
  
    val program = DynAgents[Id]
  }
~~~~~~~~

When we write a unit test (here using `FlatSpec` from scalatest), we
create an instance of `StaticHandlers` and then import all of its
members.

Our implicit `drone` and `machines` both use the `Id` execution
context and therefore interpreting this program with them returns an
`Id[WorldView]` that we can assert on.

In this trivial case we just check that the `initial` method returns
the same value that we use in the static handlers:

{lang="text"}
~~~~~~~~
  "Business Logic" should "generate an initial world view" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._
  
    program.initial shouldBe needsAgents
  }
~~~~~~~~

We can create more advanced tests of the `update` and `act` methods,
helping us flush out bugs and refine the requirements:

{lang="text"}
~~~~~~~~
  it should "remove changed nodes from pending" in {
    val world = WorldView(0, 0, managed, Map(node1 -> time3), Map.empty, time3)
    val handlers = new StaticHandlers(world)
    import handlers._
  
    val old = world.copy(alive = Map.empty,
                         pending = Map(node1 -> time2),
                         time = time2)
    program.update(old) shouldBe world
  }
  
  it should "request agents when needed" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._
  
    val expected = needsAgents.copy(
      pending = Map(node1 -> time1)
    )
  
    program.act(needsAgents) shouldBe expected
  
    handlers.stopped shouldBe 0
    handlers.started shouldBe 1
  }
~~~~~~~~

It would be boring to go through the full test suite. Convince
yourself with a thought experiment that the following tests are easy
to implement using the same approach:

-   not request agents when pending
-   don't shut down agents if nodes are too young
-   shut down agents when there is no backlog and nodes will shortly incur new costs
-   not shut down agents if there are pending actions
-   shut down agents when there is no backlog if they are too old
-   shut down agents, even if they are potentially doing work, if they are too old
-   ignore unresponsive pending actions during update

All of these tests are synchronous and isolated to the test runner's
thread (which could be running tests in parallel). If we'd designed
our test suite in Akka, our tests would be subject to arbitrary
timeouts and failures would be hidden in logfiles.

The productivity boost of simple tests for business logic cannot be
overstated. Consider that 90% of an application developer's time
interacting with the customer is in refining, updating and fixing
these business rules. Everything else is implementation detail.


## Parallel

The application that we have designed runs each of its algebraic
methods sequentially. But there are some obvious places where work can
be performed in parallel.


### initial

In our definition of `initial` we could ask for all the information we
need at the same time instead of one query at a time.

As opposed to `flatMap` for sequential operations, scalaz uses
`Apply` syntax for parallel operations:

{lang="text"}
~~~~~~~~
  ^^^^(d.getBacklog, d.getAgents, m.getManaged, m.getAlive, m.getTime)
~~~~~~~~

which can also use infix notation:

{lang="text"}
~~~~~~~~
  (d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime)
~~~~~~~~

If each of the parallel operations returns a value in the same monadic
context, we can apply a function to the results when they all return.
Rewriting `update` to take advantage of this:

{lang="text"}
~~~~~~~~
  def initial: F[WorldView] =
    ^^^^(d.getBacklog, d.getAgents, m.getManaged, m.getAlive, m.getTime) {
      case (db, da, mm, ma, mt) => WorldView(db, da, mm, ma, Map.empty, mt)
    }
~~~~~~~~


### act

In the current logic for `act`, we are stopping each node
sequentially, waiting for the result, and then proceeding. But we
could stop all the nodes in parallel and then update our view of the
world.

A disadvantage of doing it this way is that any failures will cause us
to short-circuit before updating the `pending` field. But that's a
reasonable tradeoff since our `update` will gracefully handle the case
where a `node` is shut down unexpectedly.

We need a method that operates on `NonEmptyList` that allows us to
`map` each element into an `F[MachineNode]`, returning an
`F[NonEmptyList[MachineNode]]`. The method is called `traverse`, and
when we `flatMap` over it we get a `NonEmptyList[MachineNode]` that we
can deal with in a simple way:

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(m.stop)
    updates = stopped.map(_ -> world.time).toList.toMap
    update = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~

Arguably, this is easier to understand than the sequential version.


### Parallel Interpretation

Marking something as suitable for parallel execution does not
guarantee that it will be executed in parallel: that is the
responsibility of the handler. Not to state the obvious: parallel
execution is supported by `Future`, but not `Id`.

Of course, we need to be careful when implementing handlers such that
they can perform operations safely in parallel, perhaps requiring
protecting internal state with concurrency locks or actors.


## Summary

1.  *algebras* define the boundaries between systems, implemented by
    *handlers*.
2.  *modules* define pure logic and depend on algebras and other
    modules.
3.  modules are *interpreted* by handlers
4.  Test handlers can mock out the side-effecting parts of the system
    with trivial implementations, enabling a high level of test
    coverage for the business logic.
5.  algebraic methods can be performed in parallel by taking their
    product or traversing sequences (caveat emptor, revisited later).


# Data and Functionality

From OOP we are used to thinking about data and functionality
together: class hierarchies carry methods, and traits can demand that
data fields exist. Runtime polymorphism of an object is in terms of
"is a" relationships, requiring classes to inherit from common
interfaces. This can get messy as a codebase grows. Simple data types
become obscured by hundreds of lines of methods, trait mixins suffer
from initialisation order errors, and testing / mocking of highly
coupled components becomes a chore.

FP takes a different approach, defining data and functionality
separately. In this chapter, we will cover the basics of data types
and the advantages of constraining ourselves to a subset of the Scala
language. We will also discover *typeclasses* as a way to achieve
compiletime polymorphism: thinking about functionality of a data
structure in terms of "has a" rather than "is a" relationships.


## Data

In FP we make data types explicit, rather than hidden as
implementation detail.

The fundamental building blocks of data types are

-   `final case class` also known as *products*
-   `sealed abstract class` also known as *coproducts*
-   `case object` and `Int`, `Double`, `String` (etc) *values*

with no methods or fields other than the constructor parameters.

The collective name for *products*, *coproducts* and *values* is
*Algebraic Data Type* (ADT).

We compose data types from the `AND` and `XOR` (exclusive `OR`)
Boolean algebra: a product contains every type that it is composed of,
but a coproduct can be only one. For example

-   product: `ABC = a AND b AND c`
-   coproduct: `XYZ = x XOR y XOR z`

written in Scala

{lang="text"}
~~~~~~~~
  // values
  case object A
  type B = String
  type C = Int
  
  // product
  final case class ABC(a: A.type, b: B, c: C)
  
  // coproduct
  sealed abstract class XYZ
  case object X extends XYZ
  case object Y extends XYZ
  final case class Z(b: B) extends XYZ
~~~~~~~~


### Generalised ADTs

When we introduce a type parameter into an ADT, we call it a
*Generalised Algebraic Data Type* (GADT).

`scalaz.IList`, a safe invariant alternative to the stdlib `List`, is
a GADT:

{lang="text"}
~~~~~~~~
  sealed abstract class IList[A]
  case object INil extends IList[Nothing]
  final case class ICons[A](head: A, tail: IList[A]) extends IList[A]
~~~~~~~~

If an ADT refers to itself, we call it a *recursive type*. `IList` is
recursive because `ICons` contains a reference to `IList`.


### Functions on ADTs

ADTs can contain *pure functions*

{lang="text"}
~~~~~~~~
  final case class UserConfiguration(accepts: Int => Boolean)
~~~~~~~~

But ADTs that contain functions come with some caveats as they don't
translate perfectly onto the JVM. For example, legacy `Serializable`,
`hashCode`, `equals` and `toString` do not behave as one might
reasonably expect.

Unfortunately, `Serializable` is used by popular frameworks, despite
far superior alternatives. A common pitfall is forgetting that
`Serializable` may attempt to serialise the entire closure of a
function, which can crash production servers. A similar caveat applies
to legacy Java classes such as `Throwable`, which can carry references
to arbitrary objects. This is one of the reasons why we restrict what
can live on an ADT.

A similar caveat applies to *by name*, known as *lazy* parameters

{lang="text"}
~~~~~~~~
  final case class UserConfiguration(vip: => Boolean)
~~~~~~~~

which are equivalent to functions that take no parameter.

We will explore alternatives to the legacy methods when we discuss the
scalaz library in the next chapter, at the cost of losing
interoperability with some legacy Java and Scala code.


### Exhaustivity

It is important that we use `sealed abstract class`, not just
`abstract class`, when defining a data type. Sealing a `class` means
that all subtypes must be defined in the same file, allowing the
compiler to know about them in pattern match exhaustivity checks and
in macros that eliminate boilerplate. e.g.

{lang="text"}
~~~~~~~~
  scala> sealed abstract class Foo
         final case class Bar(flag: Boolean) extends Foo
         final case object Baz extends Foo
  
  scala> def thing(foo: Foo) = foo match {
           case Bar(_) => true
         }
  <console>:14: error: match may not be exhaustive.
  It would fail on the following input: Baz
         def thing(foo: Foo) = foo match {
                               ^
~~~~~~~~

This shows the developer what they have broken when they add a new
product to the codebase. We're using `-Xfatal-warnings`, otherwise
this is just a warning.

However, the compiler will not perform exhaustivity checking if the
`class` is not sealed or if there are guards, e.g.

{lang="text"}
~~~~~~~~
  scala> def thing(foo: Foo) = foo match {
           case Bar(flag) if flag => true
         }
  
  scala> thing(Baz)
  scala.MatchError: Baz (of class Baz$)
    at .thing(<console>:15)
~~~~~~~~

To remain safe, [don't use guards on `sealed` types](https://github.com/wartremover/wartremover/issues/382).

The [`-Xstrict-patmat-analysis`](https://github.com/scala/scala/pull/5617) flag has been proposed as a language
improvement to perform additional pattern matcher checks.


### Alternative Products and Coproducts

Another form of product is a tuple, which is like an unlabelled `final
case class`.

`(A.type, B, C)` is equivalent to `ABC` in the above example but it is
best to use `final case class` when part of an ADT because the lack of
names is awkward to deal with.

Another form of coproduct is when we nest `Either` types. e.g.

{lang="text"}
~~~~~~~~
  Either[X.type, Either[Y.type, Z]]
~~~~~~~~

equivalent to the `XYZ` sealed abstract class. A cleaner syntax to define
nested `Either` types is to create an alias type ending with a colon,
allowing infix notation with association from the right:

{lang="text"}
~~~~~~~~
  type |:[L,R] = Either[L, R]
  
  X.type |: Y.type |: Z
~~~~~~~~

This is useful to create anonymous coproducts when you can't put all
the implementations into the same source file.

{lang="text"}
~~~~~~~~
  type Accepted = String |: Long |: Boolean
~~~~~~~~

Yet another alternative coproduct is to create a custom `sealed abstract class`
with `final case class` definitions that simply wrap the desired type:

{lang="text"}
~~~~~~~~
  sealed abstract class Accepted
  final case class AcceptedString(value: String) extends Accepted
  final case class AcceptedLong(value: Long) extends Accepted
  final case class AcceptedBoolean(value: Boolean) extends Accepted
~~~~~~~~

Pattern matching on these forms of coproduct can be tedious, which is
why [Union Types](https://contributors.scala-lang.org/t/733) are being explored in the Dotty next-generation scala
compiler. Workarounds such as [totalitarian](https://github.com/propensive/totalitarian)'s `Disjunct` exist as
another way of encoding anonymous coproducts and [stalagmite](https://github.com/fommil/stalagmite/issues/37) aims to
reduce the boilerplate for the approaches presented here.

A> We can also use a `sealed trait` in place of a `sealed abstract class`
A> but there are binary compatibility advantages to using `abstract
A> class`. A `sealed trait` is only needed if you need to create a
A> complicated ADT with multiple inheritance.


### Convey Information

Besides being a container for necessary business information, data
types can be used to encode constraints. For example,

{lang="text"}
~~~~~~~~
  final case class NonEmptyList[A](head: A, tail: IList[A])
~~~~~~~~

can never be empty. This makes `scalaz.NonEmptyList` a useful data
type despite containing the same information as `List`.

In addition, wrapping an ADT can convey information such as if it
contains valid instances. Instead of breaking *totality* by throwing
an exception

{lang="text"}
~~~~~~~~
  final case class Person(name: String, age: Int) {
    require(name.nonEmpty && age > 0) // breaks totality, don't do this
  }
~~~~~~~~

we can use the `Either` data type to provide `Right[Person]` instances
and protect invalid instances from propagating:

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

We will see a better way of reporting validation errors when we
introduce `scalaz.Validation` in the next chapter.


### Simple to Share

By not providing any functionality, ADTs can have a minimal set of
dependencies. This makes them easy to publish and share with other
developers. By using a simple data modelling language, it makes it
possible to interact with cross-discipline teams, such as DBAs, UI
developers and business analysts, using the actual code instead of a
hand written document as the source of truth.

Furthermore, tooling can be more easily written to produce or consume
schemas from other programming languages and wire protocols.


### Counting Complexity

The complexity of a data type is the number of instances that can
exist. A good data type has the least amount of complexity it needs to
hold the information it conveys, and no more.

Values have a built-in complexity:

-   `Unit` has one instance (why it's called "unit")
-   `Boolean` has two instances
-   `Int` has 4,294,967,295 instances
-   `String` has effectively infinite instances

To find the complexity of a product, we multiply the complexity of
each part.

-   `(Boolean, Boolean)` has 4 instances (`2*2`)
-   `(Boolean, Boolean, Boolean)` has 8 instances (`2*2*2`)

To find the complexity of a coproduct, we add the complexity of each
part.

-   `(Boolean |: Boolean)` has 4 instances (`2+2`)
-   `(Boolean |: Boolean |: Boolean)` has 6 instances (`2+2+2`)

To find the complexity of a GADT, multiply each part by the complexity
of the type parameter:

-   `Option[Boolean]` has 3 instances, `Some[Boolean]` and `None` (`2+1`)

In FP, functions are *total* and must return an instance for every
input, no `Exception`. Minimising the complexity of inputs and outputs
is the best way to achieve totality. As a rule of thumb, it is a sign
of a badly designed function when the complexity of a function's
return value is larger than the product of its inputs: it is a source
of entropy.

The complexity of a total function itself is the number of possible
functions that can satisfy the type signature: the output to the power
of the input.

-   `Unit=>Boolean` has complexity 2
-   `Boolean=>Boolean` has complexity 4
-   `Option[Boolean]=>Option[Boolean]` has complexity 27
-   `Boolean=>Int` is a mere quintillion going on a sextillion.
-   `Int=>Boolean` is so big that if all implementations were assigned a
    unique number, each number would be 4GB.

In reality, `Int=>Boolean` will be something simple like `isOdd`,
`isEven` or a sparse `BitSet`. This function, when used in an ADT,
could be better replaced with a coproduct labelling the limited set of
functions that are relevant.

When your complexity is always "infinity in, infinity out" you should
consider introducing more restrictive data types and performing
validation closer to the point of input. A powerful technique to
reduce complexity is *type refinement* which merits a dedicated
chapter later in the book. It allows the compiler to keep track of
more information than is in the bytecode, e.g. if a number is within a
specific bound.


### Prefer Coproduct over Product

An archetypal modelling problem that comes up a lot is when there are
mutually exclusive configuration parameters `a`, `b` and `c`. The
product `(a: Boolean, b: Boolean, c: Boolean)` has complexity 8
whereas the coproduct

{lang="text"}
~~~~~~~~
  sealed abstract class Config
  object Config {
    case object A extends Config
    case object B extends Config
    case object C extends Config
  }
~~~~~~~~

has a complexity of 3. It is better to model these configuration
parameters as a coproduct rather than allowing 5 invalid states to
exist.

The complexity of a data type also has implications on testing. It is
practically impossible to test every possible input to a function, but
it is easy to test a sample of values with the [scalacheck](https://www.scalacheck.org/) property. If
a random sample of a data type has a low probability of being valid,
it's a sign that the data is modelled incorrectly.


### Optimisations

A big advantage of using a simplified subset of the Scala language to
represent data types is that tooling can optimise the JVM bytecode
representation.

For example, [stalagmite](https://github.com/fommil/stalagmite) aims to pack `Boolean` and `Option` fields
into an `Array[Byte]`, cache instances, memoise `hashCode`, optimise
`equals`, enforce validation, use `@switch` statements when pattern
matching, and much more. [iota](https://www.47deg.com/blog/iota-v0-1-0-release/) has performance improvements for nested
`Either` coproducts.

These optimisations are not applicable to OOP `class` hierarchies that
may be managing state, throwing exceptions, or providing adhoc method
implementations.


### Generic Representation

We showed that product is synonymous with tuple and coproduct is
synonymous with nested `Either`. The [shapeless](https://github.com/milessabin/shapeless) library takes this
duality to the extreme and introduces a representation that is
*generic* for all ADTs:

-   `shapeless.HList` (symbolically `::`) for representing products
    (`scala.Product` already exists for another purpose)
-   `shapeless.Coproduct` (symbolically `:+:`) for representing coproducts

Shapeless provides the ability to convert back and forth between a
generic representation and the ADT, allowing functions to be written
that work **for every** `final case class` and `sealed abstract class`.

{lang="text"}
~~~~~~~~
  scala> import shapeless._
         final case class Foo(a: String, b: Long)
         Generic[Foo].to(Foo("hello", 13L))
  res: String :: Long :: HNil = hello :: 13 :: HNil
  
  scala> Generic[Foo].from("hello" :: 13L :: HNil)
  res: Foo = Foo(hello,13)
  
  scala> sealed abstract class Bar
         case object Irish extends Bar
         case object English extends Bar
  
  scala> Generic[Bar].to(Irish)
  res: English.type :+: Irish.type :+: CNil = Inl(Irish)
  
  scala> Generic[Bar].from(Inl(Irish))
  res: Bar = Irish
~~~~~~~~

`HNil` is the empty product and `CNil` is the empty coproduct.

It is not necessary to know how to write generic code to be able to
make use of shapeless. However, it is an important part of FP Scala so
we will return to it later with a dedicated chapter.


## Functionality

Pure functions are typically defined as methods on an `object`.

{lang="text"}
~~~~~~~~
  package object math {
    def sin(x: Double): Double = java.lang.Math.sin(x)
    ...
  }
  
  math.sin(1.0)
~~~~~~~~

However, it can sometimes be clunky to use `object` methods since it
reads inside-out, not left to right: it's the same problem as Java's
static methods vs class methods.

W> If you like to put methods on a `trait`, requiring users to mix your
W> traits into their `classes` or `objects` with the *cake pattern*,
W> please get out of this nasty habit: you're leaking internal
W> implementation detail to public APIs, bloating your bytecode, and
W> creating a lot of noise for IDE autocompleters.

With the `implicit class` language feature (also known as *extension
methodology* or *syntax*), and a little boilerplate, we can get the
familiar style:

{lang="text"}
~~~~~~~~
  scala> implicit class DoubleOps(x: Double) {
           def sin: Double = math.sin(x)
         }
  
  scala> 1.0.sin
  res: Double = 0.8414709848078965
~~~~~~~~

Often it's best to just skip the `object` definition and go straight
for an `implicit class`, keeping boilerplate to a minimum:

{lang="text"}
~~~~~~~~
  implicit class DoubleOps(x: Double) {
    def sin: Double = java.lang.Math.sin(x)
  }
~~~~~~~~

A> `implicit class` is syntax sugar for an implicit conversion:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit def DoubleOps(x: Double): DoubleOps = new DoubleOps(x)
A>   class DoubleOps(x: Double) {
A>     def sin: Double = java.lang.Math.sin(x)
A>   }
A> ~~~~~~~~
A> 
A> Which unfortunately has a runtime cost: each time the extension method
A> is called, an intermediate `DoubleOps` will be constructed and then
A> thrown away. This can contribute to GC pressure in hotspots.
A> 
A> There is a slightly more verbose form of `implicit class` that avoids
A> the allocation and is therefore preferred:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit final class DoubleOps(val x: Double) extends AnyVal {
A>     def sin: Double = java.lang.Math.sin(x)
A>   }
A> ~~~~~~~~


### Polymorphic Functions

The more common kind of function is a polymorphic function, which
lives in a *typeclass*. A typeclass is a trait that:

-   holds no state
-   has a type parameter
-   has at least one abstract method
-   may contain *generalised* methods
-   may extend other typeclasses

Typeclasses are used in the Scala stdlib. We'll explore a simplified
version of `scala.math.Numeric` to demonstrate the principle:

{lang="text"}
~~~~~~~~
  trait Ordering[T] {
    def compare(x: T, y: T): Int
  
    def lt(x: T, y: T): Boolean = compare(x, y) < 0
    def gt(x: T, y: T): Boolean = compare(x, y) > 0
  }
  
  trait Numeric[T] extends Ordering[T] {
    def plus(x: T, y: T): T
    def times(x: T, y: T): T
    def negate(x: T): T
    def zero: T
  
    def abs(x: T): T = if (lt(x, zero)) negate(x) else x
  }
~~~~~~~~

We can see all the key features of a typeclass in action:

-   there is no state
-   `Ordering` and `Numeric` have type parameter `T`
-   `Ordering` has abstract `compare` and `Numeric` has abstract `plus`,
    `times`, `negate` and `zero`
-   `Ordering` defines generalised `lt` and `gt` based on `compare`,
    `Numeric` defines `abs` in terms of `lt`, `negate` and `zero`.
-   `Numeric` extends `Ordering`

We can now write functions for types that "have a" `Numeric`
typeclass:

{lang="text"}
~~~~~~~~
  def signOfTheTimes[T](t: T)(implicit N: Numeric[T]): T = {
    import N._
    times(negate(abs(t)), t)
  }
~~~~~~~~

We are no longer dependent on the OOP hierarchy of our input types,
i.e. we don't demand that our input "is a" `Numeric`, which is vitally
important if we want to support a third party class that we cannot
redefine.

Another advantage of typeclasses is that the association of
functionality to data is at compiletime, as opposed to OOP runtime
dynamic dispatch.

For example, whereas the `List` class can only have one implementation
of a method, a typeclass method allows us to have a different
implementation depending on the `List` contents and therefore offload
work to compiletime instead of leaving it to runtime.


### Syntax

The syntax for writing `signOfTheTimes` is clunky, there are some
things we can do to clean it up.

Downstream users will prefer to see our method use *context bounds*,
since the signature reads cleanly as "takes a `T` that has a
`Numeric`"

{lang="text"}
~~~~~~~~
  def signOfTheTimes[T: Numeric](t: T): T = ...
~~~~~~~~

but now we have to use `implicitly[Numeric[T]]` everywhere. By
defining boilerplate on the companion of the typeclass

{lang="text"}
~~~~~~~~
  object Numeric {
    def apply[T](implicit numeric: Numeric[T]): Numeric[T] = numeric
  }
~~~~~~~~

we can obtain the implicit with less noise

{lang="text"}
~~~~~~~~
  def signOfTheTimes[T: Numeric](t: T): T = {
    val N = Numeric[T]
    import N._
    times(negate(abs(t)), t)
  }
~~~~~~~~

But it is still worse for us as the implementors. We have the
syntactic problem of inside-out static methods vs class methods. We
deal with this by introducing `ops` on the typeclass companion:

{lang="text"}
~~~~~~~~
  object Numeric {
    def apply[T](implicit numeric: Numeric[T]): Numeric[T] = numeric
  
    object ops {
      implicit class NumericOps[T](t: T)(implicit N: Numeric[T]) {
        def +(o: T): T = N.plus(t, o)
        def *(o: T): T = N.times(t, o)
        def unary_-: T = N.negate(t)
        def abs: T = N.abs(t)
  
        // duplicated from Ordering.ops
        def <(o: T): T = N.lt(t, o)
        def >(o: T): T = N.gt(t, o)
      }
    }
  }
~~~~~~~~

Note that `-x` is expanded into `x.unary_-` by the compiler's syntax
sugar, which is why we define `unary_-` as an extension method. We can
now write the much cleaner:

{lang="text"}
~~~~~~~~
  import Numeric.ops._
  def signOfTheTimes[T: Numeric](t: T): T = -(t.abs) * t
~~~~~~~~

The good news is that we never need to write this boilerplate because
[Simulacrum](https://github.com/mpilquist/simulacrum) provides a `@typeclass` macro annotation to have the
companion `apply` and `ops` automatically generated. It even allows us
to define alternative (usually symbolic) names for common methods. In
full:

{lang="text"}
~~~~~~~~
  import simulacrum._
  
  @typeclass trait Ordering[T] {
    def compare(x: T, y: T): Int
    @op("<") def lt(x: T, y: T): Boolean = compare(x, y) < 0
    @op(">") def gt(x: T, y: T): Boolean = compare(x, y) > 0
  }
  
  @typeclass trait Numeric[T] extends Ordering[T] {
    @op("+") def plus(x: T, y: T): T
    @op("*") def times(x: T, y: T): T
    @op("unary_-") def negate(x: T): T
    def zero: T
    def abs(x: T): T = if (lt(x, zero)) negate(x) else x
  }
  
  import Numeric.ops._
  def signOfTheTimes[T: Numeric](t: T): T = -(t.abs) * t
~~~~~~~~


### Instances

*Instances* of `Numeric` (which are also instances of `Ordering`) are
defined as an `implicit val` that extends the typeclass, and can
provide optimised implementations for the generalised methods:

{lang="text"}
~~~~~~~~
  implicit val NumericDouble: Numeric[Double] = new Numeric[Double] {
    def plus(x: Double, y: Double): Double = x + y
    def times(x: Double, y: Double): Double = x * y
    def negate(x: Double): Double = -x
    def zero: Double = 0.0
    def compare(x: Double, y: Double): Int = java.lang.Double.compare(x, y)
  
    // optimised
    override def lt(x: Double, y: Double): Boolean = x < y
    override def gt(x: Double, y: Double): Boolean = x > y
    override def abs(x: Double): Double = java.lang.Math.abs(x)
  }
~~~~~~~~

Although we are using `+`, `*`, `unary_-`, `<` and `>` here, which are
the ops (and could be an infinite loop!), these methods exist already
on `Double`. Class methods are always used in preference to extension
methods. Indeed, the scala compiler performs special handling of
primitives and converts these method calls into raw `dadd`, `dmul`,
`dcmpl` and `dcmpg` bytecode instructions, respectively.

We can also implement `Numeric` for Java's `BigDecimal` class (avoid
`scala.BigDecimal`, [it is fundamentally broken](https://github.com/scala/bug/issues/9670))

{lang="text"}
~~~~~~~~
  import java.math.{ BigDecimal => BD }
  
  implicit val NumericBD: Numeric[BD] = new Numeric[BD] {
    def plus(x: BD, y: BD): BD = x.add(y)
    def times(x: BD, y: BD): BD = x.multiply(y)
    def negate(x: BD): BD = x.negate
    def zero: BD = BD.ZERO
    def compare(x: BD, y: BD): Int = x.compareTo(y)
  }
~~~~~~~~

We could even take some liberties and create our own data structure
for complex numbers:

{lang="text"}
~~~~~~~~
  final case class Complex[T](r: T, i: T)
~~~~~~~~

And derive a `Numeric[Complex[T]]` if `Numeric[T]` exists. Since these
instances depend on the type parameter, it is a `def`, not a `val`.

{lang="text"}
~~~~~~~~
  implicit def numericComplex[T: Numeric]: Numeric[Complex[T]] =
    new Numeric[Complex[T]] {
      type CT = Complex[T]
      def plus(x: CT, y: CT): CT = Complex(x.r + y.r, x.i + y.i)
      def times(x: CT, y: CT): CT =
        Complex(x.r * y.r + (-x.i * y.i), x.r * y.i + x.i * y.r)
      def negate(x: CT): CT = Complex(-x.r, -x.i)
      def zero: CT = Complex(Numeric[T].zero, Numeric[T].zero)
      def compare(x: CT, y: CT): Int = {
        val real = (Numeric[T].compare(x.r, y.r))
        if (real != 0) real
        else Numeric[T].compare(x.i, y.i)
      }
    }
~~~~~~~~

The observant reader may notice that `abs` is not at all what a
mathematician would expect. The correct return value for `abs` should
be `T`, not `Complex[T]`.

`scala.math.Numeric` tries to do too much and does not generalise
beyond real numbers. This is a good lesson that smaller, well defined,
typeclasses are often better than a monolithic collection of overly
specific features.

If you need to write generic code that works for a wide range of
number types, prefer [spire](https://github.com/non/spire) to the stdlib. Indeed, in the next chapter
we will see that concepts such as having a zero element, or adding two
values, are worthy of their own typeclass.


### Implicit Resolution

We've discussed implicits a lot: this section is to clarify what
implicits are and how they work.

*Implicit parameters* are when a method requests that a unique
instance of a particular type is in the *implicit scope* of the
caller, with special syntax for typeclass instances. Implicit
parameters are a clean way to thread configuration through an
application.

In this example, `foo` requires that typeclasses for `Numeric` and
shapeless' `Typeable` are available for `T`, as well as an implicit
(user-defined) `Config` object.

{lang="text"}
~~~~~~~~
  def foo[T: Numeric: Typeable](implicit conf: Config) = ...
~~~~~~~~

*Implicit conversion* is when an `implicit def` exists. One such use
of implicit conversions is to enable extension methodology. When the
compiler is resolving a call to a method, it first checks if the
method exists on the type, then its ancestors (Java-like rules). If it
fails to find a match, it will search the *implicit scope* for
conversions to other types, then search for methods on those types.

Another use for implicit conversion is *typeclass derivation*. In the
previous section we wrote an `implicit def` that derived a
`Numeric[Complex[T]]` if a `Numeric[T]` is in the implicit scope. It
is possible to chain together many `implicit def` (including
recursively) which is the basis of *typeful programming*, allowing for
computations to be performed at compiletime rather than runtime.

The glue that combines implicit parameters (receivers) with implicit
conversion (providers) is implicit resolution.

First, the normal variable scope is searched for implicits, in order:

-   local scope, including scoped imports (e.g. the block or method)
-   outer scope, including scoped imports (e.g. members in the class)
-   ancestors (e.g. members in the super class)
-   the current package object
-   ancestor package objects (only when using nested packages)
-   the file's imports

If that fails to find a match, the special scope is searched, which
looks for implicit instances inside a type's companion, its package
object, outer objects (if nested), and then repeated for ancestors.
This is performed, in order, for the:

-   given parameter type
-   expected parameter type
-   type parameter (if there is one)

If two matching implicits are found in the same phase of implicit
resolution, an *ambiguous implicit* error is raised.

Implicits are often defined on a `trait`, which is then extended by an
object. This is to try and control the priority of an implicit
relative to another more specific one, to avoid ambiguous implicits.

The Scala Language Specification is rather vague for corner cases, and
the compiler implementation is the *de facto* standard. There are some
rules of thumb that we will use throughout this book, e.g. prefer
`implicit val` over `implicit object` despite the temptation of less
typing. It is a [quirk of implicit resolution](https://github.com/scala/bug/issues/10411) that `implicit object` on
companion objects are not treated the same as `implicit val`.

Implicit resolution falls short when there is a hierarchy of
typeclasses, like `Ordering` and `Numeric`. If we write a function
that takes an implicit `Ordering`, and we call it for a type which has
an instance of `Numeric` defined on the `Numeric` companion, the
compiler will fail to find it. A workaround is to add implicit
conversions to the companion of `Ordering` that up-cast more specific
instances. [Fixed In Dotty](https://github.com/lampepfl/dotty/issues/2047).


## Modelling OAuth2

We will finish this chapter with a practical example of data modelling
and typeclass derivation, combined with algebra / module design from
the previous chapter.

In our `drone-dynamic-agents` application, we must communicate with
Drone and Google Cloud using JSON over REST. Both services use [OAuth2](https://tools.ietf.org/html/rfc6749)
for authentication. Although there are many ways to interpret OAuth2,
we'll focus on the version that works for Google Cloud (the Drone
version is even simpler).


### Description

Every Google Cloud application needs to have an *OAuth 2.0 Client Key*
set up at

{lang="text"}
~~~~~~~~
  https://console.developers.google.com/apis/credentials?project={PROJECT_ID}
~~~~~~~~

You will be provided with a *Client ID* and a *Client secret*.

The application can then obtain a one time *code* by making the user
perform an *Authorization Request* in their browser (yes, really, **in
their browser**). We need to make this page open in the browser:

{lang="text"}
~~~~~~~~
  https://accounts.google.com/o/oauth2/v2/auth?\
    redirect_uri={CALLBACK_URI}&\
    prompt=consent&\
    response_type=code&\
    scope={SCOPE}&\
    access_type=offline&\
    client_id={CLIENT_ID}
~~~~~~~~

The *code* is delivered to the `{CALLBACK_URI}` in a `GET` request. To
capture it in our application, we need to have a web server listening
on `localhost`.

Once we have the *code*, we can perform an *Access Token Request*:

{lang="text"}
~~~~~~~~
  POST /oauth2/v4/token HTTP/1.1
  Host: www.googleapis.com
  Content-length: {CONTENT_LENGTH}
  content-type: application/x-www-form-urlencoded
  user-agent: google-oauth-playground
  code={CODE}&\
    redirect_uri={CALLBACK_URI}&\
    client_id={CLIENT_ID}&\
    client_secret={CLIENT_SECRET}&\
    scope={SCOPE}&\
    grant_type=authorization_code
~~~~~~~~

which gives a JSON response payload

{lang="text"}
~~~~~~~~
  {
    "access_token": "BEARER_TOKEN",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "REFRESH_TOKEN"
  }
~~~~~~~~

*Bearer tokens* typically expire after an hour, and can be refreshed
by sending an HTTP request with any valid *refresh token*:

{lang="text"}
~~~~~~~~
  POST /oauth2/v4/token HTTP/1.1
  Host: www.googleapis.com
  Content-length: {CONTENT_LENGTH}
  content-type: application/x-www-form-urlencoded
  user-agent: google-oauth-playground
  client_secret={CLIENT_SECRET}&
    grant_type=refresh_token&
    refresh_token={REFRESH_TOKEN}&
    client_id={CLIENT_ID}
~~~~~~~~

responding with

{lang="text"}
~~~~~~~~
  {
    "access_token": "BEARER_TOKEN",
    "token_type": "Bearer",
    "expires_in": 3600
  }
~~~~~~~~

Google expires all but the most recent 50 *bearer tokens*, so the
expiry times are just guidance. The *refresh tokens* persist between
sessions and can be expired manually by the user. We can therefore
have a one-time setup application to obtain the refresh token and then
include the refresh token as configuration for the user's install of
the headless server.


### Data

The first step is to model the data needed for OAuth2. We create an
ADT with fields having exactly the same name as required by the OAuth2
server. We will use `String` and `Long` for now, even though there is
a limited set of valid entries. We will remedy this when we learn
about *refined types*.

{lang="text"}
~~~~~~~~
  package http.oauth2.client.api
  
  import spinoco.protocol.http.Uri
  
  final case class AuthRequest(
    redirect_uri: Uri,
    scope: String,
    client_id: String,
    prompt: String = "consent",
    response_type: String = "code",
    access_type: String = "offline"
  )
  final case class AccessRequest(
    code: String,
    redirect_uri: Uri,
    client_id: String,
    client_secret: String,
    scope: String = "",
    grant_type: String = "authorization_code"
  )
  final case class AccessResponse(
    access_token: String,
    token_type: String,
    expires_in: Long,
    refresh_token: String
  )
  final case class RefreshRequest(
    client_secret: String,
    refresh_token: String,
    client_id: String,
    grant_type: String = "refresh_token"
  )
  final case class RefreshResponse(
    access_token: String,
    token_type: String,
    expires_in: Long
  )
~~~~~~~~

`Uri` is a typed ADT for URL requests from [fs2-http](https://github.com/Spinoco/fs2-http):

W> Avoid using `java.net.URL` at all costs: it uses DNS to resolve the
W> hostname part when performing `toString`, `equals` or `hashCode`.
W> 
W> Apart from being insane, and **very very** slow, these methods can throw
W> I/O exceptions (are not *pure*), and can change depending on your
W> network configuration (are not *deterministic*).
W> 
W> If you must use `java.net.URL` to satisfy a legacy system, at least
W> avoid putting it in a collection that will use `hashCode` or `equals`.
W> If you need to perform equality checks, create your own equality
W> function out of the raw `String` parts.


### Functionality

We need to marshal the data classes we defined in the previous section
into JSON, URLs and POST-encoded forms. Since this requires
polymorphism, we will need typeclasses.

[circe](https://github.com/circe/circe) gives us an ADT for JSON and typeclasses to convert to/from that
ADT (paraphrased for brevity):

{lang="text"}
~~~~~~~~
  package io.circe
  
  import simulacrum._
  
  sealed abstract class Json
  case object JNull extends Json
  final case class JBoolean(value: Boolean) extends Json
  final case class JNumber(value: JsonNumber) extends Json
  final case class JString(value: String) extends Json
  final case class JArray(value: Vector[Json]) extends Json
  final case class JObject(value: JsonObject) extends Json
  
  @typeclass trait Encoder[T] {
    def encodeJson(t: T): Json
  }
  @typeclass trait Decoder[T] {
    @op("as") def decodeJson(j: Json): Either[DecodingFailure, T]
  }
~~~~~~~~

where `JsonNumber` and `JsonObject` are optimised specialisations of
roughly `java.math.BigDecimal` and `Map[String, Json]`. To depend on
circe in your project we must add the following to `build.sbt`:

{lang="text"}
~~~~~~~~
  val circeVersion = "0.8.0"
  libraryDependencies ++= Seq(
    "io.circe"             %% "circe-core"    % circeVersion,
    "io.circe"             %% "circe-generic" % circeVersion,
    "io.circe"             %% "circe-parser"  % circeVersion
  )
~~~~~~~~

W> `java.math.BigDecimal` and especially `java.math.BigInteger` are not
W> safe objects to include in wire protocol formats. It is possible to
W> construct valid numerical values that will exception when parsed or
W> hang the `Thread` forever.
W> 
W> Travis Brown, author of Circe, has [gone to great lengths](https://github.com/circe/circe/blob/master/modules/core/shared/src/main/scala/io/circe/JsonNumber.scala) to protect
W> us. If you want to have similarly safe numbers in your wire protocols,
W> either use `JsonNumber` or settle for lossy `Double`.
W> 
W> {lang="text"}
W> ~~~~~~~~
W>   scala> new java.math.BigDecimal("1e2147483648")
W>   java.lang.NumberFormatException
W>     at java.math.BigDecimal.<init>(BigDecimal.java:491)
W>     ... elided
W>   
W>   scala> new java.math.BigDecimal("1e2147483647").toBigInteger
W>     ... hangs forever ...
W> ~~~~~~~~

Because circe provides *generic* instances, we can conjure up a
`Decoder[AccessResponse]` and `Decoder[RefreshResponse]`. This is an
example of parsing text into `AccessResponse`:

{lang="text"}
~~~~~~~~
  scala> import io.circe._
         import io.circe.generic.auto._
  
         for {
           json     <- io.circe.parser.parse("""
                       {
                         "access_token": "BEARER_TOKEN",
                         "token_type": "Bearer",
                         "expires_in": 3600,
                         "refresh_token": "REFRESH_TOKEN"
                       }
                       """)
           response <- json.as[AccessResponse]
         } yield response
  
  res = Right(AccessResponse(BEARER_TOKEN,Bearer,3600,REFRESH_TOKEN))
~~~~~~~~

We need to write our own typeclasses for URL and POST encoding. The
following is a reasonable design:

{lang="text"}
~~~~~~~~
  package http.encoding
  
  import simulacrum._
  
  @typeclass trait QueryEncoded[T] {
    def queryEncoded(t: T): Uri.Query
  }
  
  @typeclass trait UrlEncoded[T] {
    def urlEncoded(t: T): String
  }
~~~~~~~~

We need to provide typeclass instances for basic types:

{lang="text"}
~~~~~~~~
  import java.net.URLEncoder
  import spinoco.protocol.http.Uri
  
  object UrlEncoded {
    import ops._
    def instance[A](f: A => String): UrlEncoded[A] = new UrlEncoded[A] {
      override def urlEncoded(a: A): String = f(a)
    }
  
    implicit val UrlEncodedString: UrlEncoded[String] = instance { s =>
      URLEncoder.encode(s, "UTF-8")
    }
    implicit val UrlEncodedLong: UrlEncoded[Long] = instance { n =>
      n.toString
    }
    implicit val UrlEncodedStringySeq: UrlEncoded[Seq[(String, String)]] =
      instance { m =>
        m.map {
          case (k, v) => s"${k.urlEncoded}=${v.urlEncoded}"
        }.mkString("&")
      }
    implicit val UrlEncodedUri: UrlEncoded[Uri] = instance { u =>
      val scheme = u.scheme.toString
      val host   = u.host.host
      val port   = u.host.port.fold("")(p => s":$p")
      val path   = u.path.stringify
      val query  = u.query.params.toSeq.urlEncoded
      s"$scheme://$host$port$path?$query".urlEncoded
    }
  }
~~~~~~~~

A> Typing or reading
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit val UrlEncodedString: UrlEncoded[String] = new UrlEncoded[String] {
A>     override def urlEncoded(s: String): String = ...
A>   }
A> ~~~~~~~~
A> 
A> can be tiresome. We've basically said `UrlEncoded`, `String` four
A> times. A common pattern, that [may be added to simulacrum](https://github.com/mpilquist/simulacrum/issues/5) is to define
A> a method named `instance` on the typeclass companion
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   def instance[T](f: T => String): UrlEncoded[T] = new UrlEncoded[T] {
A>     override def urlEncoded(t: T): String = f(t)
A>   }
A> ~~~~~~~~
A> 
A> which then allows for instances to be defined more tersely as
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit val UrlEncodedString: UrlEncoded[String] = instance { s => ... }
A> ~~~~~~~~
A> 
A> Syntax sugar has been proposed in [dotty](https://github.com/lampepfl/dotty/issues/2879) allowing for:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   implicit val _: UrlEncoded[String] = instance { s => ... }
A> ~~~~~~~~

In a dedicated chapter on *Generic Programming* we will write generic
instances of `QueryEncoded` and `UrlEncoded`, but for now we will
write the boilerplate for the types we wish to convert:

{lang="text"}
~~~~~~~~
  import java.net.URLDecoder
  import http.encoding._
  import UrlEncoded.ops._
  
  object AuthRequest {
    implicit val QueryEncoder: QueryEncoded[AuthRequest] =
      new QueryEncoded[AuthRequest] {
        private def stringify[T: UrlEncoded](t: T) =
          URLDecoder.decode(t.urlEncoded, "UTF-8")
  
        def queryEncoded(a: AuthRequest): Uri.Query =
          Uri.Query.empty :+
            ("redirect_uri"  -> stringify(a.redirect_uri)) :+
            ("scope"         -> stringify(a.scope)) :+
            ("client_id"     -> stringify(a.client_id)) :+
            ("prompt"        -> stringify(a.prompt)) :+
            ("response_type" -> stringify(a.response_type)) :+
            ("access_type"   -> stringify(a.access_type))
      }
  }
  object AccessRequest {
    implicit val UrlEncoder: UrlEncoded[AccessRequest] =
      new UrlEncoded[AccessRequest] {
        def urlEncoded(a: AccessRequest): String =
          Seq(
            "code"          -> a.code.urlEncoded,
            "redirect_uri"  -> a.redirect_uri.urlEncoded,
            "client_id"     -> a.client_id.urlEncoded,
            "client_secret" -> a.client_secret.urlEncoded,
            "scope"         -> a.scope.urlEncoded,
            "grant_type"    -> a.grant_type.urlEncoded
          ).urlEncoded
      }
  }
  object RefreshRequest {
    implicit val UrlEncoder: UrlEncoded[RefreshRequest] =
      new UrlEncoded[RefreshRequest] {
        def urlEncoded(r: RefreshRequest): String =
          Seq(
            "client_secret" -> r.client_secret.urlEncoded,
            "refresh_token" -> r.refresh_token.urlEncoded,
            "client_id"     -> r.client_id.urlEncoded,
            "grant_type"    -> r.grant_type.urlEncoded
          ).urlEncoded
      }
  }
~~~~~~~~


### Module

That concludes the data and functionality modelling required to
implement OAuth2. Recall from the previous chapter that we define
mockable components that need to interact with the world as algebras,
and we define pure business logic in a module.

We define our dependency algebras, and use context bounds to show that
our responses must have a `Decoder` and our `POST` payload must have a
`UrlEncoded`:

{lang="text"}
~~~~~~~~
  import java.time.LocalDateTime
  
  package http.client.algebra {
    final case class Response[T](header: HttpResponseHeader, body: T)
  
    trait JsonHttpClient[F[_]] {
      def get[B: Decoder](
        uri: Uri,
        headers: List[HttpHeader] = Nil
      ): F[Response[B]]
  
      def postUrlencoded[A: UrlEncoded, B: Decoder](
        uri: Uri,
        payload: A,
        headers: List[HttpHeader] = Nil
      ): F[Response[B]]
    }
  }
  
  package http.oauth2.client.algebra {
    final case class CodeToken(token: String, redirect_uri: Uri)
  
    trait UserInteraction[F[_]] {
      /** returns the Uri of the local server */
      def start: F[Uri]
  
      /** prompts the user to open this Uri */
      def open(uri: Uri): F[Unit]
  
      /** recover the code from the callback */
      def stop: F[CodeToken]
    }
  
    trait LocalClock[F[_]] {
      def now: F[LocalDateTime]
    }
  }
~~~~~~~~

some convenient data classes

{lang="text"}
~~~~~~~~
  final case class ServerConfig(
    auth: Uri,
    access: Uri,
    refresh: Uri,
    scope: String,
    clientId: String,
    clientSecret: String
  )
  final case class RefreshToken(token: String)
  final case class BearerToken(token: String, expires: LocalDateTime)
~~~~~~~~

and then write an OAuth2 client:

{lang="text"}
~~~~~~~~
  package logic {
    import java.time.temporal.ChronoUnit
    import io.circe.generic.auto._
    import http.encoding.QueryEncoded.ops._
  
    class OAuth2Client[F[_]: Monad](
      config: ServerConfig
    )(
      implicit
      user: UserInteraction[F],
      server: JsonHttpClient[F],
      clock: LocalClock[F]
    ) { 
      def authenticate: F[CodeToken] =
        for {
          callback <- user.start
          params   = AuthRequest(callback, config.scope, config.clientId)
          _        <- user.open(config.auth.withQuery(params.queryEncoded))
          code     <- user.stop
        } yield code
  
      def access(code: CodeToken): F[(RefreshToken, BearerToken)] =
        for {
          request <- AccessRequest(code.token,
                                   code.redirect_uri,
                                   config.clientId,
                                   config.clientSecret).pure[F]
          response <- server
                       .postUrlencoded[AccessRequest, AccessResponse](
                         config.access,
                         request
                       )
          time    <- clock.now
          msg     = response.body
          expires = time.plus(msg.expires_in, ChronoUnit.SECONDS)
          refresh = RefreshToken(msg.refresh_token)
          bearer  = BearerToken(msg.access_token, expires)
        } yield (refresh, bearer)
  
      def bearer(refresh: RefreshToken): F[BearerToken] =
        for {
          request <- RefreshRequest(config.clientSecret,
                                    refresh.token,
                                    config.clientId).pure[F]
          response <- server
                       .postUrlencoded[RefreshRequest, RefreshResponse](
                         config.refresh,
                         request
                       )
          time    <- clock.now
          msg     = response.body
          expires = time.plus(msg.expires_in, ChronoUnit.SECONDS)
          bearer  = BearerToken(msg.access_token, expires)
        } yield bearer
    }
  }
~~~~~~~~


## Summary

-   data types are defined as *products* (`final case class`) and
    *coproducts* (`sealed abstract class` or nested `Either`).
-   specific functions are defined on `object` or `implicit class`,
    according to personal taste.
-   polymorphic functions are defined as *typeclasses*. Functionality is
    provided via "has a" *context bounds*, rather than "is a" class
    hierarchies.
-   *typeclass instances* are implementations of the typeclass.
-   `@simulacrum.typeclass` generates `.ops` on the companion, providing
    convenient syntax for types that have a typeclass instance.
-   *typeclass derivation* is compiletime composition of typeclass
    instances.
-   *generic instances* automatically derive instances for your data
    types.


