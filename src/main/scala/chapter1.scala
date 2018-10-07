// Copyright: 2017 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/
package chapter1

import scala.io.StdIn
import scala.concurrent._
import scala.concurrent.duration.Duration

trait Terminal[C[_]] {
  def read: C[String]
  def write(t: String): C[Unit]
}

object `package` {
  type Now[X] = X
}

object TerminalSync extends Terminal[Now] {
  def read: String           = StdIn.readLine
  def write(t: String): Unit = println(t)
}

class TerminalAsync(implicit EC: ExecutionContext) extends Terminal[Future] {
  // you could potentially implement these with the nio non-blocking
  // API, this implementation eats up a Thread for each operation.
  def read: Future[String]           = Future { StdIn.readLine }
  def write(t: String): Future[Unit] = Future { println(t) }
}

trait Execution[C[_]] {
  def doAndThen[A, B](c: C[A])(f: A => C[B]): C[B]
  def create[B](b: B): C[B]
}
object Execution {
  implicit class Ops[A, C[_]](c: C[A]) {
    def flatMap[B](f: A => C[B])(implicit e: Execution[C]): C[B] =
      e.doAndThen(c)(f)
    def map[B](f: A => B)(implicit e: Execution[C]): C[B] =
      e.doAndThen(c)(f andThen e.create)
  }

  implicit val now: Execution[Now] = new Execution[Now] {
    def doAndThen[A, B](c: A)(f: A => B): B = f(c)
    def create[B](b: B): B                  = b
  }

  implicit def future(implicit EC: ExecutionContext): Execution[Future] =
    new Execution[Future] {
      def doAndThen[A, B](c: Future[A])(f: A => Future[B]): Future[B] =
        c.flatMap(f)
      def create[B](b: B): Future[B] = Future.successful(b)
    }

  implicit val deferred: Execution[IO] = new Execution[IO] {
    def doAndThen[A, B](c: IO[A])(f: A => IO[B]): IO[B] = c.flatMap(f)
    def create[B](b: B): IO[B]                          = IO(b)
  }
}

object Runner {
  import Execution.Ops
  import ExecutionContext.Implicits._

  def echo[C[_]](implicit t: Terminal[C], e: Execution[C]): C[String] =
    for {
      in <- t.read
      _  <- t.write(in)
    } yield in

  implicit val now: Terminal[Now]       = TerminalSync
  implicit val future: Terminal[Future] = new TerminalAsync
  implicit val io: Terminal[IO]         = TerminalIO

  def main(args: Array[String]): Unit = {
    // interpret for Now (impure)
    echo[Now]: Now[String]

    // interpret for Future (impure)
    val running: Future[String] = echo[Future]
    Await.result(running, Duration.Inf)

    // define using IO
    val delayed: IO[String] = echo[IO]
    // interpret, impure, end of the world
    delayed.interpret()
  }
}

final class IO[A] private (val interpret: () => A) {
  def map[B](f: A => B): IO[B]         = IO(f(interpret()))
  def flatMap[B](f: A => IO[B]): IO[B] = IO(f(interpret()).interpret())
}
object IO {
  def apply[A](a: =>A): IO[A] = new IO(() => a)
}

object TerminalIO extends Terminal[IO] {
  def read: IO[String]           = IO { StdIn.readLine }
  def write(t: String): IO[Unit] = IO { println(t) }
}
