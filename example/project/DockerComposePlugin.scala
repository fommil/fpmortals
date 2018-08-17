package fommil

import scala.util.Try
import scala.sys.process._

import sbt._
import sbt.Keys._

// a stripped down version of
// https://github.com/cakesolutions/sbt-cake/blob/master/src/main/scala/net/cakesolutions/CakeDockerComposePlugin.scala
// only supporting external services for integration tests (not bringing up our own services)
object DockerComposePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
    val dockerComposeFile =
      settingKey[File]("docker-compose.yml file to use in dockerComposeUp.")
    val dockerComposeUp = taskKey[Unit](
      "Builds the images and then runs `docker-compose -f <file> up -d` for the scope"
    )
    val dockerComposeDown =
      taskKey[Unit]("Runs `docker-compose -f <file> down` for the scope")

    // manually added per test inConfig
    def dockerComposeSettings = Seq(
      dockerComposeUp := dockerComposeUpTask.value,
      dockerComposeDown := dockerComposeDownTask.value,
      dockerComposeFile := file(
        s"docker/${name.value}-${configuration.value}.yml"
      )
    )
  }
  import autoImport._

  val dockerComposeUpTask: Def.Initialize[Task[Unit]] = Def.task {
    val input = dockerComposeFile.value.getCanonicalPath
    val res   = s"docker-compose -f $input up -d".!
    if (res != 0)
      throw new IllegalStateException(s"docker-compose up returned $res")
  }

  val dockerComposeDownTask: Def.Initialize[Task[Unit]] = Def.task {
    val input = dockerComposeFile.value.getCanonicalPath
    s"docker-compose -f $input kill -s 9".! // much faster
    val res = s"docker-compose -f $input down -v".!
    if (res != 0)
      throw new IllegalStateException(s"docker-compose down returned $res")
  }

}
