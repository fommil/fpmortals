

# For Comprehensions

Scala's `for` comprehension is heavily used in FP --- it is the ideal
abstraction to write pure procedural code. But most Scala developers
only use `for` to loop over collections and are not aware of its full
potential.

In this chapter, we're going to visit the principles of `for` and how
cats can help us to write cleaner code with the standard library.

Scala's `for` is just a simple rewrite rule that doesn't have any
contextual information. The easiest way to see what a `for`
comprehension is doing is to use the `show` and `reify` feature in the
REPL to print out what your code looks like after type inference
(alternatively, invoke the compiler with the `-Xprint:typer` flag):

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

There's a lot of noise due to additional desugarings that you can
ignore (e.g. `+` is rewritten `$plus`). The basic rule of thumb is
that every `<-` (generator) is a nested `flatMap` call, with the final
generator being a `map`, containing the `yield`.

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

The compiler thinks it would be best to `map` over the `b` to
introduce the `val ij` in a tuple that can be flat-mapped along with
the `j`, then the final `map` for the code in the `yield`.

A> `val` doesn't have to assign to a single value, it can be anything
A> that works as a `case` in a pattern match. Be careful that you don't
A> miss any cases or you'll get a runtime exception (a *totality*
A> failure):
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

There is one more rewrite rule, it's possible to put `if` statements
after a generator:

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

generating a call to `withFilter`.[^fn_filter]

## TODO Keep it alive

<https://github.com/fommil/drone-dynamic-agents/issues/14>

## TODO Unhappy path

The `yield` is only called when `i,j,k` are all defined. If any of
`a,b,c` are `None`, the entire expression is `None`.

A> How many times have you seen a function that takes `Option` parameters
A> but actually requires them all to exist? I hate seeing this:
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
A> Although this is better than throwing an exception, it is clunky and
A> bad style. If a function really does require every input then it
A> should make its requirement explicit, pushing the responsibility for
A> dealing with optional parameters to its caller.

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


[^fn_filter]:  Older versions of scala
called `filter`, but since `filter` in the collections library creates
new collections, and unnecessary heap objects, `withFilter` was
introduced for performance.
