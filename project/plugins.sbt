scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil"   % "sbt-sensible" % "2.4.1")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.4.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.6.0-M3")
