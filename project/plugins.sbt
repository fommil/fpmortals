scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil"   % "sbt-sensible" % "2.4.5")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.6.0-RC3")

// https://oss.sonatype.org/content/repositories/snapshots/ch/epfl/scala/scalafix-core_2.12/
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.6.0-M9")
