// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package nofreelunch

// minimisation of issue encountered in drone-dynamic-agents' AlgebraSpec

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

  // this is a hacky patch because it requires the Noop
  val patch = λ[Orig ~> Patched] {
    case Old(i) =>
      val extension: Extension[Unit] = State {
        case Maybe.Just(s) => Maybe.empty   -> New(s, i)
        case Maybe.Empty() => Maybe.just(i) -> Noop()
      }
      Coproduct.leftc(extension)

    case other =>
      Coproduct.rightc(other)
  }

  // this is the patch we want to write
  val better = λ[Orig ~> Free[Patched, ?]] {
    case Old(i) =>
      val extension: State[S, Free[Improved, Unit]] = State {
        case Maybe.Just(s) => Maybe.empty   -> Free.liftF(New(s, i))
        case Maybe.Empty() => Maybe.just(i) -> Free.pure(())
      }
      // but this requires sequencing State, or distributing Free... :-(
      ???

    case other =>
      Free.liftF(Coproduct.right[Extension](other))
  }

}

// ADT (not subtype) constructors...
object Old {
  def apply(i: Int): Orig[Unit] = new Old(i)
}
object New {
  def apply(a: Int, b: Int): Improved[Unit] = new New(a, b)
}
object Noop {
  def apply(): Improved[Unit] = new Noop()
}
