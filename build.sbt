inThisBuild(
  Seq(
    startYear := Some(2017),
    scalaVersion := "2.12.4",
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
addCommandAlias("lint", "all compile:scalafix test:scalafix")

val derivingVersion = "0.13.0"
libraryDependencies ++= Seq(
  "com.github.mpilquist" %% "simulacrum"            % "0.12.0",
  "com.chuusai"          %% "shapeless"             % "2.3.3",
  "xyz.driver"           %% "spray-json-derivation" % "0.4.1",
  "eu.timepit"           %% "refined-scalaz"        % "0.8.7",
  "org.scalaz"           %% "scalaz-effect"         % "7.2.21",
  "com.fommil"           %% "deriving-macro"        % derivingVersion,
  "com.fommil"           %% "scalaz-deriving"       % derivingVersion,
  "com.propensive"       %% "magnolia"              % "0.7.1",
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

addCompilerPlugin(scalafixSemanticdb)

addCompilerPlugin("com.fommil" %% "deriving-plugin" % derivingVersion)

managedClasspath in Compile := {
  val res = (resourceDirectory in Compile).value
  val old = (managedClasspath in Compile).value
  Attributed.blank(res) +: old
}

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")
addCompilerPlugin(
  ("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)
)

resolvers += Resolver.sonatypeRepo("snapshots")
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.0-SNAPSHOT")

scalacOptions in (Compile, console) -= "-Xfatal-warnings"
scalacOptions in (Compile, console) -= "-Yno-imports"
scalacOptions in (Compile, console) -= "-Yno-predef"
initialCommands in (Compile, console) := Seq(
  "scalaz._, Scalaz._",
  "shapeless._"
).mkString("import ", ",", "")
