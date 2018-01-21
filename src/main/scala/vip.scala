// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package vip

import scalaz._, Scalaz._

final case class Vip[A] private (val peek: Maybe[A], xs: IList[A]) {
  def push(a: A)(implicit O: Order[A]): Vip[A] = peek match {
    case Maybe.Just(min) if a < min => Vip(a.just, min :: xs)
    case _                          => Vip(peek, a :: xs)
  }

  def pop(implicit O: Order[A]): Maybe[(A, Vip[A])] = peek strengthR reorder
  private def reorder(implicit O: Order[A]): Vip[A] = xs.sorted match {
    case INil()           => Vip(Maybe.empty, IList.empty)
    case ICons(min, rest) => Vip(min.just, rest)
  }
}
object Vip {
  def fromList[A: Order](xs: IList[A]): Vip[A] = Vip(Maybe.empty, xs).reorder
}
