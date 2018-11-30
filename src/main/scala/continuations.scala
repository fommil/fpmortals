// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package continuations

import scalaz.{ ContT => _, IndexedContT => _, _ }
import scalaz.Scalaz._
import scalaz.effect.IO

import scala.language.higherKinds

// final case class ContT[F[_], B, A](_run: (A => F[B]) => F[B]) {
//   def run(f: A => F[B]): F[B] = _run(f)
// }
// we can't contramap over a ContT, we need to introduce a type parameter for that
final case class IndexedContT[F[_], C, B, A](_run: (A => F[B]) => F[C]) {
  def run(f: A => F[B]): F[C] = _run(f)
}
object IndexedContT {
  type ContT[f[_], b, a] = IndexedContT[f, b, b, a]

  implicit def monad[F[_], B]: Monad[ContT[F, B, ?]] =
    new Monad[ContT[F, B, ?]] {
      def point[A](a: =>A): ContT[F, B, A] = ContT(_(a))
      def bind[A, C](
        fa: ContT[F, B, A]
      )(f: A => ContT[F, B, C]): ContT[F, B, C] =
        ContT(c_fb => fa.run(a => f(a).run(c_fb)))
    }

  implicit def contravariant[F[_]: Functor, C, A]
    : Contravariant[IndexedContT[F, C, ?, A]] =
    new Contravariant[IndexedContT[F, C, ?, A]] {
      def contramap[Z, ZZ](
        fa: IndexedContT[F, C, Z, A]
      )(f: ZZ => Z): IndexedContT[F, C, ZZ, A] =
        IndexedContT(a_fc => fa.run(a => a_fc(a).map(f)))
    }

  object ops {
    final implicit class ContTOps[F[_], A](val self: F[A]) extends AnyVal {
      def cps[B](implicit F: Monad[F]): ContT[F, B, A] =
        ContT(a_fb => self >>= a_fb)
    }
  }
}
import IndexedContT.ContT
object ContT {
  def apply[F[_], B, A](_run: (A => F[B]) => F[B]): ContT[F, B, A] =
    IndexedContT(_run)
}

object Directives {
  // this is an example of using ContT for resource cleanup

  // poor man's Bracket...
  def cleanup[F[_], E, B, A](
    action: F[Unit]
  )(implicit F: MonadError[F, E]): A => ContT[F, B, A] =
    a =>
      ContT { next =>
        next(a).handleError(e => action >> F.raiseError(e)) <* action
      }

  final case class A0() // we can create one if we have an A3
  final case class A1() // we have one of these (or can create one)
  final case class A2() // can create from A1
  final case class A3() // can create from A2
  final case class A4() // can create from A3

  // encode what we can make...
  def bar0(a4: A4): IO[A0] = ???
  def bar2(a1: A1): IO[A2] = ???
  def bar3(a2: A2): IO[A3] = ???
  def bar4(a3: A3): IO[A4] = ???

  // we want a function A1 => F[A0]

  // so just chain them, what's the big deal?
  def simple(a: A1): IO[A0] =
    bar2(a) >>= bar3 >>= bar4 >>= bar0

  // or we can overengineer it with ContT...
  import IndexedContT.ops._

  def foo1(a: A1): ContT[IO, A0, A2] = bar2(a).cps
  def foo2(a: A2): ContT[IO, A0, A3] = bar3(a).cps
  def foo3(a: A3): ContT[IO, A0, A4] = bar4(a).cps
  def overengineered(a: A1): IO[A0]  = (foo1(a) >>= foo2 >>= foo3).run(bar0)

  // but lets say we want to change the return value of something to the right,
  // e.g. foo2 could change the result of downstream.
  def alter(a: A0): A0 = ???
  def foo2_(a: A2): ContT[IO, A0, A3] = ContT { next =>
    for {
      a3  <- bar3(a)
      a0  <- next(a3)
      a0_ = alter(a0)
    } yield a0_
  }
  // all the ContT can do this, so unlike the Kleisli arrows that are one way,
  // the ContT arrows go all the way to the right and then back again!
  def yoyo(a: A1): IO[A0] = (foo1(a) >>= foo2_ >>= foo3).run(bar0)

  // we can also raise an error or run the downstream action again (visualise
  // this) based on a downstream value. Perhaps useful for asynchronous user
  // data validation.
  def check(a0: A0): Boolean = ???
  def foo2_retry(a: A2): ContT[IO, A0, A3] = ContT { next =>
    for {
      a3 <- bar3(a)
      a0 <- next(a3)
      a0_ <- if (check(a0)) a0.pure[IO]
            else next(a3)
    } yield a0_
  }

