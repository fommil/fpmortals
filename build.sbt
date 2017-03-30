inThisBuild(
  Seq(
    scalaVersion := "2.12.1",
    scalaOrganization := "org.typelevel",
    sonatypeGithub := ("fommil", "drone-dynamic-agents"),
    licenses := Seq(Apache2),
    scalacOptions ++= Seq(
      "-Ywarn-value-discard",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused", // but remove in ensime because of PC false positives
      "-language:higherKinds",
      // https://github.com/typelevel/scala/blob/typelevel-readme/notes/2.12.1.md
      "-Ykind-polymorphism",
      "-Ypartial-unification",
      "-Yinduction-heuristics",
      "-Yliteral-types",
      "-Xstrict-patmat-analysis",
      "-Xlint:strict-unsealed-patmat"
    )
  )
)

val common = Seq(
  libraryDependencies += "org.typelevel" %% "cats" % "0.9.0"
)

val predef = project.settings(common)
val core = project.settings(common)
  .dependsOn(predef)
  .settings(
    // don't forget to update ensimeScalacOptions in ensime.sbt if you use it...
    scalacOptions ++= Seq(
      "-Ysysdef", "cats._,cats.data._,cats.implicits._,freestyle._,freestyle.implicits._",
      "-Ypredef", "fommil.Predef._"
    ),
    libraryDependencies ++= Seq(
      "com.spinoco" %% "fs2-http" % "0.1.6"
    ),
    // http://frees.io/docs/
    resolvers += Resolver.sonatypeRepo("snapshots"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies += "com.47deg" %% "freestyle" % "0.1.0-SNAPSHOT"
  )
