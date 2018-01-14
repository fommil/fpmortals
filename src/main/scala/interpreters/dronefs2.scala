// Copyright: 2017 - 2018 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package interpreters.dronefs2

import java.lang.{ String, SuppressWarnings }
import java.nio.channels.AsynchronousChannelGroup

import scala.{ Array, Int }
import scala.Predef.???

import fs2._
import _root_.io.circe.fs2._
import spinoco.fs2.http
import spinoco.fs2.http._
import spinoco.protocol.http._
import spinoco.protocol.http.header.Authorization
import spinoco.protocol.http.header.value.HttpCredentials._

import algebra._

final case class DroneConfig(
  host: String,
  token: String
)

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors

object Resources {
  val ES                   = Executors.newCachedThreadPool(Strategy.daemonThreadFactory("AG"))
  implicit val S: Strategy = Strategy.fromExecutor(ES)
  implicit val AG: AsynchronousChannelGroup =
    AsynchronousChannelGroup.withThreadPool(ES)
}

final class DroneFs2(config: DroneConfig) extends Drone[Task] {
  import Resources._

  private val clientTask: Task[HttpClient[Task]] = http.client[Task]()

  // doesn't quite tell us what we want...
  // http://readme.drone.io/api/build-endpoint/
  // https://github.com/drone/drone/blob/master/router/router.go#L166

  private val backlogRequest =
    HttpRequest
      .get[Task](Uri.https(config.host, "/api/builds"))
      .withHeader(Authorization(OAuth2BearerToken(config.token)))

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def getBacklog: Task[Int] =
    clientTask.flatMap { client =>
      client
        .request(backlogRequest)
        .flatMap { resp =>
          resp.body.chunks.through(byteParser).through(decoder[Task, Int])
        }
        .runLast
        .map(_.get)
    }

  def getAgents: Task[Int] = ???

}
