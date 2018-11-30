// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.oauth2

import prelude._
import Z._
import time._
import http._
import api._

import scala.language.higherKinds

/**
 * Bearer tokens (aka access tokens) expire frequently and can also
 * be expired on a whim by the server. For example, Google only allow
 * 50 tokens, per user.
 */
@deriving(Equal, Show, ConfigReader)
final case class BearerToken(token: String, expires: Epoch)

trait Refresh[F[_]] {
  def bearer(refresh: RefreshToken): F[BearerToken]
}

final class RefreshModule[F[_]: Monad](
  config: ServerConfig
)(
  H: JsonClient[F],
  T: LocalClock[F]
) extends Refresh[F] {
  def bearer(refresh: RefreshToken): F[BearerToken] =
    for {
      request <- RefreshRequest(
                  config.clientSecret,
                  refresh.token,
                  config.clientId
                ).pure[F]
      msg <- H.post[RefreshRequest, RefreshResponse](
              config.refresh,
              request,
              IList.empty
            )
      time    <- T.now
      expires = time + msg.expires_in.seconds
      bearer  = BearerToken(msg.access_token, expires)
    } yield bearer

}
