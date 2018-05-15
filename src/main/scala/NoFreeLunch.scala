// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package nofreelunch

// Minimisation of issue encountered in drone-dynamic-agents' AlgebraSpec
//
// We have an `Orig` AST and an `Improved` AST. We want to write `Orig ~>
// Patched` where `Patched` is `Orig` plus `Improved`, wrapped with some
// `State`.
//
// The problem is that to do this, we must introduce a fake `Noop` element. A
// better solution would be to write `Orig ~> Free[Patched, ?]`. But I don't
// think it is possible to `State.sequence` or `Free.distribute`. Maybe it's
// possible for these specific types?

import scalaz._, Scalaz._

sealed abstract class Orig[A]
final case class Old(i: Int) extends Orig[Unit]

sealed abstract class Improved[A]
final case class New(a: Int, b: Int) extends Improved[Unit]
final case class Noop()              extends Improved[Unit]
// we want to remove Noop() from the Improved AST

object Main {

  type S            = Maybe[Int]
  type Extension[a] = State[S, Improved[a]]
  type Patched[a]   = Coproduct[Extension, Orig, a]

  // requires Noop :-(
  val hacky = λ[Orig ~> Patched] {
    case Old(i) =>
      val extension: Extension[Unit] = State {
        case Maybe.Just(s) => Maybe.empty   -> New(s, i)
        case Maybe.Empty() => Maybe.just(i) -> Noop()
      }
      Coproduct.leftc(extension)

    case other =>
      Coproduct.rightc(other)
  }

  // this is the signature we want to write
  val better = λ[Orig ~> Free[Patched, ?]] {
    case Old(i) =>
      val extension: State[S, Free[Improved, Unit]] = State {
        case Maybe.Just(s) => Maybe.empty   -> Free.liftF(New(s, i))
        case Maybe.Empty() => Maybe.just(i) -> Free.pure(())
      }
      ??? // :-(

    case other =>
      Free.liftF(Coproduct.right[Extension](other))
  }

}
