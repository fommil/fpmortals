inThisBuild(
  Seq(
    scalaVersion := "2.12.1",
    scalaOrganization := "org.typelevel"
  )
)

sonatypeGithub := ("fommil", "drone-dynamic-agents")
licenses := Seq(Apache2)

scalacOptions ++= Seq(
  "-Ywarn-value-discard",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused",
  "-language:higherKinds",
  // https://github.com/typelevel/scala/blob/typelevel-readme/notes/2.12.1.md
  "-Ykind-polymorphism",
  "-Ypartial-unification",
  "-Yinduction-heuristics",
  "-Yliteral-types",
  "-Xstrict-patmat-analysis",
  "-Xlint:strict-unsealed-patmat",
  "-Ysysdef", "java.lang._,scala._,scala.collection.immutable._,cats._,cats.data._,cats.implicits._,freestyle._,freestyle.implicits._"
//  "-Ypredef", "_"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats" % "0.9.0",
  "com.spinoco" %% "fs2-http" % "0.1.6"
)

// http://frees.io/docs/
// but needs `sbt freestyleJVM/publishLocal` in a checkout
resolvers += Resolver.sonatypeRepo("snapshots")
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
libraryDependencies += "com.47deg" %% "freestyle" % "0.1.0-SNAPSHOT"
