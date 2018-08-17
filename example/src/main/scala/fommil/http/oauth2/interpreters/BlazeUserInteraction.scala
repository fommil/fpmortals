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

import fommil.os.Browser

final class BlazeUserInteraction private (
  pserver: Promise[Void, Server[Task]],
  ptoken: Promise[Void, String]
) extends UserInteraction[Task] {
  private val dsl = new Http4sDsl[Task] {}
  import dsl._

  // WORKAROUND "capture" is for https://github.com/http4s/http4s/issues/2004
  private object Code extends QueryParamDecoderMatcher[String]("code")
  private val service: HttpService[Task] = HttpService[Task] {
    case GET -> Root / "capture" :? Code(code) =>
      ptoken.complete(code).toTask >> Ok(z"WE GOT ALL YUR CODEZ $code")
  }

  private val launch: Task[Server[Task]] =
    BlazeBuilder[Task].bindHttp(0, "localhost").mountService(service, "/").start

  // we could put failures into pserver if we wanted "start once" semantics.
  def start: Task[String Refined Url] =
    for {
      server  <- launch
      updated <- pserver.complete(server)
      _ <- if (updated) Task.unit
          else server.shutdown *> Task.failMessage("a server was already up")
    } yield mkUrl(server)

  // the 1 second sleep is necessary to avoid shutting down the server before
  // the response is sent back to the browser (yes, we are THAT quick!)
  def stop: Task[CodeToken] =
    for {
      server <- pserver.get.toTask
      token  <- ptoken.get.toTask
      _      <- IO.sleep(1.second) *> server.shutdown
    } yield CodeToken(token, mkUrl(server))

  def open(url: String Refined Url): Task[Unit] = Browser.open(url)

  private def mkUrl(s: Server[Task]): String Refined Url = {
    val port = s.address.getPort // scalafix:ok
    Refined.unsafeApply(str"http://localhost:${port.toString}/capture") // scalafix:ok
  }

}
object BlazeUserInteraction {
  def apply(): Task[BlazeUserInteraction] = {
    for {
      p1 <- Promise.make[Void, Server[Task]]
      p2 <- Promise.make[Void, String]
    } yield new BlazeUserInteraction(p1, p2)
  }.widenError
}
