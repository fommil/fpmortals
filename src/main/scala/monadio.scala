// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package monadio

import scala.io.StdIn

import scalaz._, Scalaz._

final class IO[A] private (val interpret: () => A)
object IO {
  def apply[A](a: =>A): IO[A] = new IO(() => a)

  def fail[A](t: Throwable): IO[A] = IO(throw t)

  implicit val Monad: Monad[IO] =
    new Monad[IO] {
      def point[A](a: =>A): IO[A] = IO(a)
      def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] =
        IO(f(fa.interpret()).interpret())

      override def map[A, B](fa: IO[A])(f: A => B): IO[B] =
        IO(f(fa.interpret()))
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

    Apply[IO].forever(hello).interpret()

  }
}
