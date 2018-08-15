// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http

import prelude._

import eu.timepit.refined.string.Url
import jsonformat._
import http.encoding._
import http.oauth2._

/**
 * A JSON HTTP client that transparently uses OAUTH 2.0 under the hood for
 * authentication.
 */
trait AuthJsonClient[F[_]] extends JsonClient[F]

final class AuthJsonClientModule[F[_]](
  auth: RefreshToken
)(
  H: http.JsonClient[F]
) extends AuthJsonClient[F] {

  def get[B: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[Response[B]] = ???

  // using application/x-www-form-urlencoded
  def postUrlEncoded[A: UrlEncodedWriter, B: JsDecoder](
    uri: String Refined Url,
    payload: A,
    headers: IList[(String, String)]
  ): F[Response[B]] = ???

}
