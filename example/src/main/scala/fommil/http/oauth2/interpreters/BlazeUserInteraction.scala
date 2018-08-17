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
  ref: IORef[FSM]
) extends UserInteraction[Task] {
  private val dsl = new Http4sDsl[Task] {}
  import dsl._

  private val service: HttpService[Task] = HttpService[Task] {
    case GET -> Root / "hello" / name => Ok(s"Hello, $name.") // FIXME
  }

  private val launch: Task[Server[Task]] =
    BlazeBuilder[Task].bindHttp(0, "localhost").mountService(service, "/").start

  def start: Task[String Refined Url] =
    for {
      _ <- ref.read.flatMap[Unit] {
            case Nuthin => Task.unit
            case other =>
              Task.failMessage(z"bad state during start, got $other")
          }
      server <- launch
      _      <- ref.write(Waiting(server))
      port   = server.address.getPort // scalafix:ok
    } yield mkUrl(port)

  def stop: Task[CodeToken] =
    ref.read.flatMap[CodeToken] {
      case Waiting(server)         => server.shutdown *> Task.failMessage(z"no token")
      case Finished(server, token) => server.shutdown *> Task.now(token)
      case other                   => Task.failMessage(z"bad state during stop, got $other")
    }

  def open(url: String Refined Url): Task[Unit] = Browser.open(url)

  private def mkUrl(port: Int): String Refined Url =
    Refined.unsafeApply(str"http://localhost:${port.toString}/") // scalafix:ok

}
object BlazeUserInteraction {
  def apply(): Task[BlazeUserInteraction] =
    IORef(Nuthin.widen).map(new BlazeUserInteraction(_))
}

// IndexedStateT would be superior to FSM... but ensuring that service can only
// call a Waiting state is not possible to enforce.
@deriving(Show)
private[interpreters] sealed abstract class FSM { def widen: FSM = this }
private[interpreters] case object Nuthin extends FSM
private[interpreters] final case class Finished(
  server: Server[Task],
  token: CodeToken
) extends FSM
private[interpreters] final case class Waiting(
  server: Server[Task]
) extends FSM

private[interpreters] object Finished {
  implicit val show: Show[Finished] = Show.shows(f => z"Waiting(${f.token})")
}
private[interpreters] object Waiting {
  implicit val show: Show[Waiting] = Show.shows(_ => "Waiting")
}
