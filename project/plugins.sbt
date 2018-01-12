scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil" % "sbt-sensible" % "2.1.1")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.2.1")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.4.0")
