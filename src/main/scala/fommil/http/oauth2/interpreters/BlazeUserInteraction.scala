// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http
package oauth2
package interpreters

import prelude._

import eu.timepit.refined.string.Url

final case class Tokens()

import BlazeUserInteraction.F
final class BlazeUserInteraction extends UserInteraction[F] {
  def start: F[String Refined Url] = ???

  def open(uri: String Refined Url): F[Unit] = ???

  def stop: F[CodeToken] = ???

}
object BlazeUserInteraction {
  type F[a] = StateT[Task, Tokens, a]

  def apply(): Task[BlazeUserInteraction] = ???

}
