

# For Comprehensions

Scala's `for` comprehension is the ideal FP abstraction for sequential
programs that interact with the world. Since we'll be using it a lot,
we're going to relearn the principles of `for` and how cats can help
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
`cats.FlatMap[T]` is available for `T`, it will provide `map` and
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

A> We could code golf it and write
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   getFromRedis(key) orElseM getFromSql(key)
A> ~~~~~~~~
A> 
A> by defining <https://github.com/typelevel/cats/issues/1625> but it can
A> be a cognitive burden to remember all these helper methods. The level
A> of verbosity of a codebase vs code reuse of trivial functions is a
A> stylistic decision for each team.

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
    Future.fail(new RuntimeException(msg))
  
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
A> then cats lets us create a `T[A]` from a value `a:A` by calling
A> `a.pure[T]`.
A> 
A> Cats provides `Monad[Future]` and `.pure[Future]` simply calls
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
what a *monad transformer* does, and cats provides implementations for
`Option`, `Future` and `Either` named `OptionT`, `FutureT` and
`EitherT` respectively.

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

`.value` returns us to the original context

{lang="text"}
~~~~~~~~
  scala> result.value
  res: Future[Option[Int]] = Future(<not completed>)
~~~~~~~~

Alternatively, `OptionT[Future, Int]` has `getOrElse` and `getOrElseF`
methods, taking `Int` and `Future[Int]` respectively, returning a
`Future[Int]`.

The monad transformer also allows us to mix `Future[Option[_]]` calls
with methods that just return plain `Future` via `OptionT.liftF`

{lang="text"}
~~~~~~~~
  scala> def getC: Future[Int] = ...
  scala> val result = for {
           a <- OptionT(getA)
           b <- OptionT(getB)
           c <- OptionT.liftF(getC)
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
           c <- OptionT.liftF(getC)
           d <- OptionT(getD.pure[Future])
         } yield (a * b) / (c * d)
  result: OptionT[Future, Int] = OptionT(Future(<not completed>))
~~~~~~~~

It is messy again, but it's better than writing nested `flatMap` and
`map` by hand. We can clean it up with a DSL that handles all the
required conversions into `OptionT[Future, _]`

{lang="text"}
~~~~~~~~
  implicit class Ops[In](in: In) {
    def |>[Out](f: In => Out): Out = f(in)
  }
  def liftFutureOption[A](f: Future[Option[A]]) = OptionT(f)
  def liftFuture[A](f: Future[A]) = OptionT.liftF(f)
  def liftOption[A](o: Option[A]) = OptionT(o.pure[Future])
  def lift[A](a: A)               = liftOption(Some(a))
~~~~~~~~

which visually separates the logic from the transformers

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

This approach also works for `EitherT` or `FutureT` as the inner
context, but their lifting methods are more complex and require
parameters. cats provides monad transformers for a lot of its own
types, so it's worth checking if one is available.

Notably absent is `ListT` (or `TraversableT`) because it is difficult
to create a well-behaved monad transformer for collections. It comes
down to the unfortunate fact that grouping of the operations can
unintentionally reorder `flatMap` calls.
<https://github.com/typelevel/cats/issues/977> aims to implement
`ListT`. Implementing a monad transformer is an advanced topic.

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

`@freestyle.free` is a macro annotation that generates boilerplate.
The details of the boilerplate are not important right now, we will
explain as required and go into gruelling detail in the Appendix.

`@free` requires that all methods return `FS[A]`, expanded into
something useful in a moment.

{lang="text"}
~~~~~~~~
  package algebra
  
  import java.time.ZonedDateTime
  import cats.data.NonEmptyList
  import freestyle._
  
  object drone {
    @free trait Drone {
      def getBacklog: FS[Int]
      def getAgents: FS[Int]
    }
  }
  
  object machines {
    case class Node(id: String)
  
    @free trait Machines {
      def getTime: FS[ZonedDateTime]
      def getManaged: FS[NonEmptyList[Node]]
      def getAlive: FS[Map[Node, ZonedDateTime]] // node and its start zdt
      def start(node: Node): FS[Node]
      def stop(node: Node): FS[Node]
    }
  }
~~~~~~~~

