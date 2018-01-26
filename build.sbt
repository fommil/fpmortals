inThisBuild(
  Seq(
    startYear := Some(2017),
    scalaVersion := "2.12.4",
    sonatypeGithost := (Gitlab, "fommil", "drone-dynamic-agents"),
    licenses := Seq(GPL3),
    scalafmtConfig := Some(file("project/scalafmt.conf")),
    scalafixConfig := Some(file("project/scalafix.conf"))
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias(
  "check",
  "all headerCheck test:headerCheck scalafmtSbtCheck scalafmtCheck test:scalafmtCheck"
)

scalafixEnabled := true
addCommandAlias("lint", "all compile:scalafixCli test:scalafixCli")

val circeVersion = "0.8.0"
libraryDependencies ++= Seq(
  "com.fommil"           %% "stalactite"    % "0.0.5",
  "com.github.mpilquist" %% "simulacrum"    % "0.11.0",
  "com.chuusai"          %% "shapeless"     % "2.3.3",
  "io.circe"             %% "circe-core"    % circeVersion,
  "io.circe"             %% "circe-generic" % circeVersion,
  "io.circe"             %% "circe-parser"  % circeVersion,
  "io.circe"             %% "circe-fs2"     % circeVersion,
  "org.scalaz"           %% "scalaz-core"   % "7.2.18",
  "com.spinoco"          %% "fs2-http"      % "0.1.8"
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

scalacOptions -= "-Ywarn-unused:implicits,imports,-locals,-params,-patvars,-privates"
scalacOptions += "-Ywarn-unused:explicits,patvars,linted"

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.5")
addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
)

scalacOptions ++= {
  val dir = (baseDirectory in ThisBuild).value / "project"
  Seq(
    s"-Xmacro-settings:stalactite.targets=$dir/stalactite-targets.conf",
    s"-Xmacro-settings:stalactite.defaults=$dir/stalactite-defaults.conf"
  )
}

wartremoverWarnings in (Compile, compile) := Warts.unsafe ++ Seq(
  Wart.FinalCaseClass,
  Wart.ExplicitImplicitTypes
)
wartremoverWarnings in (Compile, compile) -= Wart.DefaultArguments // not sure I agree with this one...
wartremoverWarnings in (Compile, compile) -= Wart.Any              // too many false positives

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
