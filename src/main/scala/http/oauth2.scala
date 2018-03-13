// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.oauth2

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
package client

import std._, Z._

import http.client._
import http.encoding._

/** Defines fixed information about a server's OAuth 2.0 service. */
final case class ServerConfig(
  auth: String Refined AsciiUrl,
  access: String Refined AsciiUrl,
  refresh: String Refined AsciiUrl,
  scope: String,
  clientId: String,
  clientSecret: String
)

/** Code tokens are one-shot and expire on use. */
final case class CodeToken(
  token: String,
  // for some stupid reason, the protocol needs the exact same
  // redirect_uri in subsequent calls
  redirect_uri: String Refined AsciiUrl
)

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
final case class BearerToken(token: String, expires: Instant)

package algebra {
  trait UserInteraction[F[_]] {

    /** returns the URL of the local server */
    def start: F[String Refined AsciiUrl]

    /** prompts the user to open this URL */
    def open(uri: String Refined AsciiUrl): F[Unit]

    /** recover the code from the callback */
    def stop: F[CodeToken]
  }

  trait LocalClock[F[_]] {
    def now: F[Instant]
  }
}

// what's the canonical name for this sort of thing? It's about combining algebras
package logic {
  import java.time.temporal.ChronoUnit
  import http.client.algebra.JsonHttpClient
  import algebra._

  class OAuth2Client[F[_]: Monad](
    config: ServerConfig
  )(
    implicit
    user: UserInteraction[F],
    server: JsonHttpClient[F],
    clock: LocalClock[F]
  ) {
    import api._
    import http.encoding.UrlQueryWriter.ops._
    import UrlQuery.ops._
    import xyz.driver.json.DerivedFormats._

    // for use in one-shot apps requiring user interaction
    def authenticate: F[CodeToken] =
      for {
        callback <- user.start
        params   = AuthRequest(callback, config.scope, config.clientId)
        _        <- user.open(config.auth.withQuery(params.toUrlQuery))
        code     <- user.stop
      } yield code

    def access(code: CodeToken): F[(RefreshToken, BearerToken)] =
      for {
        request <- AccessRequest(code.token,
                                 code.redirect_uri,
                                 config.clientId,
                                 config.clientSecret).pure[F]
        response <- server
                     .postUrlEncoded[AccessRequest, AccessResponse](
                       config.access,
                       request
                     )
        time    <- clock.now
        msg     = response.body
        expires = time.plus(msg.expires_in, ChronoUnit.SECONDS)
        refresh = RefreshToken(msg.refresh_token)
        bearer  = BearerToken(msg.access_token, expires)
      } yield (refresh, bearer)

    def bearer(refresh: RefreshToken): F[BearerToken] =
      for {
        request <- RefreshRequest(config.clientSecret,
                                  refresh.token,
                                  config.clientId).pure[F]
        response <- server
                     .postUrlEncoded[RefreshRequest, RefreshResponse](
                       config.refresh,
                       request
                     )
        time    <- clock.now
        msg     = response.body
        expires = time.plus(msg.expires_in, ChronoUnit.SECONDS)
        bearer  = BearerToken(msg.access_token, expires)
      } yield bearer
  }
}

/** The API as defined by the OAuth 2.0 server */
package api {
  @deriving(UrlQueryWriter)
  final case class AuthRequest(
    redirect_uri: String Refined AsciiUrl,
    scope: String,
    client_id: String,
    prompt: String = "consent",
    response_type: String = "code",
    access_type: String = "offline"
  )
  // AuthResponse is to send the user's browser to redirect_uri with a
  // `code` param (yup, seriously).

  @deriving(UrlEncodedWriter)
  final case class AccessRequest(
    code: String,
    redirect_uri: String Refined AsciiUrl,
    client_id: String,
    client_secret: String,
    scope: String = "",
    grant_type: String = "authorization_code"
  )
  final case class AccessResponse(
    access_token: String,
    token_type: String,
    expires_in: Long,
    refresh_token: String
  )

  @deriving(UrlEncodedWriter)
  final case class RefreshRequest(
    client_secret: String,
    refresh_token: String,
    client_id: String,
    grant_type: String = "refresh_token"
  )
  @deriving(UrlEncodedWriter)
  final case class RefreshResponse(
    access_token: String,
    token_type: String,
    expires_in: Long
  )
}
