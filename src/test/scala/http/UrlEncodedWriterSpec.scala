// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.encoding

import java.lang.String

import scala.Long
import scala.collection.immutable.List
import scala.Predef.ArrowAssoc

import org.scalatest._
import org.scalatest.Matchers._
import http.client._
import scalaz._, Scalaz._
import eu.timepit.refined.auto._

import UrlEncodedWriter.ops._

@deriving(UrlEncodedWriter, UrlQueryWriter)
final case class Foo(apple: String, bananas: Long, pears: String)

class UrlEncodedWriterSpec extends FlatSpec {
  "UrlEncodedWriter" should "encode Strings" in {
    "foo".toUrlEncoded should be("foo")
    "http://foo".toUrlEncoded should be("http%3A%2F%2Ffoo")
  }

  it should "encode Long numbers" in {
    10L.toUrlEncoded should be("10")
  }

  it should "encode stringy maps" in {
    val stringy = List(
      "apple"   -> "http://foo",
      "bananas" -> "10",
      "pears"   -> "%"
    )
    stringy.toUrlEncoded should be(
      "apple=http%3A%2F%2Ffoo&bananas=10&pears=%25"
    )
  }

  it should "encode Urls" in {
    import eu.timepit.refined.auto._

    val url: AsciiUrl.Url =
      "http://foo/?blah=http%3A%2F%2Ffoo&bloo=bar"
    url.toUrlEncoded should be(
      // the %3A must be double escaped to %253A
      "http%3A%2F%2Ffoo%2F%3Fblah%3Dhttp%253A%252F%252Ffoo%26bloo%3Dbar"
    )
  }

  it should "encode final case classes" in {
    Foo("http://foo", 10L, "%").toUrlEncoded should be(
      "apple=http%3A%2F%2Ffoo&bananas=10&pears=%25"
    )
  }

}
