// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package http

import simulacrum.typeclass

object encoding {
  @typeclass trait UrlEncoded[T] {
    def urlEncoded(t: T): String
  }
  // TODO: basic / generic impls
}
