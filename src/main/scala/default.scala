// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package default

import simulacrum._
import scalaz._, Scalaz._
import eu.timepit.refined.refineV
import eu.timepit.refined.api._
import eu.timepit.refined.collection._
import Isomorphism._

@typeclass trait Default[A] {
  def default: String \/ A
}
object Default {
  def instance[A](d: =>String \/ A): Default[A] = new Default[A] {
    def default: String \/ A = d
  }

  implicit val long: Default[Long]       = instance(0L.right)
  implicit val string: Default[String]   = instance("".right)
  implicit val boolean: Default[Boolean] = instance(false.right)

  // implicit val functor: Functor[Default] = new Functor[Default] {
  //   def map[A, B](fa: Default[A])(f: A => B): Default[B] =
  //     instance(fa.default.map(f))
  // }

  // implicit val monaderr: MonadError[Default, String] =
  //   new MonadError[Default, String] {
  //     def point[A](a: =>A): Default[A] =
  //       instance(a.right)
  //     def bind[A, B](fa: Default[A])(f: A => Default[B]): Default[B] =
  //       instance((fa >>= f).default)
  //     def handleError[A](fa: Default[A])(f: String => Default[A]): Default[A] =
  //       instance(fa.default.handleError(e => f(e).default))
  //     def raiseError[A](e: String): Default[A] =
  //       instance(e.left)
  //   }

  // private type Sig[a] = Unit => String \/ a
  // private val iso = Kleisli.iso(
  //   λ[Sig ~> Default](s => instance(s(()))),
  //   λ[Default ~> Sig](d => _ => d.default)
  // )
  //implicit val monaderr: MonadError[Default, String] = MonadError.fromIso(iso)

  private type K[a] = Kleisli[String \/ ?, Unit, a]
  implicit val monad: MonadError[Default, String] with Alt[Default] =
    new IsomorphismMonadError[Default, K, String] with Alt[Default] {
      type Sig[a] = Unit => String \/ a
      override val G: MonadError[K, String] = MonadError[K, String]
      override val iso: Default <~> K = Kleisli.iso(
        λ[Sig ~> Default](s => instance(s(()))),
        λ[Default ~> Sig](d => _ => d.default)
      )

      def alt[A](a1: =>Default[A], a2: =>Default[A]): Default[A] =
        instance(a1.default)
    }

  implicit def refined[A: Default, P](
    implicit V: Validate[A, P]
  ): Default[A Refined P] =
    Default[A].emap(refineV[P](_).disjunction)

  implicit val int: Default[Int] = Default[Long].emap {
    case n if (Int.MinValue <= n && n <= Int.MaxValue) => n.toInt.right
    case big                                           => big.toString.left
  }
}

@xderiving(Equal, Default, Semigroup)
final case class Foo(s: String)
// object Foo {
//   implicit val equal: Equal[Foo]         = Equal[String].xmap(Foo(_), _.s)
//   implicit val default: Default[Foo]     = Default[String].xmap(Foo(_), _.s)
//   implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
// }

final case class Bar(s: String, i: Int)
object Bar {
  val iso: Bar <=> (String, Int) = IsoSet(b => (b.s, b.i), t => Bar(t._1, t._2))
  //implicit val equal: Equal[Bar] = Equal.fromIso(iso)

  implicit val equal: Equal[Bar] =
    Divisible[Equal].divide2(Equal[String], Equal[Int])(b => (b.s, b.i))
  implicit val default: Default[Bar] =
    Applicative[Default].apply2(Default[String], Default[Int])(Bar(_, _))

  // implicit val equal: Equal[Bar] =
  //   Divisible[Equal].deriving2(b => (b.s, b.i))
  // implicit val default: Default[Bar] =
  //   Applicative[Default].applying2(Bar(_, _))

}

