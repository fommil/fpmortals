inThisBuild(
  Seq(
    startYear := Some(2017),
    scalaVersion := "2.12.6",
    sonatypeGithost := (Gitlab, "fommil", "drone-dynamic-agents"),
    sonatypeDevelopers := List("Sam Halliday"),
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
addCommandAlias("lint", "all compile:scalafixTest test:scalafixTest")
addCommandAlias("fix", "all compile:scalafixCli test:scalafixCli")

val derivingVersion = "0.14.0"
libraryDependencies ++= Seq(
  "com.github.mpilquist" %% "simulacrum"            % "0.12.0",
  "com.chuusai"          %% "shapeless"             % "2.3.3",
  "xyz.driver"           %% "spray-json-derivation" % "0.4.5",
  "eu.timepit"           %% "refined-scalaz"        % "0.9.0",
  "org.scalaz"           %% "scalaz-ioeffect"       % "2.7.0",
  "org.scalaz"           %% "scalaz-core"           % "7.2.25",
  "com.fommil"           %% "deriving-macro"        % derivingVersion % "provided",
  "com.fommil"           %% "scalaz-deriving"       % derivingVersion,
  "com.propensive"       %% "magnolia"              % "0.8.0",
  "com.propensive"       %% "contextual"            % "1.1.0",
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
  "-Yno-predef",
  "-Ywarn-unused:explicits,patvars,imports,privates,locals,implicits",
  "-opt:l:method,inline",
  "-opt-inline-from:scalaz.**"
)

addCompilerPlugin(
  ("org.scalameta" % "semanticdb-scalac" % "4.0.0-M1").cross(CrossVersion.full)
)

addCompilerPlugin("com.fommil" %% "deriving-plugin" % derivingVersion)

managedClasspath in Compile := {
  val res = (resourceDirectory in Compile).value
  val old = (managedClasspath in Compile).value
  Attributed.blank(res) +: old
}

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
addCompilerPlugin(
  ("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")

scalacOptions in (Compile, console) -= "-Yno-imports"
scalacOptions in (Compile, console) -= "-Yno-predef"
initialCommands in (Compile, console) := Seq(
  "scalaz._, Scalaz._",
  "shapeless._"
).mkString("import ", ",", "")
