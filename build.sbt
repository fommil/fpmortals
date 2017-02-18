scalaVersion in ThisBuild := "2.12.1"

sonatypeGithub := ("fommil", "drone-dynamic-agents")
licenses := Seq(Apache2)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats" % "0.9.0"
)
