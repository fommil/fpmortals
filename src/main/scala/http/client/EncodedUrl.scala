// Copyright: 2017 - 2018 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.client

import java.lang.String
import java.net.URI

import scala.StringContext
import scala.Predef.identity
import scala.util.Try

import scalaz._, Scalaz._

import eu.timepit.refined.refineV
import eu.timepit.refined.api.{ Refined, Validate }

/**
 * A predicate that `String Refined EncodedUrl` contains a `String` which will
 * successfully parse as a URI, representing a URL. The `String` is encoded,
 * meaning that no further encoding is necessary.
 *
 * URI is used in preference to URL as it is more standards compliant.
 */
sealed abstract class EncodedUrl
object EncodedUrl {
  def apply(raw: String): String \/ (String Refined EncodedUrl) =
    refineV[EncodedUrl](raw).disjunction

  /** Tries to encode and validate the given string */
  def encode(raw: String): String \/ (String Refined EncodedUrl) =
    for {
      uri  <- parse(raw)
      pass <- apply(uri.toASCIIString)
    } yield pass

  def toURI(encoded: String Refined EncodedUrl): URI =
    new URI(encoded.value) // safe

  def parse(raw: String): String \/ URI =
    Try(new URI(raw)).toDisjunction.leftMap { t =>
      // Parser failures are computationally expensive because of the exception.
      // If performance of the unhappy path is important, a regex bloom filter
      // could be used prior to constructing the URI.
      s"'$raw' is not a valid URL: ${t.getMessage}"
    }

  def validated(raw: String, uri: URI): String \/ URI =
    if (!uri.isAbsolute || Maybe.fromNullable(uri.getHost).isEmpty)
      s"'$raw' is not an absolute URL".left
    else if (raw != uri.toASCIIString)
      s"'$raw' encodes to '${uri.toASCIIString}'".left
    else
      uri.right

  implicit def validate: Validate.Plain[String, EncodedUrl] =
    Validate.fromPredicate(
      s => (parse(s) >>= (validated(s, _))).isRight,
      identity,
      new EncodedUrl {}
    )
}
