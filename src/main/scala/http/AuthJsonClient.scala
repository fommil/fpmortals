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

  def post[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): F[A]

}

final class AuthJsonClientModule[F[_]](
  @unused token: RefreshToken
)(
  @unused H: JsonClient[F],
  @unused A: RefreshModule[F]
)(
  implicit
  @unused F: MonadError[F, JsonClient.Error],
  @unused S: MonadState[F, BearerToken]
) extends AuthJsonClient[F] {

  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[A] = ???

  def post[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): F[A] = ???

}
