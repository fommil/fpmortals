// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

/** The predef for the project */
package object std {
  import scala.inline

  type String = java.lang.String

  type Any      = scala.Any
  type AnyRef   = scala.AnyRef
  type AnyVal   = scala.AnyVal
  type Boolean  = scala.Boolean
  type Byte     = scala.Byte
  type Double   = scala.Double
  type Float    = scala.Float
  type Short    = scala.Short
  type Int      = scala.Int
  type Long     = scala.Long
  type Char     = scala.Char
  type Symbol   = scala.Symbol
  type Unit     = scala.Unit
  type Null     = scala.Null
  type Nothing  = scala.Nothing
  type Array[A] = scala.Array[A]

  type StringContext = scala.StringContext
  @inline def StringContext(parts: String*): StringContext =
    new scala.StringContext(parts: _*)

  type Option[A] = scala.Option[A]
  @inline def Option = scala.Option
  type Some[A] = scala.Some[A]
  @inline def Some = scala.Some
  @inline def None = scala.None

  // type Either = scala.Either
  // type Left = scala.Left
  // type Right = scala.Right

  type Map[K, V] = scala.collection.immutable.Map[K, V]
  @inline def Map = scala.collection.immutable.Map
  type List[A] = scala.collection.immutable.List[A]
  @inline def List = scala.collection.immutable.List
  @inline def Nil  = scala.collection.immutable.Nil
  type Set[A] = scala.collection.immutable.Set[A]
  @inline def Set = scala.collection.immutable.Set
  type Seq[A] = scala.collection.immutable.Seq[A]
  @inline def Seq = scala.collection.immutable.Seq

  type Try[A] = scala.util.Try[A]
  @inline def Try[A](a: A): Try[A] = scala.util.Try(a)

  import scala.Predef
  // import scala.collection.{ immutable, mutable }

  @inline def ??? = Predef.???
  implicit final class ArrowAssoc[A](private val self: A) extends AnyVal {
    @inline def ->[B](y: B): (A, B) = (self, y)
  }
  @inline def identity[A](x: A): A = x
  //@inline def implicitly[T](implicit e: T) = e
  //@inline implicit def augmentString(x: String): immutable.StringOps = new immutable.StringOps(x)
  //@inline implicit def genericArrayOps[T](xs: Array[T]): mutable.ArrayOps[T] = Predef.genericArrayOps(xs)

  // third party libs
  type Refined[A, B] = eu.timepit.refined.api.Refined[A, B]
  //type |[A, B] = eu.timepit.refined.api.Refined

}
