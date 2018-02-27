// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.client

import std._, scalaz._, Scalaz._

import java.net.URI

import http.encoding.UrlEncodedWriter

/**
 * A container for URL query key=value pairs, in unencoded form.
 */
@deriving(UrlEncodedWriter)
final case class UrlQuery(params: List[(String, String)]) extends AnyVal
object UrlQuery {
  object ops {
    implicit class AsciiUrlOps(private val encoded: AsciiUrl.Url) {
      def withQuery(query: UrlQuery): AsciiUrl.Url = {
        val uri = AsciiUrl.toURI(encoded)
        val update = new URI(
          uri.getScheme,
          uri.getUserInfo,
          uri.getHost,
          uri.getPort,
          uri.getPath,
          // not a mistake: URI takes the decoded versions
          query.params.map { case (k, v) => s"$k=$v" }.intercalate("&"),
          uri.getFragment
        )
        AsciiUrl(update.toASCIIString).getOrElse(
          scala.sys.error("bug in refinement")
        )
      }
    }
  }

}
