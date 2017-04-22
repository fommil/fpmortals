

# For Comprehensions

Scala's `for` comprehension is heavily used in FP --- it is the ideal
abstraction to write pure procedural code. But most Scala developers
only use `for` to loop over collections and are not aware of its full
potential.

In this chapter, we're going to visit the principles of `for` and how
cats can help us to write cleaner code with the standard library.

## Syntax Sugar

Scala's `for` is just a simple rewrite rule that doesn't have any
contextual information. The compiler does the rewrite during parsing
as *syntax sugar*, designed only to reduce verbosity in the language.

The easiest way to see what a `for` comprehension is doing is to use
the `show` and `reify` feature in the REPL to print out what code
looks like after type inference (alternatively, invoke the compiler
with the `-Xprint:typer` flag):

{lang="text"}
~~~~~~~~
scala> import scala.reflect.runtime.universe._
scala> val a, b, c = Option(1)
scala> show { reify {
         for { i <- a ; j <- b ; k <- c } yield (i + j + k)
       } }

$read.a.flatMap(
  ((i) => $read.b.flatMap(
    ((j) => $read.c.map(
      ((k) => i.$plus(j).$plus(k)))))))
~~~~~~~~

There's a lot of noise due to additional sugarings that you can ignore
(e.g. `+` is rewritten `$plus`). The basic rule of thumb is that every
`<-` (generator) is a nested `flatMap` call, with the final generator
being a `map`, containing the `yield`.

For the remaining examples, we'll skip the `show` and `reify` for
brevity when the REPL line is `reify>`, and also manually clean up the
generated code so that it doesn't become a distraction.

We can also assign values inline like `val ij = i + j` (the `val`
keyword is not needed).

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

A> `val` doesn't have to assign to a single value, it can be anything
A> that works as a `case` in a pattern match. The same is true for
A> assignment in `for` comprehensions.
A> 
A> Be careful that you don't miss any cases or you'll get a runtime
A> exception (a *totality* failure):
A> 
A> {lang="text"}
A> ~~~~~~~~
A> scala> val (first, second) = ("hello", "world")
A> first: String = hello
A> second: String = world
A> 
A> scala> val list: List[Int] = ...
A> scala> val head :: tail = list
A> head: Int = 1
A> tail: List[Int] = List(2, 3)
A> 
A> // not safe to assume the list is non-empty
A> scala> val a :: tail = list
A> scala.MatchError: List()
A> ~~~~~~~~

