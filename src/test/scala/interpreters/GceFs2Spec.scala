// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package interpreters.gcefs

import org.scalatest._
import org.scalatest.Matchers._

class GceFs2Spec extends FlatSpec {
  val config = GceConfig(
    "summer-function-158620",
    "us-central1-a",
    "cluster-1"
  )
  val client = new GceFs2(config)

  "GceFs2Spec" should "return Cluster information" in {
    val t = client.getCluster.unsafeRun()
    scala.Predef.println(t)

  }
}
