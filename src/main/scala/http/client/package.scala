// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package http

import scala.StringContext

import contextual._

package object client {
  implicit class UrlStringContext(sc: StringContext) {
    val url = Prefix(UrlInterpolator, sc)
  }
}