We've used `cats.data.NonEmptyList`, a wrapper around the standard
library's `List`, otherwise everything should be familiar.

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
  import cats.data.NonEmptyList
  import cats.implicits._
  import freestyle._
  import algebra.drone._
  import algebra.machines._
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
    managed: NonEmptyList[Node],
    alive: Map[Node, ZonedDateTime],
    pending: Map[Node, ZonedDateTime], // requested at zdt
    time: ZonedDateTime
  )
~~~~~~~~

Now we are ready to write our business logic, but we need to indicate
that we depend on `Drone` and `Machines`.

We create a *module* to contain our main business logic. A module is
pure and depends only on other modules, algebras and pure functions.

The `@freestyle.module` macro annotation generates boilerplate for
dependency injection, we leave an unassigned `val` for each `@free` or
`@module` dependency that we need to use. Declaring dependencies this
way should be familiar if you've ever used Spring's `@Autowired`

{lang="text"}
~~~~~~~~
  @module trait DynAgents {
    val d: Drone
    val m: Machines
~~~~~~~~

A> At the time of writing, this `@module` code causes problems due to an
A> [upstream bug in scala.meta](https://github.com/frees-io/freestyle/issues/369). The workaround is to write it in the
A> slighly more verbose form:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   @module trait Deps {
A>     val d: Drone
A>     val m: Machines
A>   }
A>   
A>   class DynAgents[F[_]]()(implicit D: Deps[F]) {
A>     import D._
A>     type FS[A] = FreeS[F, A]
A> ~~~~~~~~
A> 
A> This is just boilerplate and there is no need to be aware that `Deps`
A> exists. Later on if we reference `DynAgents.Op`, just replace it with
A> `Deps.Op`.

We now have access to the algebra of `Drone` and `Machines` as `d` and
`m`, respectively, with methods returning `FS`, which is *monadic*
(i.e. has an implicit `Monad`) and can be the context of a `for`
comprehension.

Our business logic will run in an infinite loop (pseudocode)

{lang="text"}
~~~~~~~~
  state = initial()
  while True:
    state = update(state)
    state = act(state)
~~~~~~~~

We must write three functions: `initial`, `update` and `act`, all
returning an `FS[WorldView]`.

### initial

In `initial` we call all external services and aggregate their results
into a `WorldView`. We default the `pending` field to an empty `Map`.

{lang="text"}
~~~~~~~~
  def initial: FS[WorldView] = for {
    db <- d.getBacklog
    da <- d.getAgents
    mm <- m.getManaged
    ma <- m.getAlive
    mt <- m.getTime
  } yield WorldView(db, da, mm, ma, Map.empty, mt)
~~~~~~~~

Recall from Chapter 1 that `flatMap` (i.e. when we use the `<-`
generator) allows us to operate on a value that is computed at
runtime. When we return an `FS` we are returning another program to be
interpreted at runtime, that we can then `flatMap`. This is how we
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
  def update(old: WorldView): FS[WorldView] = for {
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
    def unapply(world: WorldView): Option[Node] = world match {
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
    def unapply(world: WorldView): Option[NonEmptyList[Node]] = world match {
      case WorldView(backlog, _, _, alive, pending, time) if alive.nonEmpty =>
        val stale = (alive -- pending.keys).collect {
          case (n, started)
               if backlog == 0 && timediff(started, time).toMinutes % 60 >= 58 => n
          case (n, started)
               if timediff(started, time) >= 5.hours                           => n
        }.toList
        NonEmptyList.fromList(stale)
  
      case _ => None
    }
  }
~~~~~~~~

Now that we have detected the scenarios that can occur, we can write
the `act` method. When we schedule a node to be started or stopped, we
add it to `pending` noting the time that we scheduled the action.

{lang="text"}
~~~~~~~~
  def act(world: WorldView): FS[WorldView] = world match {
    case NeedsAgent(node) =>
      for {
        _ <- m.start(node)
        update = world.copy(pending = Map(node -> world.time))
      } yield update
  
    case Stale(nodes) =>
      nodes.foldM(world) { (world, n) =>
        for {
          _ <- m.stop(n)
          update = world.copy(pending = world.pending + (n -> world.time))
        } yield update
      }
  
    case _ => world.pure[FS]
  }
~~~~~~~~

Because `NeedsAgent` and `Stale` do not cover all possible situations,
we need a catch-all `case _` to do nothing. Recall from Chapter 2 that
`.pure` creates the `for`'s (monadic) context from a value.

`foldM` is like `foldLeft` over `nodes`, but each iteration of the
fold may return a monadic value. In our case, each iteration of the
fold returns `FS[WorldView]`.

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
    val node1 = Node("1243d1af-828f-4ba3-9fc0-a19d86852b5a")
    val node2 = Node("550c4943-229e-47b0-b6be-3d686c5f013f")
    val managed = NonEmptyList(node1, List(node2))
  
    import ZonedDateTime.parse
    val time1 = parse("2017-03-03T18:07:00.000+01:00[Europe/London]")
    val time2 = parse("2017-03-03T18:59:00.000+01:00[Europe/London]") // +52 mins
    val time3 = parse("2017-03-03T19:06:00.000+01:00[Europe/London]") // +59 mins
    val time4 = parse("2017-03-03T23:07:00.000+01:00[Europe/London]") // +5 hours
  
    val needsAgents = WorldView(5, 0, managed, Map.empty, Map.empty, time1)
  }
  import Data._
~~~~~~~~

We implement algebras by creating *handlers* that extend from the
`.Handler` that `@free` generates for us. We don't extend `Drone` and
`Machines` directly.

Our "mock" implementations simply play back a fixed `WorldView`. We've
isolated the state of our system, so we can use `var` to store the
state (but this is not threadsafe).

{lang="text"}
~~~~~~~~
  class StaticHandlers(state: WorldView) {
    var started, stopped: Int = 0
  
    implicit val drone: Drone.Handler[Id] = new Drone.Handler[Id] {
      def getBacklog: Int = state.backlog
      def getAgents: Int = state.agents
    }
  
    implicit val machines: Machines.Handler[Id] = new Machines.Handler[Id] {
      def getAlive: Map[Node, ZonedDateTime] = state.alive
      def getManaged: NonEmptyList[Node] = state.managed
      def getTime: ZonedDateTime = state.time
      def start(node: Node): Node = { started += 1 ; node }
      def stop(node: Node): Node = { stopped += 1 ; node }
    }
  
    val program = DynAgents[DynAgents.Op]
  }
~~~~~~~~

When we write a unit test (here using `FlatSpec` from scalatest), we
create an instance of `StaticHandlers` and then import all of its
members.

`FS` has a method `interpret`, requiring implicit handlers for all of
its dependencies. Our implicit `drone` and `machines` both use the
`Id` execution context and therefore interpreting this program with
them returns an `Id[WorldView]` that we can assert on.

In this trivial case we just check that the `initial` method returns
the same value that we use in the static handlers:

{lang="text"}
~~~~~~~~
  "Business Logic" should "generate an initial world view" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._
  
    program.initial.interpret[Id] shouldBe needsAgents
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
    program.update(old).interpret[Id] shouldBe world
  }
  
  it should "request agents when needed" in {
    val handlers = new StaticHandlers(needsAgents)
    import handlers._
  
    val expected = needsAgents.copy(
      pending = Map(node1 -> time1)
    )
  
    program.act(needsAgents).interpret[Id] shouldBe expected
  
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

As opposed to `flatMap` for sequential operations, cats uses
`Cartesian` syntax for parallel operations:

{lang="text"}
~~~~~~~~
  d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime
~~~~~~~~

If each of the parallel operations returns a value in the same monadic
context, then the `Cartesian` product has a `map` method that will
provide all of the results as a tuple. Rewriting `update` to take
advantage of this:

{lang="text"}
~~~~~~~~
  def initial: FS[WorldView] =
    (d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime).map {
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
`map` each element into an `FS[Node]`, returning an
`FS[NonEmptyList[Node]]`. The method is called `traverse`, and when we
`flatMap` over it we get a `NonEmptyList[Node]` that we can deal with
in a simple way:

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

It is also not enough to implement a `Future` handler, it is necessary
to

{lang="text"}
~~~~~~~~
  import freestyle.NonDeterminism._
~~~~~~~~

when running the `interpret` method to ensure that the `Future`
interpreter knows it can perform actions in a non-deterministic order.

Of course, we need to be careful when implementing handlers such that
they can perform operations safely in parallel, perhaps requiring
protecting internal state with concurrency locks.

## Free Monad

What we've been doing in this chapter is using the *free monad*,
`cats.free.Free`, to build up the definition of our program as a data
type and then we interpret it. Freestyle calls it `FS`, which is just
a type alias to `Free`, hiding an irrelevant type parameter.

The reason why we use `Free` instead of just implementing `cats.Monad`
directly (e.g. for `Id` or `Future`) is an unfortunate consequence of
running on the JVM. Every nested call to `map` or `flatMap` adds to
the stack, eventually resulting in a `StackOverflowError`.

`Free` is a `sealed trait` that roughly looks like:

{lang="text"}
~~~~~~~~
  sealed trait Free[S[_], A] {
    def pure(a: A): Free[S, A] = Pure(a)
    def map[B](f: A => B): Free[S, B] = flatMap(a => Pure(f(a)))
    def flatMap[B](f: A => Free[S, B]): Free[S, B] = FlatMapped(this, f)
  }
  
  case class Pure[S[_], A](a: A) extends Free[S, A]
  case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
  case class FlatMapped[S[_], B, C](c: Free[S, C],
                                    f: C => Free[S, B]) extends Free[S, B]
~~~~~~~~

Its definition of `pure` / `map` / `flatMap` do not do any work, they
just build up data types that live on the heap. Work is delayed until
Free is *interpreted*. This technique of using heap objects to
eliminate stack growth is known as *trampolining*.

When we use the `@free` annotation, a `sealed trait` data type is
generated for each of our algebras, with a `case class` per method,
allowing trampolining. When we write a `Handler`, Freestyle is
converting pattern matches over heap objects into method calls.

### Free as in Monad

`Free[S[_], A]` can be *generated freely* for any choice of `S`, hence
the name. However, from a practical point of view, there needs to be a
`Monad[S]` in order to interpret it --- so it's more like an interest
only mortgage where you still have to buy the house at the end.

## Reality Check

In this chapter we've experienced some of the practical benefits of FP
when designing and testing applications:

1.  clean separation of components
2.  isolated, fast and reproducible tests of business logic: extreme mocking
3.  easy parallelisation

However, even if we look past the learning curve of FP, there are
still some real challenges that remain:

1.  trampolining has a performance impact due to increased memory churn
    and garbage collection pressure.
2.  there is not always IDE support for the advanced language features,
    macros or compiler plugins.
3.  implementation details --- as we have already seen with `for`
    syntax sugar, `@module`, and `Free` --- can introduce mental
    overhead and become a blocker when they don't work.
4.  the distinction between pure / side-effecting code, or stack-safe /
    stack-unsafe, is not enforced by the scala compiler. This requires
    developer discipline.
5.  the developer community is still small. Getting help from the
    community can often be a slow process.

As with any new technology, there are rough edges that will be fixed
with time. Most of the problems are because there is a lack of
commercially-funded tooling in FP scala. If you see the benefit of FP,
you can help out by getting involved.

Although FP Scala cannot be as fast as streamlined Java using
mutation, the performance impact is unlikely to affect you if you're
already considering targetting the JVM. Measure the impact before
making a decision if it is important to you.

In the following chapters we are going to learn some of the vast
library of functionality provided by the ecosystem, how it is
organised and how you can find what you need (e.g. how did we know to
use `foldM` or `traverse` when we implemented `act`?). This will allow
us to complete the implementation of our application by building
additional layers of `@module`, use better alternatives to `Future`,
and remove redundancy that we've accidentally introduced.

## Summary

1.  *algebras* define the boundaries between systems, implemented by
    *handlers*.
2.  *modules* define pure logic and depend on algebras and other
    modules.
3.  modules are *interpreted* by handlers in terms of the Free Monad,
    which ensures stack safety at a modest cost to performance.
4.  Test handlers can mock out the side-effecting parts of the system
    with trivial implementations, enabling a high level of test
    coverage for the business logic.
5.  algebraic methods can be performed in parallel by taking their
    `Cartesian` product or traversing sequences, caveat emptor.

# Data and Functionality

From OOP we are used to thinking about data and functionality
together: class hierarchies carry methods, and traits can demand that
data fields exist. Polymorphism of an object is in terms of "is a"
relationships, requiring classes to inherit from common interfaces.
This can get messy as a codebase grows. Simple data types become
obscured by hundreds of lines of methods, trait mixins suffer from
initialisation order errors, and testing / mocking of highly coupled
components becomes a chore.

FP takes a different approach, defining data and functionality
separately. In this chapter, we will cover the basics of data types
and the advantages of constrainting ourselves to a subset of the Scala
language. We will also discover *type classes* as an alternative way
to achieve polymorphism: thinking about data in terms of "has a"
rather than "is a" relationships.

## Data

In FP we make data types explicit, rather than hidden as
implementation detail.

The fundamental building blocks of data types are

-   `final case class` also known as *products*
-   `sealed trait` also known as *coproducts*
-   `case object` *singletons* and *values* (`Int`, `Double`, `String`, etc)

with no methods or fields other than the constructor parameters.

A `sealed trait` containing only singletons is equivalent to an `enum`
in Java and is a more descriptive replacement for `Boolean` or `Int`
feature flags. Singletons are just value types in disguise, so we can
think about data in terms of *products*, *coproducts* and *values*.

The collective name for products and coproducts is *Algebraic Data
Type* (ADT), an unfortunate name collision with the algebras from the
previous chapter. We compose data types from the `AND` and `XOR`
algebra: a product always has every type, but a coproduct can be only
one of the possible types. For example

-   product: `a AND b AND c`
-   coproduct: `x XOR y XOR z`

and in Scala (remembering that `.type` is the type of a singleton):

{lang="text"}
~~~~~~~~
  // values
  case object A
  type B = String
  type C = Int
  
  // product
  case class ABC(a: A.type, b: B, c: C)
  
  // coproduct
  sealed trait XYZ
  case object X extends XYZ
  case object Y extends XYZ
  case class Z(b: B) extends XYZ
~~~~~~~~

### Generalised ADTs

When we introduce a type parameter into an ADT, we call it a
*Generalised Algebraic Data Type* (GADT).

`List` is a GADT:

{lang="text"}
~~~~~~~~
  sealed trait List[+T]
  case object Nil extends List[Nothing]
  final case class ::[+T](head: T, tail: List[T]) extends List[T]
~~~~~~~~

If an ADT refers to itself, we call it a *recursive type*. Scala's
`List` is recursive because the `::` (pronounced "cons", as in
"constructor cell") contains a reference to `List`.

ADTs can contain pure functions. In fact we have already seen this in
the previous chapter when we encountered `Free`, look again at the `f`
parameter in `FlatMapped`

{lang="text"}
~~~~~~~~
  sealed trait Free[S[_], A]
  case class Pure[S[_], A](a: A) extends Free[S, A]
  case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
  case class FlatMapped[S[_], B, C](c: Free[S, C],
                                    f: C => Free[S, B]) extends Free[S, B]
~~~~~~~~

Functions on ADTs come with some caveats as they don't translate
perfectly onto the JVM. For example, legacy `Serializable`,
`hashCode`, `equals` and `toString` do not behave as one might
reasonably expect.

In particular, `Serializable` may attempt to serialise the entire
closure of a function, and has been known to crash production servers.
A similar caveat applies to legacy Java classes such as `Throwable`,
which can carry references to arbitrary objects. This is one of the
reasons why we restrict what can live on an ADT.

A similar caveat applies to *by name* parameters, e.g. `foo:=>String`,
which are equivalent to functions taking no parameter. Lazy parameters
can be used to define infinitely long data types. This is an advanced
and specialist topic that we will not cover.

We will explore alternatives to these legacy methods when we discuss
the cats library in the next chapter, at the cost of losing
interoperability with some legacy Java and Scala code.

### Exhaustivity

It is important that we use `sealed trait`, not just `trait`, when
defining a data type. Sealing a trait means that all subtypes must be
defined in the same file, allowing the compiler to know about them in
pattern match exhaustivity checks and in macros that eliminate
boilerplate.

The scala compiler will perform exhaustivity checks when matching on
sealed traits, e.g.

{lang="text"}
~~~~~~~~
  scala> sealed trait Foo
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
`trait` is not sealed or if there are guards, e.g.

{lang="text"}
~~~~~~~~
  scala> def thing(foo: Foo) = foo match {
           case Bar(flag) if flag => true
         }
  
  scala> thing(Baz)
  scala.MatchError: Baz (of class Baz$)
    at .thing(<console>:15)
~~~~~~~~

This will also fail at runtime if we pass a `Bar(false)`.

Guards should not be used when matching on a `sealed trait`. When
guards are used on a `case class`, there should always be a `case _
=>` with a default value, unless you have proven that it cannot occur.

[`-Xstrict-patmat-analysis`](https://github.com/scala/scala/pull/5617) flag has been proposed as a language
improvement to perform additional pattern matcher checks.

### Alternative Products and Coproducts

Another form of product is a tuple, which is like an unlabelled `case
class`. `(A.type, B, C)` is equivalent to `ABC` in the above example.
It's best to use `case class` when part of an ADT because the lack of
names is awkward to deal with.

Another form of coproduct is when we nest `Either` types. e.g.

{lang="text"}
~~~~~~~~
  Either[X.type, Either[Y.type, Z]]
~~~~~~~~

equivalent to the `XYZ` sealed trait. A cleaner syntax to define
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

Pattern matching on this form of coproduct can be tedious, which is
why [Union Types](https://contributors.scala-lang.org/t/733) are being explored in the Dotty next-generation scala
compiler. Workarounds such as [totalitarian](https://github.com/propensive/totalitarian)'s `Disjunct` exist as
another way of encoding anonymous coproducts.

### Convey Information

Besides being a container for necessary business information, data
types can be used to encode constraints. For example,

{lang="text"}
~~~~~~~~
  final case class NonEmptyList[+T](head: T, tail: List[T])
~~~~~~~~

can never be empty. This makes `cats.data.NonEmptyList` a useful data
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
introduce `cats.data.Validated` in the next chapter.

### Simple to Share

By not providing any functionality, ADTs can have a minimal set of
dependencies. This makes them easy to publish and share with other
developers. By using a simple data modelling language, it makes it
possible to interact with cross-discipline teams, such as DBAs, UI
developers and business analysts, using the actual code instead of a
hand written document as the source of truth.

Furthermore, tooling can be more easily written to produce schemas for
other programming languages and wire protocols that map naturally to
the data types.

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

In FP, functions are *total* and must return a instance for every input,
no `Exception`. Minimising the complexity of inputs and outputs is the
best way to achieve totality. As a rule of thumb, it is a sign of a
badly designed function when the complexity of a function's return
instance is larger than the product of its inputs: it is a source of
entropy.

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
  sealed trait Config
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
it is easy to test a sample of values with the [scalacheck](https://www.scalacheck.org/) property
testing library as we will see in the next section. If a random sample
of a data type has a low probability of being valid, it's a sign that
the data has been modelling incorrectly.

### Optimisations

A big advantage of using a simplified subset of the Scala language to
represent data types is that tooling can optimise the JVM bytecode
representation.

For example, [stalagmite](https://github.com/fommil/stalagmite) can pack `Boolean` and `Option` fields into an
`Array[Byte]`, cache instances, memoise `hashCode`, optimise `equals`,
enforce validation, use `@switch` statements when pattern matching,
and much more.

These optimisations are not applicable to general `class` definitions
that may be managing state, throwing exceptions, or providing adhoc
method implementations and pattern extractors.

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
that work **for every** `case class` and `sealed trait`.

{lang="text"}
~~~~~~~~
  scala> import shapeless._
         case class Foo(a: String, b: Long)
         Generic[Foo].to(Foo("hello", 13L))
  res: String :: Long :: HNil = hello :: 13 :: HNil
  
  scala> Generic[Foo].from("hello" :: 13L :: HNil)
  res: Foo = Foo(hello,13)
  
  scala> sealed trait Bar
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

## TODO Functionality

### implicit class to add functionality to a case class, and to a sealed trait

### aside about AnyVal and performance optimisation, also why cats sometimes uses abstract class with methods

### Advantage is that it keeps the data model clean, and can live higher up the dependency chain where deps are available

### Disadvantage is that developers may not be aware of what functionality is available for a class. Tooling could help.

### typeclasses, what if the functionality is more general than your ADT?

### Addable / Semigroup (??? maybe) with syntax

### context bounds, reads like "has a"

### implicit resolution rules

### scalacheck / properties

### kittens, don't need to write it

### similarly scalacheck-shapeless

### allows overriding with different implementations (e.g. the "merge business rules" example)

we don't always get to choose our APIs, and sometimes our customers ask us to throw an exception

### computed at compile time, more efficient than runtime lookup

cachedImplicit into a val

### downside is compile time speeds for ADTs of 50+

but there is magnolia and Miles' efforts in the compiler to address this


