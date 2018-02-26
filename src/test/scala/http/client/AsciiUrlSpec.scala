// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.client

import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class AsciiUrlSpec extends FlatSpec {
  "AsciiUrl" should "require ascii encoding" in {
    // from https://en.wikipedia.org/wiki/URL
    assert(AsciiUrl("http://example.com/引き割り.html").isLeft)

    AsciiUrl
      .encode("http://example.com/引き割り.html")
      .shouldBe(
        AsciiUrl(
          "http://example.com/%E5%BC%95%E3%81%8D%E5%89%B2%E3%82%8A.html"
        )
      )

    // seems the JDK doesn't work correctly with
    // "http://例子.卷筒纸" shouldBe "http://xn--fsqu00a.xn--3lr804guic/"
  }

}
