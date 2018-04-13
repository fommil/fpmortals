// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package continuations

import scalaz.Monad

final case class ContT[F[_], R, O, A](_run: (A => F[O]) => F[R]) {
  def run(amo: A => F[O]): F[R] = _run(amo)
}
object ContT {
  implicit def monad[F[_], R]: Monad[ContT[F, R, R, ?]] =
    new Monad[ContT[F, R, R, ?]] {
      def point[A](a: =>A): ContT[F, R, R, A] = ContT(_(a))
      def bind[A, B](
        fa: ContT[F, R, R, A]
      )(f: A => ContT[F, R, R, B]): ContT[F, R, R, B] =
        ContT(b => fa.run(a => f(a).run(b)))
    }
}

object Directives {

  // inspired by
  // https://gist.github.com/iravid/7c4b3d0bbd5a9de058bd7a5534073b4d

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
