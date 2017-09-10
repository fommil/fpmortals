scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC11")

addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.10")
