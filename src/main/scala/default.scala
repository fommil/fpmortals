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

  private type Sig[a] = Unit => String \/ a
  private val iso = Kleisli.iso(
    λ[Sig ~> Default](s => instance(s(()))),
    λ[Default ~> Sig](d => _ => d.default)
  )

  implicit val monaderr: MonadError[Default, String] = MonadError.fromIso(iso)

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

object Demo {

  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].emap(refineV[NonEmpty](_).disjunction)

  implicitly[Equal[(String, Int)]]

}
