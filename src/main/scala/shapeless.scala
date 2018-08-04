// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package shapes

import scalaz.{ Coproduct => _, :+: => _, _ }, Scalaz._
import shapeless._

trait DerivedEqual[A] extends Equal[A]
object DerivedEqual {
  def gen[A, R: DerivedEqual](implicit G: Generic.Aux[A, R]): Equal[A] =
    (a1, a2) => quick(a1, a2) || Equal[R].equal(G.to(a1), G.to(a2))

  implicit def hcons[H: Equal, T <: HList: DerivedEqual]: DerivedEqual[H :: T] =
    (h1, h2) =>
      (quick(h1.head, h2.head) || Equal[H].equal(h1.head, h2.head)) &&
        Equal[T].equal(h1.tail, h2.tail)

  implicit val hnil: DerivedEqual[HNil.type] = (_, _) => true

  implicit def ccons[H: Equal, T <: Coproduct: DerivedEqual]
    : DerivedEqual[H :+: T] = {
    case (Inl(c1), Inl(c2)) => quick(c1, c2) || Equal[H].equal(c1, c2)
    case (Inr(c1), Inr(c2)) => Equal[T].equal(c1, c2)
    case _                  => false
  }

  implicit val cnil: DerivedEqual[CNil] = (_, _) => sys.error("impossible")

  @inline private final def quick(a: Any, b: Any): Boolean =
    a.asInstanceOf[AnyRef].eq(b.asInstanceOf[AnyRef])
}

@deriving(Show)
sealed trait Foo { def widen: Foo = this }
final case class Bar(s: String)          extends Foo
final case class Faz(b: Boolean, i: Int) extends Foo
final case object Baz extends Foo {
  implicit val equal: Equal[Baz.type] = DerivedEqual.gen //[Baz.type, HNil.type]
}

object Foo {
  implicit val equal: Equal[Foo] = DerivedEqual.gen
}
object Bar {
  implicit val equal: Equal[Bar] = DerivedEqual.gen
}
object Faz {
  implicit val equal: Equal[Faz] = DerivedEqual.gen
}

object Test extends App {
  Baz.widen.assert_===(Baz)
}
