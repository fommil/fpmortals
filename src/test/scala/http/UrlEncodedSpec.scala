// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package http.encoding

import org.scalatest._
import org.scalatest.Matchers._
import UrlEncoded.ops._
import spinoco.protocol.http.Uri

final case class Foo(apple: String, bananas: Long, pears: String)

class UrlEncodedSpec extends FlatSpec {
  "UrlEncoded" should "encode Strings" in {
    "foo".urlEncoded should be("foo")
    "http://foo".urlEncoded should be("http%3A%2F%2Ffoo")
  }

  it should "encode Long numbers" in {
    10L.urlEncoded should be("10")
  }

  it should "encode stringy maps" in {
    val stringy = Seq(
      "apple" -> "http://foo",
      "bananas" -> "10",
      "pears" -> "%"
    )
    stringy.urlEncoded should be("apple=http%3A%2F%2Ffoo&bananas=10&pears=%25")
  }

  it should "encode Uris" in {
    val uri = Uri.parse("http://foo/?blah=http%3A%2F%2Ffoo&bloo=bar").toOption.get
    uri.urlEncoded should be("http%3A%2F%2Ffoo%2F%3Fblah%3Dhttp%253A%252F%252Ffoo%26bloo%3Dbar")
  }

  it should "encode case classes" in {
    Foo("http://foo", 10L, "%").urlEncoded should be("apple=http%3A%2F%2Ffoo&bananas=10&pears=%25")
  }

}
