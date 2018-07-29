// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package default

import simulacrum._
import scalaz._, Scalaz._
import eu.timepit.refined.refineV
import eu.timepit.refined.api._
import eu.timepit.refined.collection._
import Isomorphism._

import iotaz._, TList._
import IotazHacks._

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

  // implicit val monad: MonadError[Default, String] =
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
  //implicit val monad: MonadError[Default, String] = MonadError.fromIso(iso)

  // private type K[a] = Kleisli[String \/ ?, Unit, a]
  // implicit val monad: MonadError[Default, String] with Alt[Default] =
  //   new IsomorphismMonadError[Default, K, String] with Alt[Default] {
  //     type Sig[a] = Unit => String \/ a
  //     override val G = MonadError[K, String]
  //     override val iso = Kleisli.iso(
  //       λ[Sig ~> Default](s => instance(s(()))),
  //       λ[Default ~> Sig](d => _ => d.default)
  //     )

  //     def alt[A](a1: =>Default[A], a2: =>Default[A]): Default[A] =
  //       instance(a1.default)
  //   }
  // implicit val deriving: Deriving[Default] = ExtendedInvariantAlt(monad)

  private type K[a] = Kleisli[String \/ ?, Unit, a]
  implicit val monad: MonadError[Default, String] with Altz[Default] =
    new IsomorphismMonadError[Default, K, String] with Altz[Default] {
      type Sig[a] = Unit => String \/ a
      override val G = MonadError[K, String]
      override val iso = Kleisli.iso(
        λ[Sig ~> Default](s => instance(s(()))),
        λ[Default ~> Sig](d => _ => d.default)
      )

      private val extract = λ[NameF ~> (String \/ ?)](a => a.value.default)
      def applyz[Z, A <: TList, TC <: TList](tcs: Prod[TC])(
        f: Prod[A] => Z
      )(
        implicit ev1: NameF ƒ A ↦ TC
      ): Default[Z] = instance(tcs.traverse(extract).map(f))

      private val always =
        λ[NameF ~> Maybe](a => a.value.default.toMaybe)
      def altlyz[Z, A <: TList, TC <: TList](tcs: Prod[TC])(
        f: Cop[A] => Z
      )(
        implicit ev1: NameF ƒ A ↦ TC
      ): Default[Z] = instance {
        tcs.coptraverse[A, NameF, Id](always).map(f).headMaybe \/> "not found"
      }

    }

  implicit def refined[A: Default, P](
    implicit V: Validate[A, P]
  ): Default[A Refined P] =
    Default[A].emap(refineV[P](_).disjunction)

  implicit val int: Default[Int] = Default[Long].emap {
    case n if (Int.MinValue <= n && n <= Int.MaxValue) => n.toInt.right
    case big                                           => s"$big does not fit into 32 bits".left
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
object Bar extends ((String, Int) => Bar) {
  //val iso: Bar <=> (String, Int) = IsoSet(b => (b.s, b.i), t => Bar(t._1, t._2))
  val iso: Bar <=> (String, Int) = IsoSet(unapply(_).get, tupled)

  implicit val equal: Equal[Bar] = Equal.fromIso(iso)

  // implicit val equal: Equal[Bar] = Divisible[Equal].divide2(Equal[String],
  //   Equal[Int])(b => (b.s, b.i))
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

@deriving(Equal, Show, Default)
sealed abstract class Darth {
  def widen: Darth = this
}
final case class Vader(s: String, i: Int) extends Darth
final case class Maul(i: Int, s: String)  extends Darth

// object Darth {
//   private type Repr   = Vader :: Maul :: TNil
//   private type Labels = String :: String :: TNil
//   private val VaderI = Cop.Inject[Vader, Cop[Repr]]
//   private val MaulI = Cop.Inject[Maul, Cop[Repr]]
//   private val iso = CopGen[Darth, Repr, Labels](
//     {
//       case d: Vader => VaderI.inj(d)
//       case d: Maul => MaulI.inj(d)
//     }, {
//       case VaderI(d) => d
//       case MaulI(d) => d
//     },
//     Prod("Vader", "Maul"),
//     "Darth"
//   )

//   private def f(e: Vader \/ Maul): Darth = e.merge
//   private def g(t: Darth): Vader \/ Maul = t match {
//     case p @ Vader(_, _) => -\/(p)
//     case p @ Maul(_, _) => \/-(p)
//   }

//   // implicit val equal: Equal[Darth] =
//   //   Decidable[Equal].choose2(Equal[Vader], Equal[Maul])(g)
//   // implicit val default: Default[Darth] =
//   //   Alt[Default].altly2(Default[Vader], Default[Maul])(f)
//   // implicit val equal: Equal[Darth] =
//   //   InvariantAlt[Equal].xcoproduct2(Equal[Vader], Equal[Maul])(f, g)
//   // implicit val default: Default[Darth] =
//   //   InvariantAlt[Default].xcoproduct2(Default[Vader], Default[Maul])(f, g)

//   implicit val equal: Equal[Darth] =
//     Deriving[Equal].xcoproductz(
//       Prod(Need(Equal[Vader]), Need(Equal[Maul])),
//       iso.labels,
//       iso.name
//     )(iso.to, iso.from)

//   implicit val default: Default[Darth] =
//     Deriving[Default].xcoproductz(
//       Prod(Need(Default[Vader]), Need(Default[Maul])),
//       iso.labels,
//       iso.name
//     )(iso.to, iso.from)

// }
// object Vader {
//   private type Repr   = String :: Int :: TNil
//   private type Labels = String :: String :: TNil
//   private val iso = ProdGen[Vader, Repr, Labels](
//     d => Prod(d.s, d.i),
//     p => Vader(p.head, p.tail.head),
//     Prod("s", "i"),
//     "Vader"
//   )

//   private val f: (String, Int) => Vader = Vader(_, _)
//   private val g: Vader => (String, Int) = d => (d.s, d.i)

//   // implicit val equal: Equal[Vader] =
//   //   Divisible[Equal].divide2(Equal[String], Equal[Int])(g)
//   // implicit val default: Default[Vader] =
//   //   Alt[Default].apply2(Default[String], Default[Int])(f)
//   // implicit val equal: Equal[Vader] =
//   //   InvariantApplicative[Equal].xproduct2(Equal[String], Equal[Int])(f, g)
//   //implicit val default: Default[Vader] =
//   //   InvariantApplicative[Default].xproduct2(Default[String], Default[Int])(f, g)

//   implicit val equal: Equal[Vader] =
//     Deriving[Equal].xproductz(
//       Prod(Need(Equal[String]), Need(Equal[Int])),
//       iso.labels,
//       iso.name
//     )(iso.to, iso.from)

//   implicit val default: Default[Vader] =
//     Deriving[Default].xproductz(
//       Prod(Need(Default[String]), Need(Default[Int])),
//       iso.labels,
//       iso.name
//     )(iso.to, iso.from)

// }
// object Maul {
//   private type Repr   = Int :: String :: TNil
//   private type Labels = String :: String :: TNil
//   private val iso = ProdGen[Maul, Repr, Labels](
//     d => Prod(d.i, d.s),
//     p => Maul(p.head, p.tail.head),
//     Prod("i", "s"),
//     "Maul"
//   )

//   private val f: (Int, String) => Maul = Maul(_, _)
//   private val g: Maul => (Int, String) = d => (d.i, d.s)

//   // implicit val equal: Equal[Maul] =
//   //   Divisible[Equal].divide2(Equal[Int], Equal[String])(g)
//   // implicit val default: Default[Maul] =
//   //   Alt[Default].apply2(Default[Int], Default[String])(f)
//   // implicit val equal: Equal[Maul] =
//   //   InvariantApplicative[Equal].xproduct2(Equal[Int], Equal[String])(f, g)
//   // implicit val default: Default[Maul] =
//   //   InvariantApplicative[Default].xproduct2(Default[Int], Default[String])(f, g)

//   implicit val equal: Equal[Maul] =
//     Deriving[Equal].xproductz(
//       Prod(Need(Equal[Int]), Need(Equal[String])),
//       iso.labels,
//       iso.name
//     )(iso.to, iso.from)

//   implicit val default: Default[Maul] =
//     Deriving[Default].xproductz(
//       Prod(Need(Default[Int]), Need(Default[String])),
//       iso.labels,
//       iso.name
//     )(iso.to, iso.from)

// }

object Demo extends App {

  implicit val nes: Default[String Refined NonEmpty] =
    Default[String].emap(refineV[NonEmpty](_).disjunction)

  implicitly[Equal[(String, Int)]]

  println(Vader("hello", 1).widen === Maul(1, "world").widen)

  println(Default[Darth].default)

  type DarthT = Vader :: Maul :: TNil
  type VaderT = String :: Int :: TNil
  type MaulT  = Int :: String :: TNil

  val vader: Prod[VaderT] = Prod("hello", 1)
  val maul: Prod[MaulT]   = Prod(1, "hello")

  //val VaderI = Cop.Inject[Vader, Cop[DarthT]]
  //val MaulI = Cop.Inject[Maul, Cop[DarthT]]
  //val darth: Cop[DarthT] = VaderI.inj(Vader("hello", 1))

}

object IotazHacks {
  implicit class ProdExtras[A, T <: TList](prod: Prod[A :: T]) {
    def head: A       = prod.values(0).asInstanceOf[A]
    def tail: Prod[T] = Prod.unsafeApply(prod.values.drop(1))
  }
}
