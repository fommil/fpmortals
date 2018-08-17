// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http
package interpreters

import prelude._, T._, Z._

import scala.collection.immutable.List

import jsonformat._
import jsonformat.JsDecoder.ops._
import eu.timepit.refined.string.Url

import http.encoding._
import UrlEncodedWriter.ops._

import org.http4s
import org.http4s.{ EntityEncoder, MediaType }
import org.http4s.headers.`Content-Type`
import org.http4s.client.Client
import org.http4s.client.blaze.{ BlazeClientConfig, Http1Client }

import BlazeJsonClient.F
final class BlazeJsonClient private (H: Client[Task]) extends JsonClient[F] {
  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[A] = EitherT(
    H.fetch(
      http4s.Request[Task](
        uri = convert(uri),
        headers = convert(headers)
      )
    )(handler)
  )

  // This is not a typeclass, but http4s treats it implicitly. We'll treat it
  // explicitly, which may mean having to treat other implicit parameters
  // explicitly too (see cats.Monad below).
  private val encoder: EntityEncoder[Task, String Refined UrlEncoded] =
    EntityEncoder[Task, String]
      .contramap[String Refined UrlEncoded](_.value)
      .withContentType(
        `Content-Type`(MediaType.`application/x-www-form-urlencoded`)
      )

  def post[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): EitherT[Task, JsonClient.Error, A] = EitherT(
    H.fetch(
      http4s
        .Request[Task](
          method = http4s.Method.POST,
          uri = convert(uri),
          headers = convert(headers)
        )
        .withBody(
          payload.toUrlEncoded
        )(cats.Monad[Task], encoder)
    )(handler)
  )

  private[this] def convert(headers: IList[(String, String)]): http4s.Headers =
    http4s.Headers(
      headers.foldRight(List[http4s.Header]()) {
        case ((key, value), acc) => http4s.Header(key, value) :: acc
      }
    )

  // we already validated the Url. If this fails, it's a bug in http4s
  private[this] def convert(uri: String Refined Url): http4s.Uri =
    http4s.Uri.unsafeFromString(uri.value)

  private[this] def handler[A: JsDecoder]
    : http4s.Response[Task] => Task[JsonClient.Error \/ A] = { resp =>
    if (!resp.status.isSuccess)
      Task.now(JsonClient.ServerError(resp.status.code).left)
    else
      for {
        text <- resp.body.through(fs2.text.utf8Decode).compile.foldMonoid
        res = JsParser(text)
          .flatMap(_.as[A])
          .leftMap(JsonClient.DecodingError(_))
      } yield res
  }

}
object BlazeJsonClient {
  type F[a] = EitherT[Task, JsonClient.Error, a]
  def apply(): Task[BlazeJsonClient] =
    Http1Client(BlazeClientConfig.defaultConfig).map(new BlazeJsonClient(_))
}
