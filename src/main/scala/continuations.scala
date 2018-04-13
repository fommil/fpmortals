// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package continuations

import scalaz.{ Kleisli, Monad }
import monadio.IO
import scalaz.Scalaz._

final case class ContT[F[_], B, A](_run: (A => F[B]) => F[B]) {
  def run(f: A => F[B]): F[B] = _run(f)
}
object ContT {
  implicit def monad[F[_], B]: Monad[ContT[F, B, ?]] =
    new Monad[ContT[F, B, ?]] {
      def point[A](a: =>A): ContT[F, B, A] = ContT(_(a))
      def bind[A, C](
        fa: ContT[F, B, A]
      )(f: A => ContT[F, B, C]): ContT[F, B, C] =
        ContT(d => fa.run(a => f(a).run(d)))
    }
}

object Directives {

  // http://www.haskellforall.com/2012/12/the-continuation-monad.html
  class Target
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

  // inspired by
  // https://gist.github.com/iravid/7c4b3d0bbd5a9de058bd7a5534073b4d

  // trait RequestContext[F[_]] {
  //   def method: String
  //   def path: String
  //   def complete(resp: String): F[RouteResult]
  // }

  // class RouteResult
  // type Service[f[_]] = Kleisli[f, RequestContext[f], RouteResult]
  // type Directive[f[_], a] = ContT[f, Service[f], a]

  /*
  trait RequestContext {
    def method: String
    def path: String
    def complete(resp: String): Future[RouteResult]
  }

  class RouteResult

  type Service = Kleisli[Future, RequestContext, RouteResult]

  type Directive[A] = ContT[Future, Service, A]

  def inspect(f: RequestContext => Boolean)(implicit ec: ExecutionContext) =
    new Directive[Unit] {
      override def run(cont: () => Future[Service]): Future[Service] = Future.successful {
        Kleisli { request =>
          if (f(request)) cont().flatMap(_.apply(request))
          else Future.failed(new Exception)
        }
      }
    }

  def get(implicit ec: ExecutionContext): Directive[Unit] = inspect(_.method == "GET")

  def pathPrefix(prefix: String)(implicit ec: ExecutionContext): Directive[Unit] = inspect(_.path startsWith prefix)

  def complete(response: String): Service = Kleisli(_.complete(response))
  import scala.concurrent.ExecutionContext.Implicits.global

  pathPrefix("hello") run { _ =>
    get runPure { _ =>
      complete("OK")
    }
  }
 */
}
