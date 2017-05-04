

# TODO Implicits

Perhaps need a refresher on how implicits work.

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

Maybe use this example? <https://gitter.im/typelevel/cats?at=5904a2e98bb56c2d11f53979>

{lang="text"}
~~~~~~~~
  @ class Lift[F[_]] {
      def $[A](fa: F[Option[A]]): OptionT[F,A] = OptionT(fa)
      def $[A](opt: Option[A])(implicit F: Applicative[F]): OptionT[F,A] = OptionT(F.pure(opt))
      def $[A](a: A)(implicit F: Applicative[F]): OptionT[F,A] = OptionT(F.pure(Some(a)))
    }
  defined class Lift
  @ def liftFrom[F[_]] = new Lift[F] {}
  defined function liftFrom
  @ val lift = liftFrom[List]
  lift: Lift[List] = $sess.cmd26$$anon$1@6cf3d7c8
  @ val prg = for {
      x <- lift $ 1
      y <- lift $ Option(2)
      z <- lift $ List(Some(3), Some(4))
    } yield x + y + z
  prg: OptionT[List, Int] = OptionT(List(Some(6), Some(7)))
~~~~~~~~

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


