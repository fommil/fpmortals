

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

Three steps forward but two steps back: performance, IDE support.

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

Use (impure) example of merging two deep configuration ADTs (scala
does not enforce purity so we can choose our own level)

Not enough to implement, must also pass the laws

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


