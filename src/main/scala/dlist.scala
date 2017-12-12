// Copyright: 2017 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package dlist

import scalaz.IList

final case class DList[A](f: IList[A] => IList[A]) {
  def toIList: IList[A] = f(IList.empty)

  def ++(as: DList[A]): DList[A] = DList(xs => f(as.f(xs)))
}
object DList {
  def fromIList[A](as: IList[A]): DList[A] =
    DList(
      xs => {
        // convince yourself of the work that occurs
        // println(s"$as ::: $xs")
        as ::: xs
      }
    )
}

object Main extends App {

  val al = IList(1, 2, 3)
  val bl = IList(4, 5, 6)
  val cl = IList(7, 8, 9)
  val dl = IList(10, 11, 12)
  val el = IList(13, 14, 15)
  val fl = IList(16, 17, 18)
  val gl = IList(19, 20, 21)

  println("IList = " + (((al ::: bl) ::: (cl ::: dl)) ::: (el ::: (fl ::: gl))))

  val ad = DList.fromIList(al)
  val bd = DList.fromIList(bl)
  val cd = DList.fromIList(cl)
  val dd = DList.fromIList(dl)
  val ed = DList.fromIList(el)
  val fd = DList.fromIList(fl)
  val gd = DList.fromIList(gl)

  println(
    "DList = " + (((ad ++ bd) ++ (cd ++ dd)) ++ (ed ++ (fd ++ gd))).toIList
  )

}
