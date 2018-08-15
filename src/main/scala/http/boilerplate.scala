// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http

import prelude._, Z._

import jsonformat.JsDecoder
import eu.timepit.refined.string.Url

import http.encoding._

private[http] abstract class JsonClientBoilerplate {
  this: JsonClient.type =>

  def liftM[F[_]: Monad, G[_[_], _]: MonadTrans](
    f: JsonClient[F]
  ): JsonClient[G[F, ?]] =
    new JsonClient[G[F, ?]] {
      def get[A: JsDecoder](
        uri: String Refined Url,
        headers: IList[(String, String)]
      ): G[F, A] = f.get(uri, headers).liftM[G]

      def postUrlEncoded[P: UrlEncodedWriter, A: JsDecoder](
        uri: String Refined Url,
        payload: P,
        headers: IList[(String, String)]
      ): G[F, A] = f.postUrlEncoded(uri, payload, headers).liftM[G]
    }

}
