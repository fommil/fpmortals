// Copyright: 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package http
package oauth2
package interpreters

import prelude._, T._, Z._

import eu.timepit.refined.string.Url
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.Server
import org.http4s.server.blaze._
//import org.http4s.implicits._

import fommil.os.Browser
import fommil.prelude.`package`.Task
import org.http4s.`package`.HttpService

final case class Tokens()

import BlazeUserInteraction.{ F, FT }
final class BlazeUserInteraction extends UserInteraction[F] {
  import BlazeUserInteraction.dsl._

  val service: HttpService[Task] = HttpService[Task] {
    case GET -> Root / "hello" / name => Ok(s"Hello, $name.")
  }

  def start: F[String Refined Url] = {
    for {
      server <- launch
      port   = server.address.getPort // scalafix:ok
    } yield mkUrl(port)
  }.liftM[FT]

  def stop: F[CodeToken] = ???

  def open(url: String Refined Url): F[Unit] = Browser.open(url).liftM[FT]

  private val launch: Task[Server[Task]] =
    BlazeBuilder[Task].bindHttp(0, "localhost").mountService(service, "/").start

  private def mkUrl(port: Int): String Refined Url =
    Refined.unsafeApply(str"http://localhost:${port.toString}/") // scalafix:ok

}
object BlazeUserInteraction {
  type FT[f[_], a] = StateT[f, Tokens, a]
  type F[a]        = FT[Task, a]
  private val dsl = new Http4sDsl[Task] {}

  def apply(): Task[BlazeUserInteraction] = ???

}
