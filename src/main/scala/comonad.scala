// Copyright: 2017 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/
package comonad

import scalaz._
import Scalaz._
import Maybe.{ Empty, Just }

// http://blog.sigfpe.com/2006/12/evaluating-cellular-automata-is.html
final case class Hood[A](lefts: IList[A], focus: A, rights: IList[A])

object Hood {
  implicit class Ops[A](hood: Hood[A]) {
    def toList: IList[A] = hood.lefts.reverse ::: hood.focus :: hood.rights

    def previous: Maybe[Hood[A]] = hood.lefts match {
      case INil() => Empty()
      case ICons(head, tail) =>
        Just(Hood(tail, head, hood.focus :: hood.rights))
    }
    def next: Maybe[Hood[A]] = hood.rights match {
      case INil() => Empty()
      case ICons(head, tail) =>
        Just(Hood(hood.focus :: hood.lefts, head, tail))
    }

    def iterate(f: Hood[A] => Maybe[Hood[A]]): IList[Hood[A]] =
      f(hood) match {
        case Empty() => INil()
        case Just(r) => ICons(r, r.iterate(f))
      }
    def positions: Hood[Hood[A]] = {
      val left  = hood.iterate(_.previous)
      val right = hood.iterate(_.next)
      Hood(left, hood, right)
    }
  }

  implicit val comonad: Comonad[Hood] = new Comonad[Hood] {
    def map[A, B](fa: Hood[A])(f: A => B): Hood[B] =
      Hood(fa.lefts.map(f), f(fa.focus), fa.rights.map(f))

    // uncomment for performance
    //override def cojoin[A](fa: Hood[A]): Hood[Hood[A]] = fa.positions

    def cobind[A, B](fa: Hood[A])(f: Hood[A] => B): Hood[B] =
      fa.positions.map(f)
    def copoint[A](fa: Hood[A]): A = fa.focus
  }
}

object example {
  def main(args: Array[String]): Unit = {

    val middle = Hood(IList(4, 3, 2, 1), 5, IList(6, 7, 8, 9))

    println(middle.cojoin)

    /*
     Hood(
       [Hood([3,2,1],4,[5,6,7,8,9]),
        Hood([2,1],3,[4,5,6,7,8,9]),
        Hood([1],2,[3,4,5,6,7,8,9]),
        Hood([],1,[2,3,4,5,6,7,8,9])],
       Hood([4,3,2,1],5,[6,7,8,9]),
       [Hood([5,4,3,2,1],6,[7,8,9]),
        Hood([6,5,4,3,2,1],7,[8,9]),
        Hood([7,6,5,4,3,2,1],8,[9]),
        Hood([8,7,6,5,4,3,2,1],9,[])])
   */
  }
}
