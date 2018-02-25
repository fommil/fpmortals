// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package http.client

import java.lang.String
import java.net.{ URI, URISyntaxException }

import scala.{ AnyVal, Int, Some, StringContext }
import scala.Predef.identity
import scala.collection.immutable.List

import scalaz._, Scalaz._

import http.encoding.UrlEncoded

import eu.timepit.refined.api.{ Refined, Validate }

/**
 * Sanitised java.net.URI for use as a URL.
 *
 * {{{
 * scheme://[auth@]host[:port][/path][?query][#fragment]
 * }}}
 *
 * To create an instance at compiletime
 *
 * {{{
 * import eu.timepit.refined.auto._
 * Url("http://fommil.com")
 * }}}
 *
 * To create an instance at runtime, use `Url.parse`
 */
final class Url private (uri: URI) {
  def encoded: String           = uri.toASCIIString
  override def toString: String = encoded

  def scheme: String          = uri.getScheme
  def auth: Maybe[String]     = Maybe.fromNullable(uri.getUserInfo)
  def host: String            = uri.getHost
  def port: Maybe[Int]        = uri.getPort.just.filterNot(_ == -1)
  def path: Maybe[String]     = Maybe.fromNullable(uri.getPath).filterNot(_.isEmpty)
  def query: Maybe[String]    = Maybe.fromNullable(uri.getQuery)
  def fragment: Maybe[String] = Maybe.fromNullable(uri.getFragment)

  def withQuery(query: Url.Query): Url =
    new Url(
      // safe because we already validated the authority
      new URI(
        uri.getScheme,
        uri.getUserInfo,
        uri.getHost,
        uri.getPort,
        uri.getPath,
        // URI wants the decoded version
        query.params.map { case (k, v) => s"$k=$v" }.intercalate("&"),
        uri.getFragment
      )
    )

}
object Url {
  // redoes work by refineV, so not ideal from a perf point of view
  def apply(raw: String Refined Valid): Url = new Url(new URI(raw.value))

  def unapply(url: Url): Some[
    (String,
     Maybe[String],
     String,
     Maybe[Int],
     Maybe[String],
     Maybe[String],
     Maybe[String])
  ] = {
    import url._
    Some((scheme, auth, host, port, path, query, fragment))
  }

  // predicate for refined
  final case class Valid()
  object Valid {
    implicit def validate: Validate.Plain[String, Valid] =
      Validate.fromPredicate(parse(_).isRight, identity, Valid())
  }
  def parse(raw: String): String \/ Url =
    try {
      val uri = new URI(raw)
      if (uri.getScheme == null)
        s"no scheme in '$raw'".left
      else if (uri.getHost == null)
        s"no host in '$raw'".left
      else
        new Url(uri).right
    } catch {
      case m: URISyntaxException =>
        s"'$raw' is not a valid URL: ${m.getMessage}".left
    }

  /** Commonly used (but not ubiguitous) `key=value` pairs */
  @deriving(UrlEncoded)
  final case class Query(params: List[(String, String)]) extends AnyVal
}
