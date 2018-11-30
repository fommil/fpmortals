// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package pureconfig

import scala.{None, Right, Some}
import magnolia._
import mercator._
import pureconfig.error._

import scala.language.experimental.macros

// WORKAROUND: https://github.com/pureconfig/pureconfig/issues/396
object ConfigReaderMagnolia {
  type Typeclass[a] = ConfigReader[a]

  private val naming: ConfigFieldMapping =
    ConfigFieldMapping(CamelCase, KebabCase)

  def combine[A](ctx: CaseClass[ConfigReader, A]): ConfigReader[A] =
    c =>
      for {
        cur <- c.asObjectCursor
        bits <- ctx.constructMonadic { p =>
                 val key = naming(p.label)
                 val tc  = p.typeclass
                 cur.atKeyOrUndefined(key) match {
                   case undefined if undefined.isUndefined =>
                     p.default match {
                       case Some(v) => Right(v)
                       case None =>
                         tc match {
                           case _: AllowMissingKey => tc.from(undefined)
                           case _ =>
                             cur.failed(KeyNotFound.forKeys(key, cur.keys))
                         }
                     }
                   case defined => tc.from(defined)
                 }
               }
      } yield bits

  def dispatch[A](ctx: SealedTrait[ConfigReader, A]): ConfigReader[A] =
    c =>
      for {
        cur  <- c.asObjectCursor
        name <- cur.atKey("type").flatMap(_.asString)
        hit <- {
          ctx.subtypes
            .find(_.typeName.short.toLowerCase == name.toLowerCase) // scalafix:ok
            .toRight(ConfigReaderFailures(CannotParse(name, None)))
            .flatMap { s =>
              s.typeclass.from(c)
            }
        }
      } yield hit

  def gen[A]: ConfigReader[A] = macro Magnolia.gen[A]
}
