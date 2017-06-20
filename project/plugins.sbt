scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil" % "sbt-sensible" % "1.2.0")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.6")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.1")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.0.3")
