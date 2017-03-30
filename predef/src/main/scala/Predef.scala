// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package fommil

import scala.inline

object Predef {
  type String = java.lang.String
  type Boolean = scala.Boolean
  type Byte = scala.Byte
  type Int = scala.Int
  type Long = scala.Long
  type Unit = scala.Unit
  type Nothing = scala.Nothing

  type Option[+T] = scala.Option[T]
  val Option = scala.Option
  type Some[+T] = scala.Some[T]
  // prefer Option(foo) to Some(foo) for better type inference
  // val Some = scala.Some
  val None = scala.None

  type Either[+A, +B] = scala.Either[A, B]
  type Left[+A, +B] = scala.Left[A, B]
  val Left = scala.Left
  type Right[+A, +B] = scala.Right[A, B]
  val Right = scala.Right

  type Map[K, +V] = scala.collection.immutable.Map[K, V]
  val Map = scala.collection.immutable.Map
  type Seq[+V] = scala.collection.immutable.Seq[V]
  val Seq = scala.collection.immutable.Seq
  type List[+V] = scala.collection.immutable.List[V]
  val List = scala.collection.immutable.List
  type Set[V] = scala.collection.immutable.Set[V]
  val Set = scala.collection.immutable.Set

  @inline implicit def ArrowAssoc[A](a: A): scala.Predef.ArrowAssoc[A] = scala.Predef.ArrowAssoc(a)

  @inline def ??? : Nothing = scala.Predef.???

}
