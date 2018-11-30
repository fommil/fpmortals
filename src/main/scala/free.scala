// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package free

//import scala.annotation.tailrec
import scalaz.{ -\/, \/, \/-, Applicative, BindRec, Monad }
import Free._

import scala.language.higherKinds

sealed abstract class Free[S[_], A]
object Free {
  final case class Return[S[_], A](a: A)     extends Free[S, A]
  final case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
  final case class Gosub[S[_], A0, B](
    a: Free[S, A0],
    f: A0 => Free[S, B]
  ) extends Free[S, B] { type A = A0 }

  type Trampoline[A] = Free[() => ?, A]
  implicit val trampoline: Monad[Trampoline] with BindRec[Trampoline] =
    new Monad[Trampoline] with BindRec[Trampoline] {
      def point[A](a: =>A): Trampoline[A] = Return(a)
      def bind[A, B](fa: Trampoline[A])(f: A => Trampoline[B]): Trampoline[B] =
        Gosub(fa, f)
      def tailrecM[A, B](f: A => Trampoline[A \/ B])(a: A): Trampoline[B] =
        bind(f(a)) {
          case -\/(a) => tailrecM(f)(a)
          case \/-(b) => point(b)
        }
    }

}
object Trampoline {
  import scalaz.syntax.monad._

  def done[A](a: A): Trampoline[A]    = Return(a)
  def delay[A](a: =>A): Trampoline[A] = suspend(done(a))

  private val unit: Trampoline[Unit]                = Suspend(() => done(()))
  def suspend[A](a: =>Trampoline[A]): Trampoline[A] = unit >> a
}
