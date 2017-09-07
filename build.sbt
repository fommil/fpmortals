inThisBuild(
  Seq(
    scalaVersion := "2.12.3",
    sonatypeGithub := ("fommil", "drone-dynamic-agents"),
    licenses := Seq(GPL3)
  )
)

val circeVersion = "0.8.0"
libraryDependencies ++= Seq(
  "com.github.mpilquist" %% "simulacrum"    % "0.10.0",
  "com.chuusai"          %% "shapeless"     % "2.3.2",
  "org.typelevel"        %% "export-hook"   % "1.2.0",
  "io.circe"             %% "circe-core"    % circeVersion,
  "io.circe"             %% "circe-generic" % circeVersion,
  "io.circe"             %% "circe-parser"  % circeVersion,
  "io.circe"             %% "circe-fs2"     % circeVersion,
  "org.scalaz"           %% "scalaz-core"   % "7.2.15",
  "com.spinoco"          %% "fs2-http"      % "0.1.7"
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

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
)

wartremoverWarnings in (Compile, compile) := Warts.unsafe ++ Seq(
  Wart.FinalCaseClass,
  Wart.ExplicitImplicitTypes
)
wartremoverWarnings in (Compile, compile) -= Wart.DefaultArguments // not sure I agree with this one...
wartremoverWarnings in (Compile, compile) -= Wart.Any              // too many false positives

scalafmtOnCompile in ThisBuild := true
scalafmtConfig in ThisBuild := file("project/scalafmt.conf")
scalafmtVersion in ThisBuild := "1.2.0"

scalacOptions in (Compile, console) -= "-Xfatal-warnings"
initialCommands in (Compile, console) := Seq(
  "java.lang.String",
  "scala.{Any,AnyRef,AnyVal,Boolean,Byte,Double,Float,Short,Int,Long,Char,Symbol,Unit,Null,Nothing,Option,Some,None,Either,Left,Right,StringContext}",
  "scala.annotation.tailrec",
  "scala.collection.immutable.{Map,Seq,List,::,Nil,Set,Vector}",
  "scala.util.{Try,Success,Failure}",
  "scala.Predef.{???,ArrowAssoc,identity,implicitly,<:<,=:=,augmentString,genericArrayOps}",
  "shapeless.{ :: => :*:, _ }",
  "_root_.io.circe",
  "scalaz._",
  "Scalaz._"
).mkString("import ", ",", "")

addCommandAlias("fmt", ";sbt:scalafmt ;scalafmt ;test:scalafmt")
