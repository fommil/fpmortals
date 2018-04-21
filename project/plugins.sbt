scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil"   % "sbt-sensible" % "2.4.4")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.4.0")

// https://oss.sonatype.org/content/repositories/snapshots/ch/epfl/scala/scalafix-core_2.12/
resolvers += Resolver.sonatypeRepo("snapshots")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.6.0-M5-6-57061f72-SNAPSHOT")
