// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http.encoding

import prelude._, Z._

import UrlQueryWriter.ops._

class UrlQueryWriterSpec extends Test {
  "UrlQueryWriter"
    .should("encode case classes")
    .in(
      Foo("http://foo", 10, "%").toUrlQuery.params.shouldBe(
        IList(
          "apple"   -> "http://foo",
          "bananas" -> "10",
          "pears"   -> "%"
        )
      )
    )
}
