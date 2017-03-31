// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package interpreters.gcefs

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors

import fs2._
import _root_.io.circe.generic.auto._
import _root_.io.circe.fs2._
import spinoco.fs2.http
import spinoco.fs2.http._
import spinoco.protocol.http._
import spinoco.protocol.http.header.Authorization
import spinoco.protocol.http.header.value.HttpCredentials._

import algebra.machines._
import apis.gce._

final case class GceConfig(
  projectId: String, // e.g. summer-function-158620
  zone: String, // e.g. us-central1-a
  clusterId: String, // e.g. cluster-1
  token: String // https://console.developers.google.com/apis/credentials?project={projectId}
)

// TODO: take what we need as implicits
object Resources {
  val ES = Executors.newCachedThreadPool(Strategy.daemonThreadFactory("AG"))
  implicit val S = Strategy.fromExecutor(ES)
  //implicit val Sch = Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(4, Strategy.daemonThreadFactory("S")))
  implicit val AG = AsynchronousChannelGroup.withThreadPool(ES)
}

// https://cloud.google.com/container-engine/docs/
// https://cloud.google.com/container-engine/reference/rest/
final class GceFs2(config: GceConfig) extends Machines.Handler[Task] {
  import Resources._

  // TODO: take clientTask as input so we can mock
  private val clientTask: Task[HttpClient[Task]] = http.client()

  // not sure if this is possible?
  def getTime: Task[Time] = ???

  // https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters/get
  // FIXME: we don't read Backlog off the wire, we read a custom format and then convert into Backlog
  private val clusterRequest =
    HttpRequest.get[Task](Uri.https("container.googleapis.com", s"/v1/projects/{config.projectId}/zones/{config.zone}/clusters/{config.clusterId}"))
      .withHeader(Authorization(OAuth2BearerToken(config.token)))

  def getManaged: Task[Managed] = {
    val unmarshall = decoder[Task, Cluster]
    def extract: Pipe[Task, Cluster, Managed] = ???

    clientTask.flatMap { client =>
      client.request(clusterRequest).flatMap { resp =>
        resp.body.chunks.through(byteParser).through(unmarshall).through(extract)
      }.runLast.map(_.get)
    }
  }

  def getAlive: Task[Alive] = ???
  def start(node: Node): Task[Unit] = ???
  def stop(node: Node): Task[Unit] = ???

}

