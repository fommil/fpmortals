// Copyright: 2017 Sam Halliday
// License: https://creativecommons.org/publicdomain/zero/1.0/
package validation

import scalaz._, Scalaz._

final case class Credentials(user: Username, name: Fullname)
final case class Username(value: String) extends AnyVal
final case class Fullname(value: String) extends AnyVal

object Disjunction extends App {

  def username(in: String): String \/ Username =
    if (in.isEmpty) "empty username".left
    else if (in.contains(" ")) "username contains spaces".left
    else Username(in).right

  def realname(in: String): String \/ Fullname =
    if (in.isEmpty) "empty real name".left
    else Fullname(in).right

  val creds = for {
    u <- username("sam halliday")
    r <- realname("")
  } yield Credentials(u, r)

  println(creds)

  val creds2 = (username("sam halliday") |@| realname(""))(Credentials.apply)

  println(creds2)
}

object Validated extends App {

  type Result[A] = ValidationNel[String, A]

  def username(in: String): Result[Username] =
    if (in.isEmpty) "empty username".failureNel
    else if (in.contains(" ")) "username contains spaces".failureNel
    else Username(in).success

  def realname(in: String): Result[Fullname] =
    if (in.isEmpty) "empty real name".failureNel
    else Fullname(in).success

  val creds2 = (username("sam halliday") |@| realname(""))(Credentials.apply)

  println(creds2)
  // Failure(NonEmpty[username contains spaces,empty real name])
}
