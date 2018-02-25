// Copyright: 2017 - 2018 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.client

import java.lang.String
import java.net.URI

import scala.{ AnyVal, StringContext }
import scala.collection.immutable.List

import scalaz._, Scalaz._

import http.encoding.UrlEncodedWriter

/**
 * A container for URL query key=value pairs, in unencoded form.
 */
@deriving(UrlEncodedWriter)
final case class UrlQuery(params: List[(String, String)]) extends AnyVal
object UrlQuery {
  object ops {
    implicit class EncodedUrlOps(private val encoded: EncodedUrl.Url) {
      def withQuery(query: UrlQuery): EncodedUrl.Url = {
        val uri = EncodedUrl.toURI(encoded)
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
        EncodedUrl(update.toASCIIString).getOrElse(
          scala.sys.error("bug in refinement")
        )
      }
    }
  }

}
