// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http
package oauth2

import prelude._, Z._

import time._
import http.client.algebra.JsonHttpClient
import api._

/**
 * Refresh tokens do not expire, except in response to a security
 * breach or user / server whim.
 */
final case class RefreshToken(token: String)

/**
 * Turns a one-shot `CodeToken` into a `BearerToken` than can be securely
 * persisted, and a `RefreshToken` that can be used temporarily.
 */
trait OAuthAccess[F[_]] {
  def access(code: CodeToken): F[(RefreshToken, BearerToken)]
}

final class OAuthAccessModule[F[_]: Monad](
  config: ServerConfig
)(
  server: JsonHttpClient[F],
  clock: LocalClock[F]
) extends OAuthAccess[F] {

  def access(code: CodeToken): F[(RefreshToken, BearerToken)] =
    for {
      request <- AccessRequest(
                  code.token,
                  code.redirect_uri,
                  config.clientId,
                  config.clientSecret
                ).pure[F]
      response <- server
                   .postUrlEncoded[AccessRequest, AccessResponse](
                     config.access,
                     request,
                     IList.empty
                   )
      time    <- clock.now
      msg     = response.body
      expires = time + msg.expires_in.seconds
      refresh = RefreshToken(msg.refresh_token)
      bearer  = BearerToken(msg.access_token, expires)
    } yield (refresh, bearer)

}
