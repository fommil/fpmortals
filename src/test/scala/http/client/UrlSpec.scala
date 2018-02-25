// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package http.client

import scala.collection.immutable.List

import org.scalatest._
import org.scalatest.Matchers._

import scalaz._, Maybe.{ Empty, Just }

import eu.timepit.refined.auto._

class UrlSpec extends FlatSpec {
  "Url" should "parse hosts" in {
    Url("http://fommil.com") should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse usernames and passwords" in {
    Url("https://fommil@fommil.com") should matchPattern {
      case Url("https",
               Just("fommil"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    Url("http://fommil:wobble@fommil.com") should matchPattern {
      case Url("http",
               Just("fommil:wobble"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    Url("http://:wobble@fommil.com") should matchPattern {
      case Url("http",
               Just(":wobble"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    Url("http://:@fommil.com") should matchPattern {
      case Url("http",
               Just(":"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }

    Url("http://::@fommil.com") should matchPattern {
      case Url("http",
               Just("::"),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse ports" in {
    Url("http://fommil.com:80") should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Just(80),
               Empty(),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse paths" in {
    Url("http://fommil.com/") should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Just("/"),
               Empty(),
               Empty()) =>
    }

    Url("http://fommil.com//") should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Just("//"),
               Empty(),
               Empty()) =>
    }

    Url("http://fommil.com/wibble/") should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Just("/wibble/"),
               Empty(),
               Empty()) =>
    }

    Url("http://example.com/引き割り.html") should matchPattern {
      case Url("http",
               Empty(),
               "example.com",
               Empty(),
               Just("/引き割り.html"),
               Empty(),
               Empty()) =>
    }
  }

  it should "parse query" in {
    Url("http://fommil.com?foo=bar&baz=gaz") should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Empty(),
               Just("foo=bar&baz=gaz"),
               Empty()) =>
    }
  }

  it should "parse anchor" in {
    Url("http://fommil.com#wibble") should matchPattern {
      case Url("http",
               Empty(),
               "fommil.com",
               Empty(),
               Empty(),
               Empty(),
               Just("wibble")) =>
    }
  }

  it should "correctly encode to ascii" in {
    // from https://en.wikipedia.org/wiki/URL
    // seems the JDK doesn't work correctly here
    // url"http://例子.卷筒纸".encoded shouldBe "http://xn--fsqu00a.xn--3lr804guic/"

    Url("http://example.com/引き割り.html").encoded
      .shouldBe("http://example.com/%E5%BC%95%E3%81%8D%E5%89%B2%E3%82%8A.html")
  }

  it should "allow changing the query" in {
    Url("http://fommil.com?wibble=wobble")
      .withQuery(
        Url.Query(
          List(
            ("blah", "bloo"),
            (" meh ", "#")
          )
        )
      )
      .encoded
      .shouldBe("http://fommil.com?blah=bloo&%20meh%20=%23")
  }

}
