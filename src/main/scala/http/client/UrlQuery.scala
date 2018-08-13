// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.client

import prelude._, Z._, S._

import java.net.URI
import eu.timepit.refined.string.Url

import http.encoding.UrlEncodedWriter

/**
 * URL query key=value pairs, in unencoded form.
 */
@xderiving(UrlEncodedWriter)
final case class UrlQuery(params: IList[(String, String)]) extends AnyVal
object UrlQuery {
  object ops {
    implicit class UrlOps(private val encoded: String Refined Url) {
      def withQuery(query: UrlQuery): String Refined Url = {
        val uri = new URI(encoded.value)
        val update = new URI(
          uri.getScheme,
          uri.getUserInfo,
          uri.getHost,
          uri.getPort,
          uri.getPath,
          // not a mistake: URI takes the decoded versions
          query.params.map { case (k, v) => k + "=" + v }.intercalate("&"),
          uri.getFragment
        )
        Refined.unsafeApply(update.toASCIIString)
      }
    }
  }

}
