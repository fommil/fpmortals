// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package http.encoding

import scala.collection.immutable.Seq
import scala.Predef.ArrowAssoc

import org.scalatest._
import org.scalatest.Matchers._
import QueryEncoded.ops._
import DerivedQueryEncoded.exports._

class QueryEncodedSpec extends FlatSpec {
  "QueryEncoded" should "encode case classes" in {
    Foo("http://foo", 10, "%").queryEncoded.params should contain theSameElementsInOrderAs (
      Seq(
        "apple"   -> "http://foo",
        "bananas" -> "10",
        "pears"   -> "%"
      )
    )
  }
}
