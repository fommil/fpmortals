scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil" % "sbt-sensible" % "1.1.16")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.1")
