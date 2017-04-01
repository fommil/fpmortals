// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package interpreters.oauth2.client

/**
 * [[https://tools.ietf.org/html/rfc6749 OAuth2]] client
 * authentication is an absolute pain to set up: this library helps
 * make it less painful to use the
 * [[https://tools.ietf.org/html/rfc6749#page-24 Authorization Code
 * Grant]] (ACG) method when you have an fs2-http stack.
 *
 * Alternatively, consider using the
 * [[https://github.com/google/google-oauth-java-client Libre Java
 * client from Google]].
 *
 * There are two parts to this library:
 *
 * 1. a standalone main application that must be run once - by each
 *    user - to obtain a "refresh token" that can be used thereafter.
 *    This must be kept secret, it's equivalent to your password in
 *    plain text.
 *
 * 2. an algebra and fs2 interpreter to generate transient "bearer
 *    tokens" as required by every request to the third party API.
 *
 * We'll use Google Cloud as an example, but the concept is
 * generalisable.
 *
 * Every App needs to set up an
 * [[https://console.developers.google.com/apis/credentials?project={projectId}
 * OAuth 2.0 client ID]] and you should know the scopes that you wish
 * to request.
 *
 * This allows the App to obtain a one time "code" by making the user
 * perform an [[https://tools.ietf.org/html/rfc6749#section-4.1.1
 * Authorization Request]] in their browser, and giving the App
 * authority:
 *
 * {{{
 *   https://accounts.google.com/o/oauth2/v2/auth?redirect_uri=CALLBACK_URI&prompt=consent&response_type=code&scope=SCOPE&access_type=offline&client_id=CLIENT_ID
 * }}}
 *
 * This app spawn a localhost server on a transient port to capture
 * the code - via the `CALLBACK_URI` - and then performs an
 * [[https://tools.ietf.org/html/rfc6749#section-4.1.3 Access Token
 * Request]]:
 *
 * {{{
 * POST /oauth2/v4/token HTTP/1.1
 * Host: www.googleapis.com
 * Content-length: 233
 * content-type: application/x-www-form-urlencoded
 * user-agent: google-oauth-playground
 * code=CODE&redirect_uri=CALLBACK_URI&client_id=CLIENT_ID&client_secret=CLIENT_SECRET&scope=&grant_type=authorization_code
 * }}}
 *
 * giving a response payload looking like
 *
 * {{{
 * {
 *   "access_token": "BEARER_TOKEN",
 *   "token_type": "Bearer",
 *   "expires_in": 3600,
 *   "refresh_token": "REFRESH_TOKEN"
 * }
 * }}}
 *
 * According to the spec the "refresh token" is optional. We don't
 * support such authentication servers: it's basically impossible to
 * write a headless server under these circumstances unless the user
 * provides their username / password, i.e. the "Client Credentials
 * Grant" variant of OAuth2.
 *
 * Bearer tokens typically expire after an hour, and can
 * [[https://tools.ietf.org/html/rfc6749#section-5 be refreshed]]
 *
 * {{{
 *   POST /oauth2/v4/token HTTP/1.1
 *   Host: www.googleapis.com
 *   Content-length: 163
 *   content-type: application/x-www-form-urlencoded
 *   user-agent: google-oauth-playground
 *   client_secret=CLIENT_SECRET&grant_type=refresh_token&refresh_token=REFRESH_TOKEN&client_id=CLIENT_ID
 *  }}}
 *
 * giving
 *
 * {{{
 * {
 *   "access_token": "BEARER_TOKEN",
 *   "token_type": "Bearer",
 *   "expires_in": 3600
 * }
 * }}}
 *
 * But note that Google expires all but the most recent 50 bearer
 * tokens (and any other implementation is free to do similar),
 * so the expiry times are just guidance.
 *
 *
 * It used to be possible to use
 * [[http://stackoverflow.com/a/19766913/1041691 a pointy click UI]]
 * to do most of the above for testing purposes, but Google decided to
 * limit the `redirect_uri` to only domains owned by the owners of the
 * app.
 */
