// Copyright: 2018 Sam Halliday
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
import org.http4s.client.blaze.{ BlazeClientConfig, Http1Client }

final class BlazeJsonClient(
  H: http4s.client.Client[Task]
) extends JsonClient[Task] {

  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): Task[Response[A]] =
    H.fetch(
      http4s.Request[Task](
        uri = convert(uri),
        headers = convert(headers)
      )
    )(handler)

  def postUrlEncoded[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): Task[Response[A]] =
    H.fetch(
      http4s.Request[Task](
        method = http4s.Method.POST,
        uri = convert(uri),
        headers = convert(headers),
        body = convert(payload.toUrlEncoded.value)
      )
    )(handler)

  private[this] def convert(headers: IList[(String, String)]): http4s.Headers =
    http4s.Headers(
      headers.foldRight(List[http4s.Header]()) {
        case ((key, value), acc) => http4s.Header(key, value) :: acc
      }
    )
  private[this] def convert(headers: http4s.Headers): IList[(String, String)] =
    headers.foldRight(IList.empty[(String, String)]) { (h, acc) =>
      (h.name.value -> h.value) :: acc
    }

  // we already validated the Url. If this fails, it's a bug in http4s
  private[this] def convert(uri: String Refined Url): http4s.Uri =
    http4s.Uri.unsafeFromString(uri.value)

  private[this] def convert(body: String): fs2.Stream[Task, Byte] =
    fs2.Stream(body).through(fs2.text.utf8Encode).covary[Task]

  private[this] def handler[A: JsDecoder]
    : http4s.Response[Task] => Task[Response[A]] = { resp =>
    val headers = convert(resp.headers)
    if (!resp.status.isSuccess)
      Task.now(Response(headers, Response.ServerError(resp.status.code).left))
    else
      for {
        text <- resp.body.through(fs2.text.utf8Decode).compile.foldMonoid
        body = JsParser(text)
          .flatMap(_.as[A])
          .leftMap(Response.DecodingError(_))
      } yield Response(headers, body)
  }

}
object BlazeJsonClient {
  def apply(config: BlazeClientConfig): Task[BlazeJsonClient] =
    Http1Client(config).map(new BlazeJsonClient(_))
}
