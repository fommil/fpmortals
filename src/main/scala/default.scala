// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package default

import simulacrum._
import scalaz._, Scalaz._

@typeclass trait Default[A] {
  def default: Maybe[A]
}
object Default {
  def instance[A](d: =>Maybe[A]): Default[A] = new Default[A] {
    def default: Maybe[A] = d
  }

  implicit val int: Default[Int]         = instance(0.just)
  implicit val string: Default[String]   = instance("".just)
  implicit val boolean: Default[Boolean] = instance(false.just)

  implicit val functor: Functor[Default] = new Functor[Default] {
    def map[A, B](fa: Default[A])(f: A => B): Default[B] =
      instance(fa.default.map(f))
  }
}

@xderiving(Equal, Default, Semigroup)
final case class Foo(s: String)
// object Foo {
//   implicit val equal: Equal[Foo]         = Equal[String].xmap(Foo(_), _.s)
//   implicit val default: Default[Foo]     = Default[String].xmap(Foo(_), _.s)
//   implicit val semigroup: Semigroup[Foo] = Semigroup[String].xmap(Foo(_), _.s)
// }
