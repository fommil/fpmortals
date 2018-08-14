// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package interpreters

import prelude._

import eu.timepit.refined.string.Url
import jsonformat._
import http.client.algebra._
import http.encoding._

/**
 * A JSON HTTP client that transparently uses OAUTH 2.0 under the hood for
 * authentication.
 */
final class OAuth2JsonHttpClient[F[_]](
  H: JsonHttpClient[F]
) extends JsonHttpClient[F] {

  def get[B: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)] = IList.empty
  ): F[Response[B]] = ???

  // using application/x-www-form-urlencoded
  def postUrlEncoded[A: UrlEncodedWriter, B: JsDecoder](
    uri: String Refined Url,
    payload: A,
    headers: IList[(String, String)] = IList.empty
  ): F[Response[B]] = ???

}
