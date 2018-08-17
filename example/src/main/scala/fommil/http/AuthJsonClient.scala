// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http

import prelude._, Z._

import eu.timepit.refined.string.Url
import jsonformat._
import http.encoding._
import http.oauth2._
import time._

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
  token: RefreshToken
)(
  H: JsonClient[F],
  T: LocalClock[F],
  A: Refresh[F]
)(
  implicit F: MonadState[F, BearerToken]
) extends AuthJsonClient[F] {

  // if we wanted to add more resilience and re-obtain a token if the H.get
  // fails, we could do so here, but we would need to request a MonadError to be
  // able to detect failures: which means we need to remove the `implicit`
  // keyword to avoid ambiguity.

  def get[A: JsDecoder](
    uri: String Refined Url,
    headers: IList[(String, String)]
  ): F[A] =
    for {
      bearer <- goodBearer
      res    <- H.get(uri, mkHeader(bearer) :: headers)
    } yield res

  def post[P: UrlEncodedWriter, A: JsDecoder](
    uri: String Refined Url,
    payload: P,
    headers: IList[(String, String)]
  ): F[A] =
    for {
      bearer <- goodBearer
      res    <- H.post(uri, payload, mkHeader(bearer) :: headers)
    } yield res

  private def goodBearer: F[BearerToken] =
    for {
      now    <- T.now
      stored <- F.get
      valid <- {
        if (stored.expires < now) A.bearer(token) >>! F.put
        else stored.pure[F]
      }
    } yield valid

  private def mkHeader(b: BearerToken): (String, String) =
    "Authorization" -> ("Bearer " + b.token)
}
