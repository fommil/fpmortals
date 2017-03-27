scalaVersion in ThisBuild := "2.12.1"

sonatypeGithub := ("fommil", "drone-dynamic-agents")
licenses := Seq(Apache2)

scalacOptions += "-language:higherKinds"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats" % "0.9.0",
  "com.spinoco" %% "fs2-http" % "0.1.6"
)

// http://frees.io/docs/
resolvers += Resolver.sonatypeRepo("snapshots")
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
libraryDependencies += "com.47deg" %% "freestyle" % "0.1.0-SNAPSHOT"
