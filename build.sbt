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
addCommandAlias("lint", "all compile:scalafix test:scalafix")

libraryDependencies ++= Seq(
  "com.github.mpilquist" %% "simulacrum"            % "0.12.0",
  "com.chuusai"          %% "shapeless"             % "2.3.3",
  "xyz.driver"           %% "spray-json-derivation" % "0.1.1",
  "com.propensive"       %% "contextual"            % "1.1.0",
  "com.propensive"       %% "kaleidoscope"          % "0.1.0",
  "org.scalaz"           %% "scalaz-core"           % "7.2.19",
  "com.fommil"           %% "deriving-macro"        % "0.9.0",
  "com.fommil"           %% "scalaz-deriving"       % "0.9.0",
  "org.scalatest"        %% "scalatest"             % "3.0.5" % "test"
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

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")
addCompilerPlugin(
  ("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)
)

scalacOptions ++= {
  val dir = (baseDirectory in ThisBuild).value / "project"
  Seq(
    s"-Xmacro-settings:deriving.targets=$dir/deriving-targets.conf"
  )
}

scalacOptions in (Compile, console) -= "-Xfatal-warnings"
initialCommands in (Compile, console) := Seq(
  "java.lang.String",
  "scala.{Any,AnyRef,AnyVal,Boolean,Byte,Double,Float,Short,Int,Long,Char,Symbol,Unit,Null,Nothing,Option,Some,None,Either,Left,Right,StringContext}",
  "scala.annotation.tailrec",
  "scala.collection.immutable.{Map,Seq,List,::,Nil,Set,Vector}",
  "scala.util.{Try,Success,Failure}",
  "scala.Predef.{???,ArrowAssoc,identity,implicitly,<:<,=:=,augmentString,genericArrayOps}",
  "shapeless.{ :: => :*:, _ }",
  "scalaz._",
  "Scalaz._"
).mkString("import ", ",", "")
