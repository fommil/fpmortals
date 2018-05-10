// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.encoding

import prelude._

import org.scalatest._
import org.scalatest.Matchers._
import UrlQueryWriter.ops._

class UrlQueryWriterSpec extends FlatSpec {
  "UrlQueryWriter" should "encode case classes" in
    (Foo("http://foo", 10, "%").toUrlQuery.params should contain)
      .theSameElementsInOrderAs(
        List(
          "apple"   -> "http://foo",
          "bananas" -> "10",
          "pears"   -> "%"
        )
      )
}
