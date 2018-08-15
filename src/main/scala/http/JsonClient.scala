// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http

import prelude._

import jsonformat.JsDecoder
import eu.timepit.refined.string.Url

import http.encoding._

case class Response[A](
  headers: IList[(String, String)],
  body: Response.Error \/ A
)
object Response {
  sealed abstract class Error
  final case class ServerError(status: Int)       extends Error
  final case class DecodingError(message: String) extends Error
}

/**
 * An algebra for issuing basic GET / POST requests to a web server that returns
 * JSON.
 */
trait JsonClient[F[_]] {

  // FIXME: Refined types for Header keys

  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[Response[A]]

  // using application/x-www-form-urlencoded
  def postUrlEncoded[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): F[Response[A]]

}
