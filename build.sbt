// this file exists only so I can have quick access to a REPL
// with the correct dependencies.

addCommandAlias("cpl", "all compile")
addCommandAlias("fmt", "all scalafmtSbt scalafmt")

scalaVersion in ThisBuild := "2.12.7"
scalacOptions in ThisBuild ++= Seq(
  "-language:_",
  //"-Xsource:2.13",
  "-Ypartial-unification",
  "-deprecation"
)

libraryDependencies ++= Seq(
  "com.github.mpilquist" %% "simulacrum"      % "0.14.0",
  "com.chuusai"          %% "shapeless"       % "2.3.3",
  "org.scalaz"           %% "scalaz-effect"   % "7.2.27",
  "org.scalaz"           %% "scalaz-ioeffect" % "2.10.1",
  "eu.timepit"           %% "refined-scalaz"  % "0.9.3",
  "com.lihaoyi"          %% "sourcecode"      % "0.1.5",
  "io.estatico"          %% "newtype"         % "0.4.2"
)

val derivingVersion = "1.0.0"
libraryDependencies ++= Seq(
  "org.scalaz" %% "deriving-macro" % derivingVersion,
  compilerPlugin("org.scalaz" %% "deriving-plugin" % derivingVersion),
  "org.scalaz" %% "scalaz-deriving"            % derivingVersion,
  "org.scalaz" %% "scalaz-deriving-magnolia"   % derivingVersion,
  "org.scalaz" %% "scalaz-deriving-scalacheck" % derivingVersion
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9")
addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")

scalacOptions in (Compile, console) -= "-Xfatal-warnings"
initialCommands in (Compile, console) := Seq(
  "scalaz._, Scalaz._"
).mkString("import ", ",", "")

//scalafmtOnCompile in ThisBuild := true
scalafmtConfig in ThisBuild := Some(file("project/scalafmt.conf"))
scalafmtOnCompile in ThisBuild := true
