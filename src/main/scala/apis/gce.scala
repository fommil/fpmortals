// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package apis.gce

import java.lang.String

import scala.{Boolean, Int}
import scala.collection.immutable.{Map, Seq}

// https://cloud.google.com/container-engine/reference/rest/v1/NodeConfig
case class NodeConfig(
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
case class MasterAuth(
  username: String,
  password: String,
  clusterCaCertificate: String,
  clientCertificate: String,
  clientKey: String
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters#AddonsConfig
case class HttpLoadBalancing(disabled: Boolean)
case class HorizontalPodAutoscaling(disabled: Boolean)
case class AddonsConfig(
  httpLoadBalancing: HttpLoadBalancing,
  horizontalPodAutoscaling: HorizontalPodAutoscaling
)

// https://cloud.google.com/container-engine/reference/rest/v1/projects.zones.clusters.nodePools#NodePool
case class NodePoolAutoscaling(
  enabled: Boolean,
  minNodeCount: Int,
  maxNodeCount: Int
)
case class AutoUpgradeOptions(
  autoUpgradeStartTime: String,
  description: String
)
case class NodeManagement(
  autoUpgrade: Boolean,
  upgradeOptions: AutoUpgradeOptions
)
case class NodePool(
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
case class Cluster(
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