object orphans {
  // breaks typeclass coherence for everything above Divisible
  implicit val _decidable_equal: Decidable[Equal] = new Decidable[Equal] {
    override def divide[A1, A2, Z](a1: Equal[A1], a2: Equal[A2])(
      f: Z => (A1, A2)
    ): Equal[Z] = Equal.equal { (z1, z2) =>
      val (s1, s2) = f(z1)
      val (t1, t2) = f(z2)
      ((s1.asInstanceOf[AnyRef].eq(t1.asInstanceOf[AnyRef])) || a1
        .equal(s1, t1)) &&
      ((s2.asInstanceOf[AnyRef].eq(t2.asInstanceOf[AnyRef])) || a2
        .equal(s2, t2))
    }
    override def conquer[A]: Equal[A] = Equal.equal((_, _) => true)

    override def choose2[Z, A1, A2](a1: =>Equal[A1], a2: =>Equal[A2])(
      f: Z => A1 \/ A2
    ): Equal[Z] = Equal.equal { (z1, z2) =>
      (f(z1), f(z2)) match {
        case (-\/(s), -\/(t)) =>
          (s.asInstanceOf[AnyRef].eq(t.asInstanceOf[AnyRef])) || a1.equal(s, t)
        case (\/-(s), \/-(t)) =>
          (s.asInstanceOf[AnyRef].eq(t.asInstanceOf[AnyRef])) || a2.equal(s, t)
        case _ => false
      }
    }
  }
}
import orphans._

sealed abstract class Tweedle {
  def widen: Tweedle = this
}
final case class Dee(s: String, i: Int) extends Tweedle
final case class Dum(i: Int, s: String) extends Tweedle

object Tweedle {
  private def f(e: Dee \/ Dum): Tweedle = e.merge
  private def g(t: Tweedle): Dee \/ Dum = t match {
    case p @ Dee(_, _) => -\/(p)
    case p @ Dum(_, _) => \/-(p)
  }

  // implicit val equal: Equal[Tweedle] =
  //   Decidable[Equal].choose2(Equal[Dee], Equal[Dum])(g)
  // implicit val default: Default[Tweedle] =
  //   Alt[Default].altly2(Default[Dee], Default[Dum])(f)

  implicit val equal: Equal[Tweedle] =
    InvariantAlt[Equal].xcoproduct2(Equal[Dee], Equal[Dum])(f, g)
  implicit val default: Default[Tweedle] =
    InvariantAlt[Default].xcoproduct2(Default[Dee], Default[Dum])(f, g)
}
object Dee {
  private val f: (String, Int) => Dee = Dee(_, _)
  private val g: Dee => (String, Int) = d => (d.s, d.i)

  // implicit val equal: Equal[Dee] =
  //   Divisible[Equal].divide2(Equal[String], Equal[Int])(g)
  // implicit val default: Default[Dee] =
  //   Alt[Default].apply2(Default[String], Default[Int])(f)
  implicit val equal: Equal[Dee] =
    InvariantApplicative[Equal].xproduct2(Equal[String], Equal[Int])(f, g)
  implicit val default: Default[Dee] =
    InvariantApplicative[Default].xproduct2(Default[String], Default[Int])(f, g)
}
object Dum {
  private val f: (Int, String) => Dum = Dum(_, _)
  private val g: Dum => (Int, String) = d => (d.i, d.s)

  // implicit val equal: Equal[Dum] =
  //   Divisible[Equal].divide2(Equal[Int], Equal[String])(g)
  // implicit val default: Default[Dum] =
  //   Alt[Default].apply2(Default[Int], Default[String])(f)
  implicit val equal: Equal[Dum] =
    InvariantApplicative[Equal].xproduct2(Equal[Int], Equal[String])(f, g)
  implicit val default: Default[Dum] =
    InvariantApplicative[Default].xproduct2(Default[Int], Default[String])(f, g)

}

object Demo extends App {

  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].emap(refineV[NonEmpty](_).disjunction)

  implicitly[Equal[(String, Int)]]

  println(Dee("hello", 1).widen === Dum(1, "world").widen)

  println(Default[Tweedle].default)
}
