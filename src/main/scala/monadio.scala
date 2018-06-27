// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package monadio

import scala.io.StdIn
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration

import scalaz._, Scalaz._
import Tags.Parallel

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

  type Par[a] = IO[a] @@ Parallel
  implicit val ParApplicative: Applicative[Par] = new Applicative[Par] {

    def point[A](a: =>A): Par[A] = Tag(IO(a))

    override def map[A, B](fa: Par[A])(f: A => B): Par[B] =
      Tag(IO(f(Tag.unwrap(fa).interpret())))

    override def ap[A, B](fa: =>Par[A])(f: =>Par[A => B]): Par[B] =
      apply2(fa, f)((a, abc) => abc(a))

    override def apply2[A, B, C](fa: =>Par[A], fb: =>Par[B])(
      f: (A, B) => C
    ): Par[C] =
      Tag(
        IO {
          import ExecutionContext.Implicits._

          val a_ = Future { Tag.unwrap(fa).interpret() }
          val b  = Tag.unwrap(fb).interpret()
          val a  = Await.result(a_, Duration.Inf)

          f(a, b)
        }
      )
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
