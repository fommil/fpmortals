// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.oauth2

import prelude._, Z._

import time._
import http.client.algebra.JsonHttpClient

import api._

/**
 * Bearer tokens (aka access tokens) expire frequently and can also
 * be expired on a whim by the server. For example, Google only allow
 * 50 tokens, per user.
 */
final case class BearerToken(token: String, expires: Epoch)

// TODO: split into two
final class OAuthRefresh[F[_]: Monad](
  config: ServerConfig
)(
  server: JsonHttpClient[F],
  clock: LocalClock[F]
) {
  def bearer(refresh: RefreshToken): F[BearerToken] =
    for {
      request <- RefreshRequest(
                  config.clientSecret,
                  refresh.token,
                  config.clientId
                ).pure[F]
      response <- server
                   .postUrlEncoded[RefreshRequest, RefreshResponse](
                     config.refresh,
                     request,
                     IList.empty
                   )
      time    <- clock.now
      msg     = response.body
      expires = time + msg.expires_in.seconds
      bearer  = BearerToken(msg.access_token, expires)
    } yield bearer

}
