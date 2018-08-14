// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.client.interpreters

import prelude._, S._, Z._

import jsonformat._
import jsonformat.JsDecoder.ops._
import eu.timepit.refined.string.Url

import http.client.algebra._
import http.encoding._

import org.http4s
import org.http4s.client.blaze._

import scalaz.ioeffect.catz._
import shims._

sealed abstract class BlazeError
    extends java.lang.Exception
    with scala.util.control.NoStackTrace

final case class DecodingError(message: String)      extends BlazeError
final case class ServerError(message: http4s.Status) extends BlazeError

final class BlazeJsonHttpClient(
  client: http4s.client.Client[Task]
) extends JsonHttpClient[Task] {

  private[this] def convert(headers: IList[(String, String)]): http4s.Headers =
    http4s.Headers(
      headers.foldRight(Nil: List[http4s.Header]) {
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

  // reading the body could be optimised with .chunks and Cord in order to avoid
  // building intermediate Strings.
  def get[B: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)] = IList.empty
  ): Task[Response[B]] =
    client.fetch[Response[B]](
      http4s.Request[Task](
        uri = convert(uri),
        headers = convert(headers)
      )
    ) { (resp: http4s.Response[Task]) =>
      if (!resp.status.isSuccess)
        Task.fail(ServerError(resp.status))
      else
        for {
          text   <- resp.body.through(fs2.text.utf8Decode).compile.foldMonoid
          parsed = JsParser(text).flatMap(_.as[B])
          body <- parsed match {
                   case \/-(b)   => Task.now(b)
                   case -\/(err) => Task.fail(DecodingError(err))
                 }
        } yield Response(convert(resp.headers), body)
    }

  // using application/x-www-form-urlencoded
  def postUrlEncoded[A: UrlEncodedWriter, B: JsDecoder](
    uri: String Refined Url,
    payload: A,
    headers: IList[(String, String)] = IList.empty
  ): Task[Response[B]] = ???

}
object BlazeJsonHttpClient {
  def apply(config: BlazeClientConfig): Task[BlazeJsonHttpClient] =
    Http1Client(config).map(new BlazeJsonHttpClient(_))
}
