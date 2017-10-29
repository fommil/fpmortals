scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil" % "sbt-sensible" % "2.1.0")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.14")

addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.2.1")
