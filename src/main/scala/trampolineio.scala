// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package trampoline

import scala.io.StdIn

import scalaz._, Scalaz._
import Free.Trampoline

final class IO[A](val tramp: Trampoline[A]) {
  def unsafePerformIO(): A = tramp.run
}
object IO {
  def apply[A](a: =>A): IO[A] = new IO(Trampoline.delay(a))

  implicit val Monad: Monad[IO] with BindRec[IO] =
    new Monad[IO] with BindRec[IO] {
      def point[A](a: =>A): IO[A] = IO(a)
      def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
        new IO(fa.tramp >>= (a => f(a).tramp))

      def tailrecM[A, B](f: A => IO[A \/ B])(a: A): IO[B] =
        bind(f(a)) {
          case -\/(a) => tailrecM(f)(a)
          case \/-(b) => point(b)
        }
    }
}

object Runner {
  import brokenfuture.Terminal
  import brokenfuture.Runner.echo

  implicit val TerminalIO: Terminal[IO] = new Terminal[IO] {
    def read: IO[String]           = IO { StdIn.readLine }
    def write(t: String): IO[Unit] = IO { println(t) }
  }

  val program: IO[String] = echo[IO]

  def main(args: Array[String]): Unit = {
    // program.interpret()

    val hello = IO { println("hello") }

    Apply[IO].forever(hello).unsafePerformIO()

  }
}
