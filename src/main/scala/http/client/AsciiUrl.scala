// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.client

import std._, Z._, S._

import java.net.URI

import eu.timepit.refined.refineV
import eu.timepit.refined.api.Validate

/**
 * A predicate that `String Refined AsciiUrl` contains a `String` which will
 * successfully parse as a URI, representing a URL. The `String` is encoded,
 * meaning that no further encoding is necessary.
 *
 * URI is used in preference to URL as it is more standards compliant.
 */
sealed abstract class AsciiUrl
object AsciiUrl {
  def apply(raw: String): String \/ (String Refined AsciiUrl) =
    refineV[AsciiUrl](raw).disjunction

  /** Tries to encode and validate the given string */
  def encode(raw: String): String \/ (String Refined AsciiUrl) =
    for {
      uri  <- parse(raw)
      pass <- apply(uri.toASCIIString)
    } yield pass

  def toURI(encoded: (String Refined AsciiUrl)): URI =
    new URI(encoded.value) // safe

  def parse(raw: String): String \/ URI =
    Maybe.attempt(new URI(raw)).toRight(s"'$raw' is not a valid URL")

  def validated(raw: String, uri: URI): String \/ URI =
    if (!uri.isAbsolute || Maybe.fromNullable(uri.getHost).isEmpty)
      s"'$raw' is not an absolute URL".left[URI]
    else if (raw /== uri.toASCIIString)
      s"'$raw' is not ASCII encoded".left[URI]
    else
      uri.right[String]

  implicit def validate: Validate.Plain[String, AsciiUrl] =
    Validate.fromPredicate(
      s => (parse(s) >>= (validated(s, _))).isRight,
      identity,
      new AsciiUrl {}
    )
}
