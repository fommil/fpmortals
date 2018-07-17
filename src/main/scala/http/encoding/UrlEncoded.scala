// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.encoding

import prelude._
import java.util.regex.Pattern
import java.net.URLEncoder
import eu.timepit.refined.api.Validate

/**
 * Evidence that a `String` is valid `application/x-www-form-urlencoded` (i.e.
 * URLEncoder plus equals and ampersand).
 *
 * Note that a urlencoding encoding can be applied multiple times, this only
 * confirms that at least one encoding round has been applied.
 */
sealed abstract class UrlEncoded
object UrlEncoded {
  def apply(s: String): String Refined UrlEncoded =
    Refined.unsafeApply(URLEncoder.encode(s, "UTF-8")) // scalafix:ok

  // WORKAROUND https://github.com/propensive/contextual/issues/21
  private[this] val valid: Pattern =
    Pattern.compile("\\A(\\p{Alnum}++|[-.*_+=&]++|%\\p{XDigit}{2})*\\z") // scalafix:ok

  implicit def urlValidate: Validate.Plain[String, UrlEncoded] =
    Validate.fromPredicate(
      s => valid.matcher(s).find(), // scalafix:ok
      identity,
      new UrlEncoded {}
    )
}