  // or set the computation off into a different chain of continuations
  def elsewhere: ContT[IO, A0, A4] = ???
  def foo2_elsewhere(a: A2): ContT[IO, A0, A3] = ContT { next =>
    for {
      a3 <- bar3(a)
      a0 <- next(a3)
      a0_ <- if (check(a0)) a0.pure[IO]
            else elsewhere.run(bar0)
    } yield a0_
  }

  def cleanup: IO[Unit]                  = ???
  def foo2_err(a: A2): ContT[IO, A0, A3] = bar3(a).ensuring(cleanup).cps

  // basically, ContT is custom control flow for Kleisli. it is incredibly
  // unlikely that you'll need to use it, but now you know it exist.

  def main(args: Array[String]): Unit = {
    type F[a] = EitherT[IO, Int, a]
    val F = MonadError[F, Int]

    def safe: Boolean => ContT[F, String, Boolean] =
      cleanup(EitherT.rightT(IO(println(s"cleaned up"))))

    def good(a: Boolean): ContT[F, String, Boolean] =
      ContT(next => next(a).map(_.toUpperCase))
    def bad(a: Boolean): ContT[F, String, Boolean] =
      ContT(next => F.raiseError(1) >> next(a))
    def ugly(a: Boolean): ContT[F, String, Boolean] =
      ContT(_ => EitherT.rightT(IO.throwIO[String](new NullPointerException)))

    println((safe(true) >>= good).run(_.toString.pure[F]).run.unsafePerformIO())

    println((safe(true) >>= bad).run(_.toString.pure[F]).run.unsafePerformIO())

    // doesn't run after this...
    //println((safe(true) >>= ugly).run(_.toString.pure[F]).run.unsafePerformIO())
  }

  // TODO take alternative actions based on some condition check

  // def unitAttack: ContT[IO, Unit, Target] = ContT { todo =>
  //   for {
  //     _     <- swingAxeBack(60)
  //     valid <- isTargetValid(t)
  //     res   <- if (valid) todo(t) else sayUhOh
  //   } yield res
  // }

  // this section translates a haskell tutorial... it touches on the core
  // concept of "breaking out of the standard control flow"
  //
  // http://www.haskellforall.com/2012/12/the-continuation-monad.html
  class Target
  // without ContT
  def unitAttack_(t: Target): (Target => IO[Unit]) => IO[Unit] = { todo =>
    for {
      _     <- swingAxeBack(60)
      valid <- isTargetValid(t)
      res   <- if (valid) todo(t) else sayUhOh
    } yield res
  }
  // but creating combo would be very painful...

  def unitAttack(t: Target): ContT[IO, Unit, Target] = ContT { todo =>
    for {
      _     <- swingAxeBack(60)
      valid <- isTargetValid(t)
      res   <- if (valid) todo(t) else sayUhOh
    } yield res
  }
  def swingAxeBack(i: Int): IO[Unit]        = ???
  def isTargetValid(t: Target): IO[Boolean] = ???
  def sayUhOh: IO[Unit]                     = ???

  def halfAssed(t: Target): ContT[IO, Unit, Int] = ???
  def fullAss(i: Int): ContT[IO, Unit, String]   = ???

  // with bind
  def combo1(t: Target): ContT[IO, Unit, Int] = unitAttack(t) >>= halfAssed
  def combo2(t: Target): ContT[IO, Unit, String] =
    unitAttack(t) >>= halfAssed >>= fullAss

  // with arrows
  val combo1
    : Target => ContT[IO, Unit, String] = Kleisli(unitAttack) >=> Kleisli(
    halfAssed
  ) >=> Kleisli(fullAss)
  // or with the >==> sugar
  val combo2
    : Target => ContT[IO, Unit, String] = Kleisli(unitAttack) >==> halfAssed >==> fullAss

  // this is a completely failed attempt to build a web framework based on
  // ContT. Spoiler: it ends up just being Kleisli, and I never figured
  // out how to do paths.
  //
  // inspired by
  // https://gist.github.com/iravid/7c4b3d0bbd5a9de058bd7a5534073b4d
  final case class Request[F[_]](
    method: String,
    query: String,
    headers: Map[String, String],
    body: F[String]
  )

  final case class Response[F[_]](
    code: Int,
    headers: Map[String, String],
    body: F[String]
  )

  final case class RequestError(
    code: Int,
    message: String
  )

  type Route[f[_], a, b] = Kleisli[ContT[f, Response[f], ?], a, b]
  object Route {
    // this version lets us perform actions after the handler
    def apply[F[_], A, B](
      f: (A, (B => F[Response[F]])) => F[Response[F]]
    ): Route[F, A, B] =
      Kleisli(a => ContT(b => f(a, b)))

    // this applies the handler as the last action... but this is just Kleisli
    def apply[F[_]: Monad, A, B](
      f: A => F[B]
    ): Route[F, A, B] =
      Kleisli(a => ContT(b => f(a) >>= b))
  }

}
