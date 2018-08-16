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

resourcesOnCompilerCp(Compile)

addCommandAlias("cpl", "all compile test:compile")
addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias(
  "check",
  "all headerCheck test:headerCheck scalafmtSbtCheck scalafmtCheck test:scalafmtCheck"
)
addCommandAlias("lint", "all compile:scalafixTest test:scalafixTest")
addCommandAlias("fix", "all compile:scalafixCli test:scalafixCli")

val http4sVersion = "0.18.16"
libraryDependencies ++= Seq(
  "com.github.mpilquist"  %% "simulacrum"          % "0.13.0",
  "com.chuusai"           %% "shapeless"           % "2.3.3",
  "eu.timepit"            %% "refined-scalaz"      % "0.9.2",
  "com.propensive"        %% "contextual"          % "1.1.0",
  "org.scalatest"         %% "scalatest"           % "3.0.5" % "test",
  "com.github.pureconfig" %% "pureconfig"          % "0.9.1",
  "org.http4s"            %% "http4s-dsl"          % http4sVersion,
  "org.http4s"            %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"            %% "http4s-blaze-client" % http4sVersion,
  // and because we're using http4s, all the compat stuff too...
  "com.codecommit" %% "shims"                % "1.4.0",
  "org.scalaz"     %% "scalaz-ioeffect-cats" % "2.10.1"
)

//val derivingVersion = "1.0.0-RC8"
val derivingVersion = "SNAPSHOT"
libraryDependencies ++= Seq(
  "com.fommil" %% "deriving-macro" % derivingVersion % "provided",
  compilerPlugin("com.fommil" %% "deriving-plugin" % derivingVersion),
  "com.fommil" %% "scalaz-deriving"            % derivingVersion,
  "com.fommil" %% "scalaz-deriving-magnolia"   % derivingVersion,
  "com.fommil" %% "scalaz-deriving-scalacheck" % derivingVersion,
  "com.fommil" %% "jsonformat"                 % derivingVersion
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

// http://www.scalatest.org/user_guide/using_the_runner
testOptions += Tests.Argument(
  TestFrameworks.ScalaTest,
  "-oD" // suppresses stack traces, shows durations
)

addCompilerPlugin(scalafixSemanticdb)
addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.7")
addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")
addCompilerPlugin(
  ("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)
)

scalacOptions.in(Compile, console) -= "-Yno-imports"
scalacOptions.in(Compile, console) -= "-Yno-predef"
initialCommands.in(Compile, console) := Seq(
  "scalaz._, Scalaz._"
).mkString("import ", ",", "")