Unfortunately we [cannot assign a value before any generators](https://github.com/typelevel/scala/issues/143):

{lang="text"}
~~~~~~~~
scala> for {
         initial = getDefault
         i <- a
       } yield initial + i
<console>:1: error: '<-' expected but '=' found.
~~~~~~~~

but we can workaround it by defining a `val` outside the `for` or wrap
the initial assignment:

{lang="text"}
~~~~~~~~
scala> val initial = getDefault
       for { i <- a } yield initial + i

scala> for {
         initial <- Option(getDefault)
         i <- a
       } yield initial + i
~~~~~~~~

It's possible to put `if` statements after a generator to call
`withFilter`:

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

Older versions of scala called `filter`, but since `filter` in the
collections library creates new collections, and memory churn,
`withFilter` was more performant.

Finally, if there is no `yield`, the compiler will use `foreach`
instead of `flatMap`, which is only useful for side-effects, and
therefore discouraged.

{lang="text"}
~~~~~~~~
reify> for { i <- a ; j <- b } println(s"$i $j")

a.foreach { i => b.foreach { j => println(s"$i $j") } }
~~~~~~~~

The full set of methods that can be (optionally) used by a `for`
comprehension do not share a common super type; each generated snippet
is independently compiled. If there were a trait, it would roughly
look like:

{lang="text"}
~~~~~~~~
trait ForComprehendable[C[_]] {
  def map[A, B](f: A => B): C[B]
  def flatMap[A, B](f: A => C[B]): C[B]
  def withFilter[A](p: A => Boolean): C[A]
  def foreach[A](f: A => Unit): Unit
}
~~~~~~~~

If an implicit `cats.FlatMap[T]` (or `cats.Monad[T]`) is available for
your type `T`, you automatically get `map` and `flatMap` and can use
your `T` in a `for` comprehension. `withFilter` and `foreach` are not
concepts that are useful in functional programming, so we won't
discuss them any further.

A> Many developers are surprised that when they start a `Future` in a
A> `for` comprehension, their calculations do not run in parallel:
A> 
A> {lang="text"}
A> ~~~~~~~~
A> import scala.concurrent._
A> import ExecutionContext.Implicits.global
A> 
A> for {
A>   i <- Future { expensiveCalc() }
A>   j <- Future { anotherExpensiveCalc() }
A> } yield (i + j)
A> ~~~~~~~~
A> 
A> The is because the `flatMap` spawning `anotherExpensiveCalc` is only
A> called **after** `expensiveCalc`. To ensure that two `Future`
A> calculations begin in parallel, start them outside the `for`
A> comprehension.
A> 
A> {lang="text"}
A> ~~~~~~~~
A> val a = Future { expensiveCalc() }
A> val b = Future { anotherExpensiveCalc() }
A> for { i <- a ; j <- b } yield (i + j)
A> ~~~~~~~~
A> 
A> `for` comprehensions are fundamentally for defining procedural
A> programs. We will show a far superior way of defining parallel
A> computations in a later chapter.

## Unhappy path

So far we've only considered what the rewrite rules are, not what is
happening in `map` and `flatMap`. Let's consider what happens when the
container decides that it can't proceed any further.

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

A> How often have you seen a function that takes `Option` parameters but
A> requires them all to exist? An alternative to throwing a runtime
A> exception is to use a `for` comprehension:
A> 
A> {lang="text"}
A> ~~~~~~~~
A> def namedThings(
A>   someName  : Option[String],
A>   someNumber: Option[Int]
A> ): Option[String] = for {
A>   name   <- someName
A>   number <- someNumber
A> } yield s"$number ${name}s"
A> ~~~~~~~~
A> 
A> but this is clunky and bad style. If a function requires every input
A> then it should make this requirement explicit, pushing the
A> responsibility of dealing with optional parameters to its caller ---
A> don't use `for` unless you need to.

If any of `a,b,c` are `None`, the comprehension short-circuits with
`None` but it doesn't tell us what went wrong. If we use `Either`,
then a `Left` will cause the `for` comprehension to short circuit with
some extra information, much better than `Option` for error reporting:

{lang="text"}
~~~~~~~~
scala> val a = Right(1)
scala> val b = Right(2)
scala> val c: Either[String, Int] = Left("sorry, no c")
scala> for { i <- a ; j <- b ; k <- c } yield (i + j + k)

Left(sorry, no c)
~~~~~~~~

And lastly, let's see what happens with `Future` that fails:

{lang="text"}
~~~~~~~~
scala> import scala.concurrent._
scala> import ExecutionContext.Implicits.global
scala> for {
         i <- Future.failed[Int](new Throwable)
         j <- Future { println("hello") ; 1 }
       } yield (i + j)
scala> Await.result(f, duration.Duration.Inf)

java.lang.Throwable
~~~~~~~~

The `Future` which prints to the terminal is never called because,
like `Option` and `Either`, the `for` comprehension short circuits.

Short circuiting for the unhappy path is a common and important theme.
`for` comprehensions cannot express resource cleanup: there is no way
to do `try` / `finally`. Cleanup needs to be a part of the `Monad` for
the container that we're flat-mapping over. This is a good thing, it
puts a clear ownership of responsibility for unexpected errors onto
the `Monad`, not the business logic.

## TODO Limitations

This section will gather examples of things that are easy to do with
typical scala code, but require some mental summersaults with monads,
e.g.

{lang="text"}
~~~~~~~~
val foo = sideEffectThingA
if (foo.isDefined) foo
else sideEffectThingB
~~~~~~~~

needs to be

{lang="text"}
~~~~~~~~
for {
  foo <- IO { sideEffectThingA }
  res <- if (foo.isDefined) IO.pure(foo) else IO { sideEffectThingB }
} yield res
~~~~~~~~

## TODO Monad Transformers

# TODO Implicits

Perhaps need a refresher on how implicits work.

# TODO Example

Just the high level concepts. Ask the reader to suspend their belief
of `@free` and we'll explain what it's doing later, plus the algebraic
mixing.

And an `Id` based test to show that we can really write business logic
tests without a real implementation.

An architect's dream: you can focus on algebras, business logic and
functional requirements, and delegate the implementations to your
teams.

# TODO Pure business logic

(the cross-over from previous section is not yet clear)

We can define things that are like Java =interface=s, but with the
container and its implementation abstracted away, called an Algebra.

We can write all our business logic solely by combining these
algebras. If you ever want to call some code that can throw an
exception or speaks to the outside world, wrap it in an algebra so it
can be abstracted.

Everything can now be mocked, and we can write tests just of the
business logic.

Include some thoughts from [Beginner Friendly Tour](http://degoes.net/articles/easy-monads)

# RESEARCH Parallel work

Generating the initial state and <https://github.com/fommil/drone-dynamic-agents/issues/6>

Might require a moment to explain `FreeApplicative` (I'd rather not get into details yet).

# TODO Reality Check

-   solved initial abstraction problem
-   clean way to write logic and divide labour
-   easier to write maintainable and testable code

Three steps forward but one step back: performance.

High level overview of what `@free` and `@module` is doing, and the
concept of trampolining. For a detailed explanation of free style and
the cats free monad implementation, see the appendix.

## RESEARCH perf numbers

# TODO Typeclasses

look into the oauth / google / drone algebras as examples.

how cats uses typeclasses, e.g. to provide the `flatMap` on the free
monad and `|+|` on applicatives.

Discourage hierarchies except for ADTs

# TODO Cats

## RESEARCH typeclasses

Foldable being imminently more interesting than the others.

Traversable will need to be discussed, seems to come up a lot.

## RESEARCH data types

Not really sure what to say here.

# TODO Spotting patterns, refactoring

Note that some of our algebras are actually common things and can be
rewritten: reader / writer / state / error / indexed monad. It's ok
that this is a step you can do later.

These are called Effects.

# CODE FS2 Streams

The basics, and covering the Effect, which can be our free monad.

Why streams are so awesome. I'd like a simple example here of reading
from a huge data source, doing parallel work and then writing out in
order to a (slower) device to demonstrate backpressure and constant
memory overhead. Maybe compare this vs hand rolled and akka streams
for a perf test?

Rewrite our business logic to be streaming, convert our GET api into a
`Stream` by polling.

# TODO interpreters

Show that although interpreters can be as messy as you like, you can
continue to write them as a pure core with side effects pushed to the
outside.

# TODO type refinement

instead of needing those `error` calls in the first place, just don't
allow them to happen at your layer if you can get away with it.

Protect yourself from mistyping

# RESEARCH Optics

not sure what the relevance to this project would be yet.


