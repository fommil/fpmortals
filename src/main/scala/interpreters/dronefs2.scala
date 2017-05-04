// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package interpreters.dronefs2

import fs2._
import _root_.io.circe.generic.auto._
import _root_.io.circe.fs2._
import spinoco.fs2.http
import spinoco.fs2.http._
import spinoco.protocol.http._
import spinoco.protocol.http.header.Authorization
import spinoco.protocol.http.header.value.HttpCredentials._

import algebra.drone._

final case class DroneConfig(
  host: String,
  token: String
)

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors

// TODO: take what we need as implicits
object Resources {
  val ES = Executors.newCachedThreadPool(Strategy.daemonThreadFactory("AG"))
  implicit val S = Strategy.fromExecutor(ES)
  //implicit val Sch = Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(4, Strategy.daemonThreadFactory("S")))
  implicit val AG = AsynchronousChannelGroup.withThreadPool(ES)
}

final class DroneFs2(config: DroneConfig) extends Drone.Handler[Task] {
  import Resources._

  // TODO: take clientTask as input so we can mock
  private val clientTask: Task[HttpClient[Task]] = http.client[Task]()

  // doesn't quite tell us what we want...
  // http://readme.drone.io/api/build-endpoint/
  // https://github.com/drone/drone/blob/master/router/router.go#L166

  // FIXME: investigate /api/info/queue

  // FIXME: we don't read Backlog off the wire, we read a custom format and then convert into Backlog
  private val backlogRequest =
    HttpRequest.get[Task](Uri.https(config.host, "/api/builds"))
      .withHeader(Authorization(OAuth2BearerToken(config.token)))

  def getBacklog: Task[Int] = {
    clientTask.flatMap { client =>
      client.request(backlogRequest).flatMap { resp =>
        resp.body.chunks.through(byteParser).through(decoder[Task, Int])
      }.runLast.map(_.get)
    }
  }

  def getAgents: Task[Int] = ???

}
