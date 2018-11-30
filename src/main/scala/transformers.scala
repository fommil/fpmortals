// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package transformers

import scalaz._
import Scalaz._

import scala.language.higherKinds

final case class User(name: String) extends AnyVal

trait Twitter[F[_]] {
  def getUser(name: String): F[Maybe[User]]
  def getStars(user: User): F[Int]
}
object Twitter {
  def T[F[_]](implicit t: Twitter[F]): Twitter[F] = t
}
import Twitter.T

object Difficult {
  def stars[F[_]: Monad: Twitter](name: String): F[Maybe[Int]] =
    for {
      maybeUser  <- T.getUser(name)
      maybeStars <- maybeUser.traverse(T.getStars)
    } yield maybeStars
}

object WithMonadPlus {
  def monad[F[_]: Monad] = new MonadPlus[MaybeT[F, ?]] {
    def point[A](a: =>A): MaybeT[F, A] = MaybeT.just(a)
    def bind[A, B](fa: MaybeT[F, A])(f: A => MaybeT[F, B]): MaybeT[F, B] =
      MaybeT(fa.run >>= (_.cata(f(_).run, Maybe.empty.pure[F])))

    def empty[A]: MaybeT[F, A] = MaybeT.empty

    def plus[A](a: MaybeT[F, A], b: =>MaybeT[F, A]): MaybeT[F, A] = ???
  }

  def stars[F[_]: MonadPlus: Twitter](name: String): F[Int] =
    for {
      user  <- T.getUser(name) >>= (_.orEmpty[F])
      stars <- T.getStars(user)
    } yield stars
}

object WithMaybeT {
  def stars[F[_]: Monad: Twitter](name: String): MaybeT[F, Int] =
    for {
      user  <- MaybeT(T.getUser(name))
      stars <- T.getStars(user).liftM[MaybeT]
    } yield stars
}

object WithMonadError {
  def monad[F[_]: Monad, E] = new MonadError[EitherT[F, E, ?], E] {
    def bind[A, B](
      fa: EitherT[F, E, A]
    )(f: A => EitherT[F, E, B]): EitherT[F, E, B] =
      EitherT(fa.run >>= (_.fold(_.left[B].pure[F], b => f(b).run)))
    def point[A](a: =>A): EitherT[F, E, A] = EitherT.pure(a)

    def raiseError[A](e: E): EitherT[F, E, A] = EitherT.pureLeft(e)
    def handleError[A](
      fa: EitherT[F, E, A]
    )(f: E => EitherT[F, E, A]): EitherT[F, E, A] =
      EitherT(fa.run >>= {
        case -\/(e) => f(e).run
        case right  => right.pure[F]
      })
  }

  implicit class HelperMaybeOps[A](m: Maybe[A]) {
    def orError[F[_], E](e: E)(implicit F: MonadError[F, E]): F[A] =
      m.cata(F.point(_), F.raiseError(e))
  }

  implicit class HelperEitherOps[E, A](d: E \/ A) {
    def orRaiseError[F[_]](implicit F: MonadError[F, E]): F[A] =
      d.fold(F.raiseError(_), F.point(_))
  }

  def stars[F[_]: Twitter](
    name: String
  )(implicit F: MonadError[F, String]): F[Int] =
    for {
      user <- T.getUser(name) >>= (_.orError[F, String](
               s"user '$name' not found"
             ))
      //.map(_ \/> s"user '$name' not found") >>= (_.orRaiseError[F])
      stars <- T.getStars(user)
    } yield stars
}

object WithEitherT {
  def stars[F[_]: Monad: Twitter](name: String): EitherT[F, String, Int] =
    for {
      user  <- EitherT(T.getUser(name).map(_ \/> s"user '$name' not found"))
      stars <- EitherT.rightT(T.getStars(user))
    } yield stars

}

object BetterErrors {
  final case class Meta(fqn: String, file: String, line: Int)
  object Meta {
    implicit def gen(
      implicit fqn: sourcecode.FullName,
      file: sourcecode.File,
      line: sourcecode.Line
    ): Meta =
      new Meta(fqn.value, file.value, line.value)
  }

  final case class Err(msg: String)(implicit val meta: Meta)
  def main(args: Array[String]) =
    println(Err("hello world").meta)

}

object MockErrors {
  final class MockTwitter extends Twitter[String \/ ?] {
    def getUser(name: String): String \/ Maybe[User] =
      if (name.contains(" ")) Maybe.empty.right
      else if (name === "wobble") "connection error".left
      else User(name).just.right

    def getStars(user: User): String \/ Int =
      if (user.name.startsWith("w")) 10.right
      else "stars have been replaced by hearts".left
  }

  implicit val twitter: Twitter[String \/ ?] = new MockTwitter

  def main(args: Array[String]) = {
    println(WithMonadError.stars("wibble"))
    println(WithMonadError.stars("wobble"))
    println(WithMonadError.stars("i'm a fish"))
    println(WithMonadError.stars("fommil"))
  }

}
