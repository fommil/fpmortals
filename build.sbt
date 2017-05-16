inThisBuild(
  Seq(
    scalaVersion := "2.12.2",
    sonatypeGithub := ("fommil", "drone-dynamic-agents"),
    licenses := Seq(Apache2)
  )
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-fs2"
).map(_ % "0.8.0") ++ Seq(
  "org.typelevel" %% "cats" % "0.9.0",
  "com.spinoco" %% "fs2-http" % "0.1.6"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-explaintypes",
  "-Ywarn-value-discard",
  "-Ywarn-numeric-widen",
  "-language:higherKinds",
  "-Ypartial-unification",
  "-Xlog-free-terms",
  "-Xlog-free-types",
  "-Xlog-reflective-calls",
  "-Yrangepos",
  "-Yno-imports",
  "-Yno-predef"
)

// false positives with sysdef
scalacOptions -= "-Ywarn-unused-import"
scalacOptions += "-Ywarn-unused:implicits,-imports,-locals,-params,-patvars,-privates"

// http://frees.io/docs/
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch)
libraryDependencies += "io.frees" %% "freestyle" % "0.1.1"
