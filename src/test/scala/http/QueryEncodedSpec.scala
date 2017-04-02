// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package http.encoding

import org.scalatest._
import org.scalatest.Matchers._
import QueryEncoded.ops._
import spinoco.protocol.http.Uri.Query

class QueryEncodedSpec extends FlatSpec {
  "QueryEncoded" should "encode Strings" in {
    Seq("foo" -> "bar").queryEncoded should be(Query(List("foo" -> "bar")))

    // doesn't encode special characters
    Seq("http://foo" -> "bar?=%/").queryEncoded should be(Query(List("http://foo" -> "bar?=%/")))
  }

  it should "encode case classes" in {
    Foo("http://foo", 10, "%").queryEncoded.params should contain theSameElementsInOrderAs (
      Seq(
        "apple" -> "http://foo",
        "bananas" -> "10",
        "pears" -> "%"
      )
    )
  }
}
