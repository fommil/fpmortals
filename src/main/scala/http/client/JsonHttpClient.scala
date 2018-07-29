// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.client

/**
 * An algebra for issuing basic GET / POST requests to a web server that returns
 * JSON.
 */
package algebra

import prelude._

import jsonformat.JsDecoder
import eu.timepit.refined.string.Url

import http.encoding._

final case class Response[T](headers: Map[String, String], body: T)

trait JsonHttpClient[F[_]] {
  def get[B: JsDecoder](
    uri: String Refined Url,
    headers: Map[String, String] = Map.empty
  ): F[Response[B]]

  // using application/x-www-form-urlencoded
  def postUrlEncoded[A: UrlEncodedWriter, B: JsDecoder](
    uri: String Refined Url,
    payload: A,
    headers: Map[String, String] = Map.empty
  ): F[Response[B]]

}
