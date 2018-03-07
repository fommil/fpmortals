scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil"   % "sbt-sensible" % "2.3.0")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.4.0")

resolvers += Resolver.sonatypeRepo("snapshots")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.6.0-M1-35-67a1f287-SNAPSHOT")
