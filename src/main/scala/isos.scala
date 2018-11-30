// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package isos

import scalaz._
import Scalaz._
import Isomorphism.{ <~>, IsoFunctorTemplate }

import scala.language.higherKinds

// this is just to check if the MonadErrorTest in scalaz 7.2 is cleaner with
// partial unification turned on.

object KleisliIso {
  def iso[D[_], I, O[_]](
    instance: λ[a => (I => O[a])] ~> D,
    decode: D ~> λ[a => (I => O[a])]
  ): D <~> Kleisli[O, I, ?] =
    new IsoFunctorTemplate[D, Kleisli[O, I, ?]] {
      def from[A](fa: Kleisli[O, I, A]): D[A] = instance(fa.run(_))
      def to[A](fa: D[A]): Kleisli[O, I, A]   = Kleisli[O, I, A](decode(fa))
    }
}

trait Decoder[A] {
  def decode(s: String): Int \/ A
}
object Decoder {
  @inline def apply[A](implicit A: Decoder[A]): Decoder[A] = A
  @inline def instance[A](f: String => Int \/ A): Decoder[A] = new Decoder[A] {
    override def decode(s: String): Int \/ A = f(s)
  }

  implicit val string: Decoder[String] = instance(_.right)

  private[this] type Decode[a] = String => Int \/ a
  val iso: Decoder <~> Kleisli[Int \/ ?, String, ?] = KleisliIso.iso(
    λ[Decode ~> Decoder](instance(_)),
    λ[Decoder ~> Decode](_.decode)
  )

  implicit val monad: MonadError[Decoder, Int] = MonadError.fromIso(iso)
}
