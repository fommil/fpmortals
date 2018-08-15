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
 * authentication. Methods are the same as on JsonClient but are not inherited,
 * to emphasise the different semantics.
 */
trait AuthJsonClient[F[_]] {

  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[A]

  def postUrlEncoded[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): F[A]

}

final class AuthJsonClientModule[F[_]](
  token: RefreshToken
)(
  H: JsonClient[F],
  A: RefreshModule[F]
)(
  implicit
  F: MonadError[F, JsonClient.Error],
  S: MonadState[F, BearerToken]
) extends AuthJsonClient[F] {

  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[A] = ???

  // using application/x-www-form-urlencoded
  def postUrlEncoded[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): F[A] = ???

}
