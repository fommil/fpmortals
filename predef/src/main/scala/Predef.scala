// Copyright: 2017 https://github.com/fommil/drone-dynamic-agents/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package fommil

import scala.language.experimental.macros

object Predef {
  // maybe a step too far?
  def cachedImplicit[T]: T = macro shapeless.CachedImplicitMacros.cachedImplicitImpl[T]

  type String = java.lang.String
  type Boolean = scala.Boolean
  type Int = scala.Int
  type Long = scala.Long
  type Unit = scala.Unit
  type Nothing = scala.Nothing

  type Option[T] = scala.Option[T]
  @inline def Option = scala.Option
  type Some[T] = scala.Some[T]
  // prefer Option(foo) to Some(foo) for better type inference
  // @inline def Some = scala.Some
  @inline def None = scala.None

  type Map[K, V] = scala.collection.immutable.Map[K, V]
  @inline def Map = scala.collection.immutable.Map
  type Seq[V] = scala.collection.immutable.Seq[V]
  @inline def Seq = scala.collection.immutable.Seq
  type List[V] = scala.collection.immutable.List[V]
  @inline def List = scala.collection.immutable.List
  type Set[V] = scala.collection.immutable.Set[V]
  @inline def Set = scala.collection.immutable.Set

  @inline implicit def ArrowAssoc[A](a: A): scala.Predef.ArrowAssoc[A] = scala.Predef.ArrowAssoc(a)

  @inline def ??? : Nothing = scala.Predef.???

}
