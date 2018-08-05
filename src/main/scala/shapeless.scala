// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package shapes

import scalaz.{ Coproduct => _, :+: => _, _ }, Scalaz._
import shapeless._

sealed trait DerivedEqual[A] extends Equal[A]
object DerivedEqual {
  def gen[A, R](
    implicit G: Generic.Aux[A, R],
    R: Cached[Strict[DerivedEqual[R]]]
  ): Equal[A] = new Equal[A] {
    def equal(a1: A, a2: A) =
      quick(a1, a2) || R.value.value.equal(G.to(a1), G.to(a2))
  }

  implicit def hcons[H, T <: HList](
    implicit H: Lazy[Equal[H]],
    T: DerivedEqual[T]
  ): DerivedEqual[H :: T] = new DerivedEqual[H :: T] {
    def equal(ht1: H :: T, ht2: H :: T) =
      (quick(ht1.head, ht2.head) || H.value.equal(ht1.head, ht2.head)) &&
        T.equal(ht1.tail, ht2.tail)
  }

  implicit val hnil: DerivedEqual[HNil] = new DerivedEqual[HNil] {
    def equal(@unused h1: HNil, @unused h2: HNil) = true
  }

  implicit def ccons[H, T <: Coproduct](
    implicit H: Lazy[Equal[H]],
    T: DerivedEqual[T]
  ): DerivedEqual[H :+: T] = new DerivedEqual[H :+: T] {
    def equal(ht1: H :+: T, ht2: H :+: T) = (ht1, ht2) match {
      case (Inl(c1), Inl(c2)) => quick(c1, c2) || H.value.equal(c1, c2)
      case (Inr(c1), Inr(c2)) => T.equal(c1, c2)
      case _                  => false
    }
  }

  implicit val cnil: DerivedEqual[CNil] = new DerivedEqual[CNil] {
    def equal(@unused c1: CNil, @unused c2: CNil) = sys.error("impossible")
  }

  @inline private final def quick(a: Any, b: Any): Boolean =
    a.asInstanceOf[AnyRef].eq(b.asInstanceOf[AnyRef])
}

trait Default[A] {
  def default: String \/ A
}
object Default {
  @inline def apply[A](implicit A: Default[A]): Default[A] = A
  def instance[A](d: =>String \/ A): Default[A] = new Default[A] {
    def default: String \/ A = d
  }

  implicit val int: Default[Int]         = instance(0.right)
  implicit val string: Default[String]   = instance("".right)
  implicit val boolean: Default[Boolean] = instance(false.right)
}

sealed trait DerivedDefault[A] extends Default[A]
object DerivedDefault {
  def gen[A, R](
    implicit G: Generic.Aux[A, R],
    R: Cached[Strict[DerivedDefault[R]]]
  ): Default[A] = new Default[A] {
    def default = R.value.value.default.map(G.from)
  }

  implicit def hcons[H, T <: HList](
    implicit H: Lazy[Default[H]],
    T: DerivedDefault[T]
  ): DerivedDefault[H :: T] = new DerivedDefault[H :: T] {
    def default =
      for {
        head <- H.value.default
        tail <- T.default
      } yield head :: tail
  }

  implicit val hnil: DerivedDefault[HNil] = new DerivedDefault[HNil] {
    def default = HNil.right
  }

  implicit def ccons[H, T <: Coproduct](
    implicit H: Lazy[Default[H]],
    T: DerivedDefault[T]
  ): DerivedDefault[H :+: T] = new DerivedDefault[H :+: T] {
    def default = H.value.default.map(Inl(_)).orElse(T.default.map(Inr(_)))
  }

  implicit val cnil: DerivedDefault[CNil] = new DerivedDefault[CNil] {
    def default = "not a valid coproduct".left
  }

}

@deriving(Show)
sealed abstract class Foo { def widen: Foo = this }
final case class Bar(s: String)          extends Foo
final case class Faz(b: Boolean, i: Int) extends Foo
final case object Baz extends Foo {
  implicit val equal: Equal[Baz.type]     = DerivedEqual.gen
  implicit val default: Default[Baz.type] = DerivedDefault.gen
}
object Bar {
  implicit val equal: Equal[Bar]     = DerivedEqual.gen
  implicit val default: Default[Bar] = DerivedDefault.gen
}
object Faz {
  implicit val equal: Equal[Faz]     = DerivedEqual.gen
  implicit val default: Default[Faz] = DerivedDefault.gen
}
object Foo {
  implicit val equal: Equal[Foo]     = DerivedEqual.gen
  implicit val default: Default[Foo] = DerivedDefault.gen
}

sealed trait ATree
final case class Leaf(value: String)               extends ATree
final case class Branch(left: ATree, right: ATree) extends ATree

object ATree {
  implicit val equal: Equal[ATree] = DerivedEqual.gen
}
object Leaf {
  implicit val equal: Equal[Leaf] = DerivedEqual.gen
}
object Branch {
  implicit val equal: Equal[Branch] = DerivedEqual.gen
}

object Test extends App {
  //Baz.widen.assert_===(Baz)

  println(Default[Foo].default)

  val leaf1: Leaf    = Leaf("hello")
  val leaf2: Leaf    = Leaf("goodbye")
  val branch: Branch = Branch(leaf1, leaf2)
  val tree1: ATree   = Branch(leaf1, branch)
  val tree2: ATree   = Branch(leaf2, branch)

  assert(leaf1 === leaf1)
  assert(leaf2 === leaf2)
  assert(leaf1 /== leaf2)
  assert(tree1 === tree1)
  assert(tree1 /== tree2)

}
