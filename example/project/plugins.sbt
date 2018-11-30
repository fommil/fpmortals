scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil"    % "sbt-sensible" % "2.4.6")
addSbtPlugin("com.geirsson"  % "sbt-scalafmt" % "1.6.0-RC4")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.1")
