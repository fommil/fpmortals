// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package continuations

import scalaz.{ Contravariant, Functor, Kleisli, Monad }
import monadio.IO
import scalaz.Scalaz._
import spray.json._

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

  // with cps syntax, not sure I see where this is used...
  //import ContT.ops._
  //def otherAss: IO[Unit] = ???
  //val combo3: Target => ContT[IO, Unit, String] = Kleisli(unitAttack) >==> halfAssed >==> otherAss.cps[Unit]

  // a web framework based on callbacks
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

  //def routes_[F[_]: Monad]: Request[F] => F[Response[F]] = ???
  //def routes[F[_]: Monad]: Kleisli[F, Request[F], Response[F]] = ???
  def routes[F[_]: Monad](req: Request[F]): ContT[F, Response[F], Request[F]] =
    ???

  final case class JsonRequest(
    method: String,
    query: String,
    headers: Map[String, String],
    body: JsValue
  )
  final case class JsonResponse(
    code: Int,
    headers: Map[String, String],
    body: JsValue
  )

  import scalaz.Maybe

  def fromJson[F[_]: Monad](
    req: Request[F]
  ): ContT[F, Response[F], JsonRequest] = ContT { handler =>
    for {
      body  <- req.body
      mjson = Maybe.attempt(JsonParser(body)) // TODO: MonadPlus
      resp <- mjson match {
               case Maybe.Just(json) =>
                 handler(JsonRequest(req.method, req.query, req.headers, json))
               case Maybe.Empty() =>
                 ??? // TODO: error
             }
    } yield resp
  }

  // changing the return type is horrible... best require the users to
  // "completeJson" or something.
  def toJson[F[_]: Monad](
    req: Request[F]
  ): IndexedContT[F, Response[F], JsonResponse, JsonRequest] =
    (fromJson(req): IndexedContT[F, Response[F], Response[F], JsonRequest])
      .contramap(
        (jresp: JsonResponse) => null: Response[F]
//    (rreps: Response[F]) => ??? : JsonResponse
      )

//  def jsonRoutes[F[_]: Monad]: ContT[F, Response[F], Request[F]] = ContT {
//    (req: Request[F] => F[Response[F]]) =>
//    toJson(req).flatMap { json =>

//    }
//  }

  //def toJson[F[_]: Monad](req: Request[F]): F[JsonRequest] = ???
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
