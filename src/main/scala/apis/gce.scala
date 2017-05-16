// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package apis.gce

import java.lang.String

import scala.{Boolean, Int}
import scala.collection.immutable.{Map, Seq}

// https://cloud.google.com/container-engine/reference/rest/v1/NodeConfig
final case class NodeConfig(
  machineType: String,
  diskSizeGb: Int,
  oauthScopes: Seq[String],
  serviceAccount: String,
  metadata: Map[String, String],
  imageType: String,
  labels: Map[String, String],
  localSsdCount: Int,
  tags: Map[String, String],
  preemptible: Boolean
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#MasterAuth
final case class MasterAuth(
  username: String,
  password: String,
  clusterCaCertificate: String,
  clientCertificate: String,
  clientKey: String
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#AddonsConfig
final case class HttpLoadBalancing(disabled: Boolean)
final case class HorizontalPodAutoscaling(disabled: Boolean)
final case class AddonsConfig(
  httpLoadBalancing: HttpLoadBalancing,
  horizontalPodAutoscaling: HorizontalPodAutoscaling
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters.nodePools#NodePool
final case class NodePoolAutoscaling(
  enabled: Boolean,
  minNodeCount: Int,
  maxNodeCount: Int
)
final case class AutoUpgradeOptions(
  autoUpgradeStartTime: String,
  description: String
)
final case class NodeManagement(
  autoUpgrade: Boolean,
  upgradeOptions: AutoUpgradeOptions
)
final case class NodePool(
  name: String,
  config: NodeConfig,
  initialNodeCount: Int,
  selfLink: String,
  version: String,
  instanceGroupUrls: Seq[String],
  status: Status,
  statusMessage: String,
  autoscaling: NodePoolAutoscaling,
  management: NodeManagement
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#Status
sealed trait Status
case object STATUS_UNSPECIFIED extends Status
case object PROVISIONING extends Status
case object RUNNING extends Status
case object RECONCILING extends Status
case object STOPPING extends Status
case object ERROR extends Status

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#Cluster
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
  nodePools: Seq[NodePool],
  locations: Seq[String],
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
  instanceGroupUrls: Seq[String],
  currentNodeCount: Int,
  expireTime: String
)
