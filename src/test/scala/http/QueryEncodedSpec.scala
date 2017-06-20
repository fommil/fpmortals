// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package http.encoding

import scala.collection.immutable.Seq
import scala.Predef.ArrowAssoc

import org.scalatest._
import org.scalatest.Matchers._
import QueryEncoded.ops._

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
