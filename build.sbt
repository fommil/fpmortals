inThisBuild(
  Seq(
    scalaVersion := "2.12.3",
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
  "com.spinoco"   %% "fs2-http"      % "0.1.7"
)

scalacOptions ++= Seq(
  "-language:_",
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
wartremoverWarnings in (Compile, compile) -= Wart.Any              // too many cats false positives

// http://frees.io/docs/
resolvers += Resolver.sonatypeRepo("snapshots")
addCompilerPlugin(
  "org.scalameta" % "paradise" % "3.0.0-M10" cross CrossVersion.patch
)
libraryDependencies += "io.frees" %% "freestyle" % "0.3.1"

scalafmtOnCompile in ThisBuild := true
scalafmtConfig in ThisBuild := file("project/scalafmt.conf")
scalafmtVersion in ThisBuild := "1.0.0-RC4"

// WORKAROUND https://github.com/scalameta/paradise/issues/10
scalacOptions in (Compile, console) ~= (_ filterNot (_ contains "paradise"))
