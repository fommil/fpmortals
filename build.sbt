inThisBuild(
  Seq(
    scalaVersion := "2.12.2",
    sonatypeGithub := ("fommil", "drone-dynamic-agents"),
    licenses := Seq(Apache2)
  )
)

val circeVersion = "0.8.0"
libraryDependencies ++= Seq(
  "io.circe"      %% "circe-core"    % circeVersion,
  "io.circe"      %% "circe-generic" % circeVersion,
  "io.circe"      %% "circe-parser"  % circeVersion,
  "io.circe"      %% "circe-fs2"     % circeVersion,
  "org.typelevel" %% "cats"          % "0.9.0",
  "com.spinoco"   %% "fs2-http"      % "0.1.6"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-explaintypes",
  "-Ywarn-value-discard",
  "-Ywarn-numeric-widen",
  "-Ypartial-unification",
  "-Xlog-free-terms",
  "-Xlog-free-types",
  "-Xlog-reflective-calls",
  "-Yrangepos",
  "-Yno-imports",
  "-Yno-predef"
)

scalacOptions += "-Ywarn-unused:implicits,imports,locals,params,patvars,privates"

wartremoverWarnings in (Compile, compile) := Warts.unsafe ++ Seq(
  Wart.FinalCaseClass,
  Wart.ExplicitImplicitTypes
)
wartremoverWarnings in (Compile, compile) -= Wart.DefaultArguments // not sure I agree with this one...

// http://frees.io/docs/
resolvers += Resolver.sonatypeRepo("snapshots")
addCompilerPlugin(
  "org.scalameta" % "paradise" % "3.0.0-M8" cross CrossVersion.patch
)
libraryDependencies += "io.frees" %% "freestyle" % "0.3.1-SNAPSHOT"

scalafmtOnCompile in ThisBuild := true
scalafmtConfig in ThisBuild := file("project/scalafmt.conf")
scalafmtVersion in ThisBuild := "1.0.0-RC4"
