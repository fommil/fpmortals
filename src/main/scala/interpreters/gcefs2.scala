// Copyright: 2017 - 2018 https://gitlab.com/fommil/drone-dynamic-agents/graphs/master
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package interpreters.gcefs

import java.lang.{ String, SuppressWarnings }
import java.nio.channels.AsynchronousChannelGroup
import java.time.ZonedDateTime
import java.util.concurrent.Executors

import scala.{ Array, StringContext, Unit }
import scala.collection.immutable.Map
import scala.Predef.???

import scalaz._
import fs2._
import _root_.io.circe
import circe._
import circe.generic.auto._
import circe.fs2._
import spinoco.fs2.http
import spinoco.fs2.http._
import spinoco.protocol.http._

import algebra._
import apis.gce._

// NOTE: for oauth2 we need to use
// scope=https://www.googleapis.com/auth/cloud-platform

final case class GceConfig(
  projectId: String, // e.g. summer-function-158620
  zone: String, // e.g. us-central1-a
  clusterId: String // e.g. cluster-1
)

object Resources {
  val ES         = Executors.newCachedThreadPool(Strategy.daemonThreadFactory("AG"))
  implicit val S = Strategy.fromExecutor(ES)
  //implicit val Sch = Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(4, Strategy.daemonThreadFactory("S")))
  implicit val AG = AsynchronousChannelGroup.withThreadPool(ES)
}

// https://cloud.google.com/container-engine/docs/
// https://cloud.google.com/container-engine/reference/rest/
final class GceFs2Machine extends Machines[Task] {
  def getTime: Task[ZonedDateTime]                    = ???
  def getManaged: Task[NonEmptyList[MachineNode]]     = ???
  def getAlive: Task[Map[MachineNode, ZonedDateTime]] = ???
  def start(node: MachineNode): Task[Unit]            = ???
  def stop(node: MachineNode): Task[Unit]             = ???
}

final class GceFs2(config: GceConfig) {
  import Resources._

  private val clientTask: Task[HttpClient[Task]] = http.client()

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
