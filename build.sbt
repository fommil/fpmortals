inThisBuild(
  Seq(
    scalaVersion := "2.12.1",
    scalaOrganization := "org.typelevel",
    sonatypeGithub := ("fommil", "drone-dynamic-agents"),
    licenses := Seq(Apache2),
    scalacOptions ++= Seq(
      "-Ywarn-value-discard",
      "-Ywarn-numeric-widen",
      "-language:higherKinds",
      // https://github.com/typelevel/scala/blob/typelevel-readme/notes/2.12.1.md
      "-Ykind-polymorphism",
      "-Ypartial-unification",
      "-Yinduction-heuristics",
      "-Yliteral-types",
      "-Xstrict-patmat-analysis"
    )
  )
)

val common = Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats" % "0.9.0"
  ) ++ shapeless.value
)

val circeVersion = "0.7.0"

val predef = project.settings(common).settings(
  scalacOptions ++= Seq(
    "-Yno-imports",
    "-Yno-predef"
  )
)
val core = project.settings(common)
  .dependsOn(predef)
  .settings(
    // ENSIME users should add the following to ensime.sbt
    // ensimeScalacOptions := (scalacOptions in LocalProject("core")).value
    scalacOptions ++= Seq(
      "-Ysysdef", "cats._,cats.data._,cats.implicits._,freestyle._,freestyle.implicits._,shapeless.cachedImplicit,fs2._",
      "-Ypredef", "fommil.Predef._"
    ),
    libraryDependencies ++= Seq(
      "com.spinoco" %% "fs2-http" % "0.1.6"
    ) ++ Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),
    // http://frees.io/docs/
    resolvers += Resolver.sonatypeRepo("snapshots"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies += "com.47deg" %% "freestyle" % "0.1.0-SNAPSHOT"
  )
