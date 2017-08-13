// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package http.client

/**
 * An algebra for issuing basic GET / POST requests to a web server
 * that returns JSON. Uses the spinoco HTTP protocol definition
 * classes out of convenience.
 */
package algebra

import scala.collection.immutable.{ List, Nil }
import scala.language.higherKinds

import io.circe.Decoder
import spinoco.protocol.http._
import spinoco.protocol.http.header._
import http.encoding._

final case class Response[T](header: HttpResponseHeader, body: T)

trait JsonHttpClient[F[_]] {
  def get[B: Decoder](
    uri: Uri,
    headers: List[HttpHeader] = Nil
  ): F[Response[B]]

  // using application/x-www-form-urlencoded
  def postUrlencoded[A: UrlEncoded, B: Decoder](
    uri: Uri,
    payload: A,
    headers: List[HttpHeader] = Nil
  ): F[Response[B]]
}
