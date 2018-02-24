scalacOptions ++= Seq("-unchecked", "-deprecation")
ivyLoggingLevel := UpdateLogging.Quiet

addSbtPlugin("com.fommil"    % "sbt-sensible" % "2.3.0")
addSbtPlugin("com.geirsson"  % "sbt-scalafmt" % "1.4.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.5.10")
