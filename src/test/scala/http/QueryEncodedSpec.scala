// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.encoding

import scala.collection.immutable.Seq
import scala.Predef.ArrowAssoc

import org.scalatest._
import org.scalatest.Matchers._
import UrlQueryWriter.ops._

class UrlQueryWriterSpec extends FlatSpec {
  "UrlQueryWriter" should "encode case classes" in {
    (Foo("http://foo", 10, "%").toUrlQuery.params should contain)
      .theSameElementsInOrderAs(
        Seq(
          "apple"   -> "http://foo",
          "bananas" -> "10",
          "pears"   -> "%"
        )
      )
  }
}
