// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package interpreters.gcefs

import java.lang.{ String, SuppressWarnings }
import java.nio.channels.AsynchronousChannelGroup
import java.time.ZonedDateTime
import java.util.concurrent.Executors

import scala.{ Array, StringContext }
import scala.collection.immutable.Map
import scala.Predef.???

import cats.data._
import fs2._
import _root_.io.circe
import circe._
import circe.generic.auto._
import circe.fs2._
import spinoco.fs2.http
import spinoco.fs2.http._
import spinoco.protocol.http._

import algebra.machines._
import apis.gce._

final case class GceConfig(
  projectId: String, // e.g. summer-function-158620
  zone: String, // e.g. us-central1-a
  clusterId: String // e.g. cluster-1
)

// TODO: take what we need as implicits
object Resources {
  val ES         = Executors.newCachedThreadPool(Strategy.daemonThreadFactory("AG"))
  implicit val S = Strategy.fromExecutor(ES)
  //implicit val Sch = Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(4, Strategy.daemonThreadFactory("S")))
  implicit val AG = AsynchronousChannelGroup.withThreadPool(ES)
}

// https://cloud.google.com/container-engine/docs/
// https://cloud.google.com/container-engine/reference/rest/
final class GceFs2Machine extends Machines.Handler[Task] {
  def getTime: Task[ZonedDateTime]             = ???
  def getManaged: Task[NonEmptyList[Node]]     = ???
  def getAlive: Task[Map[Node, ZonedDateTime]] = ???
  def start(node: Node): Task[Node]            = ???
  def stop(node: Node): Task[Node]             = ???
}

final class GceFs2(config: GceConfig) {
  import Resources._

  // TODO: take clientTask as input so we can mock
  private val clientTask: Task[HttpClient[Task]] = http.client()

  // TODO: abstract out the OAuth with an algebra
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def get[G: Decoder](path: String): Task[G] = {
    val request = HttpRequest.get[Task](
      Uri.https("container.googleapis.com", path)
    ) //.withHeader(Authorization(OAuth2BearerToken(config.token)))

    clientTask.flatMap { client =>
      client
        .request(request)
        .flatMap { resp =>
          resp.body.chunks.through(byteParser andThen decoder[Task, G])
        }
        .runLast
        .map(_.get)
    }
  }

  // https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters/get
  def getCluster: Task[Cluster] =
    get[Cluster](
      s"/v1/projects/${config.projectId}/zones/${config.zone}/clusters/${config.clusterId}"
    )

}
