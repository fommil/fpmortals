// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.client

/**
 * An algebra for issuing basic GET / POST requests to a web server that returns
 * JSON.
 */
package algebra

import java.lang.String

import scala.collection.immutable.{ Map }

import spray.json.JsonReader
import http.encoding._

final case class Response[T](headers: Map[String, String], body: T)

trait JsonHttpClient[F[_]] {
  def get[B: JsonReader](
    uri: AsciiUrl.Url,
    headers: Map[String, String] = Map.empty
  ): F[Response[B]]

  // using application/x-www-form-urlencoded
  def postUrlEncoded[A: UrlEncodedWriter, B: JsonReader](
    uri: AsciiUrl.Url,
    payload: A,
    headers: Map[String, String] = Map.empty
  ): F[Response[B]]

}