package acg

import java.net.URI
import freestyle._
import simulacrum._
import java.time.LocalDateTime

/** Defines fixed information about a server's OAuth 2.0 service. */
final case class ServerConfig(
  auth: URI,
  access: URI,
  bearer: URI,
  scope: String,
  clientId: String,
  clientSecret: String
)

/** Code tokens are one-shot and expire on use. */
final case class CodeToken(token: String)

/**
 * Refresh tokens do not expire, except in response to a security
 * breach or user / server whim.
 */
final case class RefreshToken(token: String)

/**
 * Bearer tokens (aka access tokens) expire frequently and can also
 * be expired on a whim by the server. For example, Google only allow
 * 50 tokens, per user.
 */
final case class BearerToken(token: String, expires: LocalDateTime)

object algebra {
  @free trait UserInteraction[F[_]] {
    /** returns the URI that the local server is listening on */
    def start: FreeS[F, URI]
    /** prompts the user to open this URI, which will end up at the local server */
    def open(uri: URI): FreeS[F, Unit]
    /** wait for the user interaction to complete and recover the code */
    def stop: FreeS[F, CodeToken]
  }

  @free trait OAuth2Client[F[_]] {
    // for use in one-shot apps requiring user interaction
    def authenticate: FreeS[F, CodeToken]
    def access(code: CodeToken): FreeS[F, (RefreshToken, Option[BearerToken])]

    // for use in headless servers
    def bearer(refresh: RefreshToken): FreeS[F, BearerToken]
  }
}

/** The API as defined by the OAuth 2.0 server */
package api {

  final case class AuthRequest(
    redirect_uri: URI,
    scope: String,
    client_id: String,
    prompt: String = "consent",
    response_type: String = "code",
    access_type: String = "offline"
  )
  // AuthResponse is to send the user's browser to redirect_uri with a
  // `code` param (yup, seriously).

  final case class AccessRequest(
    code: String,
    redirect_uri: URI,
    client_id: String,
    client_secret: String,
    scope: String = "",
    grant_type: String = "authorization_code"
  )
  final case class AccessResponse(
    access_token: String,
    token_type: String,
    expires_in: Int,
    refresh_token: String
  )

  final case class RefreshRequest(
    client_secret: String,
    refresh_token: String,
    client_id: String,
    grant_type: String = "refresh_token"
  )
  final case class RefreshResponse(
    access_token: String,
    token_type: String,
    expires_in: Int
  )

}

// there has got to be a typeclass like this somewhere in fs2
object urlencoding {
  @typeclass trait UrlEncoding[T] {
    def urlEncoding(t: T): String
  }
}

package interpreters {
  import api._
  import algebra._
  import urlencoding.UrlEncoding
  import fs2._

  // TODO: take mockable client, server and user interaction
  final class Fs2Handler(
    config: ServerConfig
  )(
    implicit
    user: UserInteraction[Task]
  ) extends OAuth2Client.Handler[Task] {

    import UrlEncoding.ops._
    implicit object AuthRequestUrlEncoder extends UrlEncoding[AuthRequest] {
      override def urlEncoding(t: AuthRequest): String = ???
    }

    override def authenticate: FreeS[Task, CodeToken] =
      for {
        callback <- user.start
        params = AuthRequest(callback, config.scope, config.clientId)
        // NOTE: multi-argument URI constructors quote illegal characters
        auth = new URI(s"${config.auth}?${params.urlEncoding}")
        _ <- user.open(auth)
        code <- user.stop
      } yield code

    override def access(code: CodeToken): FreeS[Task, (RefreshToken, Option[BearerToken])] = ???
    override def bearer(refresh: RefreshToken): FreeS[Task, BearerToken] = ???
  }

}
