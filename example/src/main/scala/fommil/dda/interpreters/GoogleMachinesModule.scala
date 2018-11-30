// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil
package dda
package interpreters

import prelude._
import Z._
import jsonformat._
import JsDecoder.fail
import algebra._
import time._
import http._

import scala.language.higherKinds

final class GoogleMachinesModule[F[_]](
  @unused H: OAuth2JsonClient[F]
) extends Machines[F] {

  def getAlive: F[MachineNode ==>> Epoch]      = ???
  def getManaged: F[NonEmptyList[MachineNode]] = ???
  def getTime: F[Epoch]                        = ???
  def start(node: MachineNode): F[Unit]        = ???
  def stop(node: MachineNode): F[Unit]         = ???

}

// https://cloud.google.com/container-engine/reference/rest/v1/NodeConfig
@deriving(Equal, Show, JsDecoder)
final case class NodeConfig(
  machineType: String,
  diskSizeGb: Int,
  oauthScopes: IList[String],
  serviceAccount: String,
  metadata: String ==>> String,
  imageType: String,
  labels: String ==>> String,
  localSsdCount: Int,
  tags: String ==>> String,
  preemptible: Boolean
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#MasterAuth
@deriving(Equal, Show, JsDecoder)
final case class MasterAuth(
  username: String,
  password: String,
  clusterCaCertificate: String,
  clientCertificate: String,
  clientKey: String
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#AddonsConfig
@deriving(Equal, Show, JsDecoder)
final case class HttpLoadBalancing(disabled: Boolean)
@deriving(Equal, Show, JsDecoder)
final case class HorizontalPodAutoscaling(disabled: Boolean)
@deriving(Equal, Show, JsDecoder)
final case class AddonsConfig(
  httpLoadBalancing: HttpLoadBalancing,
  horizontalPodAutoscaling: HorizontalPodAutoscaling
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters.nodePools#NodePool
@deriving(Equal, Show, JsDecoder)
final case class NodePoolAutoscaling(
  enabled: Boolean,
  minNodeCount: Int,
  maxNodeCount: Int
)
@deriving(Equal, Show, JsDecoder)
final case class AutoUpgradeOptions(
  autoUpgradeStartTime: String,
  description: String
)
@deriving(Equal, Show, JsDecoder)
final case class NodeManagement(
  autoUpgrade: Boolean,
  upgradeOptions: AutoUpgradeOptions
)
@deriving(Equal, Show, JsDecoder)
final case class NodePool(
  name: String,
  config: NodeConfig,
  initialNodeCount: Int,
  selfLink: String,
  version: String,
  instanceGroupUrls: IList[String],
  status: Status,
  statusMessage: String,
  autoscaling: NodePoolAutoscaling,
  management: NodeManagement
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#Status
@deriving(Equal, Show)
sealed abstract class Status
object Status {
  case object STATUS_UNSPECIFIED extends Status
  case object PROVISIONING       extends Status
  case object RUNNING            extends Status
  case object RECONCILING        extends Status
  case object STOPPING           extends Status
  case object ERROR              extends Status

  implicit val decoder: JsDecoder[Status] = JsDecoder[String].emap {
    case "STATUS_UNSPECIFIED" => STATUS_UNSPECIFIED.right
    case "PROVISIONING"       => PROVISIONING.right
    case "RUNNING"            => RUNNING.right
    case "RECONCILING"        => RECONCILING.right
    case "STOPPING"           => STOPPING.right
    case "ERROR"              => ERROR.right
    case other                => fail("a valid status", JsString(other))
  }
}

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#Cluster
@deriving(Equal, Show, JsDecoder)
final case class Cluster(
  name: String,
  description: String,
  initialNodeCount: Int,
  nodeConfig: NodeConfig,
  masterAuth: MasterAuth,
  loggingService: String,
  monitoringService: String,
  network: String,
  clusterIpv4Cidr: String,
  addonsConfig: AddonsConfig,
  subnetwork: String,
  nodePools: IList[NodePool],
  locations: IList[String],
  enableKubernetesAlpha: Boolean,
  selfLink: String,
  zone: String,
  endpoint: String,
  initialClusterVersion: String,
  currentMasterVersion: String,
  currentNodeVersion: String,
  createTime: String,
  status: Status,
  statusMessage: String,
  nodeIpv4CidrSize: Int,
  servicesIpv4Cidr: String,
  instanceGroupUrls: IList[String],
  currentNodeCount: Int,
  expireTime: String
)
