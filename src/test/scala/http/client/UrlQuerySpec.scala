// Copyright: 2017 - 2018 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package http.client

import scala.collection.immutable.List

import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import eu.timepit.refined.auto._

class UrlQuerySpec extends FlatSpec {
  import UrlQuery.ops._

  "UrlQuery" should "allow changing the query" in {
    val url: AsciiUrl.Url = "http://fommil.com?wibble=wobble"

    url
      .withQuery(
        UrlQuery(
          List(
            ("blah", "bloo"),
            (" meh ", "#")
          )
        )
      )
      .value
      .shouldBe("http://fommil.com?blah=bloo&%20meh%20=%23")
  }

}
