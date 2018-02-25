// Copyright: 2018 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/

package refinement

import eu.timepit.refined
import refined.api.Refined

import refined.numeric.Positive
//import refined.string._
import refined.boolean._
import refined.collection._
import refined.auto._

final case class Person(
  name: String Refined NonEmpty,
  age: Int Refined Positive
)

object Main {
  def main(args: Array[String]) = {
    println(refined.refineV[NonEmpty](""))

    println(refined.refineV[NonEmpty]("Sam"))

    val sam: String Refined NonEmpty = "Sam"

    type Name = NonEmpty And MaxSize[refined.W.`10`.T]
    val wibble: String Refined Name = "wibble"

    // val empty: String Refined NonEmpty = ""
  }
}
