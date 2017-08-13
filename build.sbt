inThisBuild(
  Seq(
    scalaVersion := "2.12.3",
    sonatypeGithub := ("fommil", "drone-dynamic-agents"),
    licenses := Seq(GPL3)
  )
)

val circeVersion = "0.8.0"
libraryDependencies ++= Seq(
  "io.circe"      %% "circe-core"    % circeVersion,
  "io.circe"      %% "circe-generic" % circeVersion,
  "io.circe"      %% "circe-parser"  % circeVersion,
  "io.circe"      %% "circe-fs2"     % circeVersion,
  "org.typelevel" %% "cats"          % "0.9.0",
  "org.typelevel" %% "kittens"       % "1.0.0-M10",
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

scalafmtOnCompile in ThisBuild := true
scalafmtConfig in ThisBuild := file("project/scalafmt.conf")
scalafmtVersion in ThisBuild := "1.2.0"

scalacOptions in (Compile, console) -= "-Xfatal-warnings"
initialCommands in (Compile, console) := Seq(
  "scala._",
  "scala.Predef._",
  "scala.collection.immutable._",
  "shapeless._",
  "_root_.io.circe",
  "circe._",
  "circe.generic.auto._",
  "cats._",
  "cats.implicits._"
).mkString("import ", ",", "")

addCommandAlias("fmt", ";sbt:scalafmt ;scalafmt ;test:scalafmt")
