// this file exists only so I can have quick access to a REPL
// with the correct dependencies.

scalaVersion in ThisBuild := "2.12.4"
scalacOptions in ThisBuild ++= Seq(
  "-language:_",
  "-Ypartial-unification",
  "-deprecation"
)

libraryDependencies ++= Seq(
  "com.github.mpilquist" %% "simulacrum"            % "0.12.0",
  "com.chuusai"          %% "shapeless"             % "2.3.3",
  "com.fommil"           %% "deriving-macro"        % "0.9.0",
  "org.scalaz"           %% "scalaz-effect"         % "7.2.22",
  "org.scalaz"           %% "scalaz-ioeffect"       % "1.0.0",
  "eu.timepit"           %% "refined-scalaz"        % "0.9.0",
  "com.lihaoyi"          %% "sourcecode"            % "0.1.4",
  "xyz.driver"           %% "spray-json-derivation" % "0.4.1",
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")
addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.0")

scalacOptions in (Compile, console) -= "-Xfatal-warnings"
initialCommands in (Compile, console) := Seq(
  "shapeless._",
  "scalaz._, Scalaz._"
).mkString("import ", ",", "")

//scalafmtOnCompile in ThisBuild := true
scalafmtConfig in ThisBuild := Some(file("project/scalafmt.conf"))
