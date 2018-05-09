// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

package fommil

/** The prelude for the project */
package object std {
  // primitive types
  type Any      = scala.Any // scalafix:ok Disable.Any
  type AnyRef   = scala.AnyRef // scalafix:ok Disable.AnyRef
  type AnyVal   = scala.AnyVal // scalafix:ok Disable.AnyVal
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

  // String interpolation
  type String        = java.lang.String
  type StringContext = scala.StringContext
  @inline final def StringContext(parts: String*): StringContext =
    new scala.StringContext(parts: _*)

  // annotations
  type inline  = scala.inline
  type tailrec = scala.annotation.tailrec
  type sp      = scala.specialized

  // java stdlib data types
  type Instant = java.time.Instant

  // scala stdlib data types
  //
  // Note that we try to avoid exposing subtypes, preferring to only see the ADT
  // and its constructors.
  type Option[A] = scala.Option[A]
  @inline final val Option: scala.Option.type = scala.Option
  @inline final val Some: scala.Some.type     = scala.Some
  @inline final val None: scala.None.type     = scala.None

  type Either[A, B] = scala.Either[A, B]
  @inline final val Left: scala.util.Left.type   = scala.Left
  @inline final val Right: scala.util.Right.type = scala.Right

  import scala.collection.immutable
  type Map[K, V] = immutable.Map[K, V]
  type List[A]   = immutable.List[A]
  type Set[A]    = immutable.Set[A]
  @inline final val Map: immutable.Map.type   = immutable.Map
  @inline final val List: immutable.List.type = immutable.List
  @inline final val Nil: immutable.Nil.type   = immutable.Nil
  @inline final val Set: immutable.Set.type   = immutable.Set

  type Try[A] = scala.util.Try[A]
  @inline final val Try: scala.util.Try.type = scala.util.Try

  // Predef things
  import scala.Predef
  @inline final def ??? : scala.Nothing      = Predef.???
  @inline final def identity[@sp A](x: A): A = x
  implicit final class ArrowAssoc[A](private val a: A) extends AnyVal {
    @inline final def ->[
      @sp(scala.Int, scala.Long, scala.Double, scala.Char, scala.Boolean) B
    ](b: B): scala.Tuple2[A, B] = scala.Tuple2(a, b)
  }

  // third party libs
  type Refined[A, B] = eu.timepit.refined.api.Refined[A, B]
  //type @:@[A, B] = eu.timepit.refined.api.Refined[A, B]

  // macro annotations don't work: https://github.com/scalamacros/paradise/issues/8
  // type typeclass = simulacrum.typeclass
  type deriving  = scalaz.deriving
  type xderiving = scalaz.xderiving

  // common scalaz typeclasses
  type Align[F[_]]            = scalaz.Align[F]
  type Applicative[F[_]]      = scalaz.Applicative[F]
  type ApplicativePlus[F[_]]  = scalaz.ApplicativePlus[F]
  type Alternative[F[_]]      = scalaz.ApplicativePlus[F]
  type Apply[F[_]]            = scalaz.Apply[F]
  type Band[A]                = scalaz.Band[A]
  type Bifoldable[F[_, _]]    = scalaz.Bifoldable[F]
  type Bifunctor[F[_, _]]     = scalaz.Bifunctor[F]
  type Bind[F[_]]             = scalaz.Bind[F]
  type Bitraverse[F[_, _]]    = scalaz.Bitraverse[F]
  type Contravariant[F[_]]    = scalaz.Contravariant[F]
  type Cozip[F[_]]            = scalaz.Cozip[F]
  type Divide[F[_]]           = scalaz.Divide[F]
  type Divisible[F[_]]        = scalaz.Divisible[F]
  type Enum[A]                = scalaz.Enum[A]
  type Equal[A]               = scalaz.Equal[A]
  type Foldable[F[_]]         = scalaz.Foldable[F]
  type Foldable1[F[_]]        = scalaz.Foldable1[F]
  type Functor[F[_]]          = scalaz.Functor[F]
  type InvariantFunctor[F[_]] = scalaz.InvariantFunctor[F]
  type Monad[F[_]]            = scalaz.Monad[F]
  type MonadError[F[_], E]    = scalaz.MonadError[F, E]
  type MonadListen[F[_], W]   = scalaz.MonadListen[F, W]
  type MonadPlus[F[_]]        = scalaz.MonadPlus[F]
  type MonadReader[F[_], S]   = scalaz.MonadReader[F, S]
  type MonadState[F[_], S]    = scalaz.MonadState[F, S]
  type MonadTell[F[_], S]     = scalaz.MonadTell[F, S]
  type MonadTrans[T[_[_], _]] = scalaz.MonadTrans[T]
  type Monoid[A]              = scalaz.Monoid[A]
  type Nondeterminism[F[_]]   = scalaz.Nondeterminism[F]
  type Optional[F[_]]         = scalaz.Optional[F]
  type Order[A]               = scalaz.Order[A]
  type Plus[F[_]]             = scalaz.Plus[F]
  type Semigroup[A]           = scalaz.Semigroup[A]
  type Show[A]                = scalaz.Show[A]
  type Traverse[F[_]]         = scalaz.Traverse[F]
  type Traverse1[F[_]]        = scalaz.Traverse1[F]
  type Unzip[F[_]]            = scalaz.Unzip[F]
  type Zip[F[_]]              = scalaz.Zip[F]
  @inline final val Align: scalaz.Align.type             = scalaz.Align
  @inline final val Applicative: scalaz.Applicative.type = scalaz.Applicative
  @inline final val ApplicativePlus: scalaz.ApplicativePlus.type =
    scalaz.ApplicativePlus
  @inline final val Alternative: scalaz.ApplicativePlus.type =
    scalaz.ApplicativePlus
  @inline final val Apply: scalaz.Apply.type           = scalaz.Apply
  @inline final val Band: scalaz.Band.type             = scalaz.Band
  @inline final val Bifoldable: scalaz.Bifoldable.type = scalaz.Bifoldable
  @inline final val Bifunctor: scalaz.Bifunctor.type   = scalaz.Bifunctor
  @inline final val Bind: scalaz.Bind.type             = scalaz.Bind
  @inline final val Bitraverse: scalaz.Bitraverse.type = scalaz.Bitraverse
  @inline final val Contravariant: scalaz.Contravariant.type =
    scalaz.Contravariant
  @inline final val Cozip: scalaz.Cozip.type         = scalaz.Cozip
  @inline final val Divide: scalaz.Divide.type       = scalaz.Divide
  @inline final val Divisible: scalaz.Divisible.type = scalaz.Divisible
  @inline final val Enum: scalaz.Enum.type           = scalaz.Enum
  @inline final val Equal: scalaz.Equal.type         = scalaz.Equal
  @inline final val Foldable: scalaz.Foldable.type   = scalaz.Foldable
  @inline final val Foldable1: scalaz.Foldable1.type = scalaz.Foldable1
  @inline final val Functor: scalaz.Functor.type     = scalaz.Functor
  @inline final val InvariantFunctor: scalaz.InvariantFunctor.type =
    scalaz.InvariantFunctor
  @inline final val Monad: scalaz.Monad.type             = scalaz.Monad
  @inline final val MonadError: scalaz.MonadError.type   = scalaz.MonadError
  @inline final val MonadListen: scalaz.MonadListen.type = scalaz.MonadListen
  @inline final val MonadPlus: scalaz.MonadPlus.type     = scalaz.MonadPlus
  @inline final val MonadReader: scalaz.MonadReader.type = scalaz.MonadReader
  @inline final val MonadState: scalaz.MonadState.type   = scalaz.MonadState
  @inline final val MonadTell: scalaz.MonadTell.type     = scalaz.MonadTell
  @inline final val MonadTrans: scalaz.MonadTrans.type   = scalaz.MonadTrans
  @inline final val Monoid: scalaz.Monoid.type           = scalaz.Monoid
  @inline final val Nondeterminism: scalaz.Nondeterminism.type =
    scalaz.Nondeterminism
  @inline final val Optional: scalaz.Optional.type   = scalaz.Optional
  @inline final val Order: scalaz.Order.type         = scalaz.Order
  @inline final val Plus: scalaz.Plus.type           = scalaz.Plus
  @inline final val Semigroup: scalaz.Semigroup.type = scalaz.Semigroup
  @inline final val Show: scalaz.Show.type           = scalaz.Show
  @inline final val Traverse: scalaz.Traverse.type   = scalaz.Traverse
  @inline final val Traverse1: scalaz.Traverse1.type = scalaz.Traverse1
  @inline final val Unzip: scalaz.Unzip.type         = scalaz.Unzip
  @inline final val Zip: scalaz.Zip.type             = scalaz.Zip

  // scalaz utilities
  type <~<[A, B]      = scalaz.Liskov.<~<[A, B]
  type ===[A, B]      = scalaz.Leibniz.===[A, B]
  type @@[T, Tag]     = scalaz.@@[T, Tag]
  type ~>[F[_], G[_]] = scalaz.NaturalTransformation[F, G]
  type Name[A]        = scalaz.Name[A]
  type Need[A]        = scalaz.Need[A]
  type Value[A]       = scalaz.Value[A]
  type Memo[K, V]     = scalaz.Memo[K, V]
  @inline final val Liskov: scalaz.Liskov.type   = scalaz.Liskov
  @inline final val Leibniz: scalaz.Leibniz.type = scalaz.Leibniz
  @inline final val Name: scalaz.Name.type       = scalaz.Name
  @inline final val Need: scalaz.Need.type       = scalaz.Need
  @inline final val Value: scalaz.Value.type     = scalaz.Value
  @inline final val Memo: scalaz.Memo.type       = scalaz.Memo

  // scalaz data types
  type Maybe[A]                 = scalaz.Maybe[A]
  type \/[A, B]                 = scalaz.\/[A, B]
  type Disjunction[A, B]        = scalaz.\/[A, B]
  type Validation[A, B]         = scalaz.Validation[A, B]
  type ValidationNel[E, X]      = scalaz.Validation[scalaz.NonEmptyList[E], X]
  type \?/[A, B]                = scalaz.Validation[A, B]
  type \&/[A, B]                = scalaz.\&/[A, B]
  type These[A, B]              = scalaz.\&/[A, B]
  type Const[A, B]              = scalaz.Const[A, B]
  type IList[A]                 = scalaz.IList[A]
  type NonEmptyList[A]          = scalaz.NonEmptyList[A]
  type EStream[A]               = scalaz.EphemeralStream[A]
  type CorecursiveList[A]       = scalaz.CorecursiveList[A]
  type ImmutableArray[A]        = scalaz.ImmutableArray[A]
  type Dequeue[A]               = scalaz.Dequeue[A]
  type DList[A]                 = scalaz.DList[A]
  type ISet[A]                  = scalaz.ISet[A]
  type ==>>[A, B]               = scalaz.==>>[A, B]
  type IMap[A, B]               = scalaz.==>>[A, B]
  type StrictTree[A]            = scalaz.StrictTree[A]
  type Tree[A]                  = scalaz.Tree[A]
  type FingerTree[V, A]         = scalaz.FingerTree[V, A]
  type Cord                     = scalaz.Cord
  type Heap[A]                  = scalaz.Heap[A]
  type Diev[A]                  = scalaz.Diev[A]
  type OneAnd[F[_], A]          = scalaz.OneAnd[F, A]
  type IO[A]                    = scalaz.effect.IO[A]
  type LiftIO[F[_]]             = scalaz.effect.LiftIO[F]
  type MonadIO[F[_]]            = scalaz.effect.MonadIO[F]
  type Free[S[_], A]            = scalaz.Free[S, A]
  type Inject[F[_], G[_]]       = scalaz.Inject[F, G]
  type :<:[F[_], G[_]]          = scalaz.Inject[F, G]
  type Coproduct[F[_], G[_], A] = scalaz.Coproduct[F, G, A]
  type Trampoline[A]            = scalaz.Free.Trampoline[A]
  @inline final val Maybe: scalaz.Maybe.type               = scalaz.Maybe
  @inline final val Disjunction: scalaz.\/.type            = scalaz.\/
  @inline final val Validation: scalaz.Validation.type     = scalaz.Validation
  @inline final val These: scalaz.\&/.type                 = scalaz.\&/
  @inline final val Const: scalaz.Const.type               = scalaz.Const
  @inline final val IList: scalaz.IList.type               = scalaz.IList
  @inline final val NonEmptyList: scalaz.NonEmptyList.type = scalaz.NonEmptyList
  @inline final val EStream: scalaz.EphemeralStream.type =
    scalaz.EphemeralStream
  @inline final val CorecursiveList: scalaz.CorecursiveList.type =
    scalaz.CorecursiveList
  @inline final val ImmutableArray: scalaz.ImmutableArray.type =
    scalaz.ImmutableArray
  @inline final val Dequeue: scalaz.Dequeue.type        = scalaz.Dequeue
  @inline final val DList: scalaz.DList.type            = scalaz.DList
  @inline final val ISet: scalaz.ISet.type              = scalaz.ISet
  @inline final val IMap: scalaz.==>>.type              = scalaz.==>>
  @inline final val StrictTree: scalaz.StrictTree.type  = scalaz.StrictTree
  @inline final val Tree: scalaz.Tree.type              = scalaz.Tree
  @inline final val FingerTree: scalaz.FingerTree.type  = scalaz.FingerTree
  @inline final val Cord: scalaz.Cord.type              = scalaz.Cord
  @inline final val Heap: scalaz.Heap.type              = scalaz.Heap
  @inline final val Diev: scalaz.Diev.type              = scalaz.Diev
  @inline final val OneAnd: scalaz.OneAnd.type          = scalaz.OneAnd
  @inline final val IO: scalaz.effect.IO.type           = scalaz.effect.IO
  @inline final val LiftIO: scalaz.effect.LiftIO.type   = scalaz.effect.LiftIO
  @inline final val MonadIO: scalaz.effect.MonadIO.type = scalaz.effect.MonadIO
  @inline final val Free: scalaz.Free.type              = scalaz.Free
  @inline final val Inject: scalaz.Inject.type          = scalaz.Inject
  @inline final val Coproduct: scalaz.Coproduct.type    = scalaz.Coproduct
  @inline final val Trampoline: scalaz.Trampoline.type  = scalaz.Trampoline

  // scalaz MTL
  type MaybeT[F[_], A]                = scalaz.MaybeT[F, A]
  type EitherT[F[_], A, B]            = scalaz.EitherT[F, A, B]
  type Kleisli[M[_], A, B]            = scalaz.Kleisli[M, A, B]
  type ReaderT[F[_], E, A]            = scalaz.Kleisli[F, E, A]
  type WriterT[F[_], W, A]            = scalaz.WriterT[F, W, A]
  type IndexedStateT[F[_], S1, S2, A] = scalaz.IndexedStateT[F, S1, S2, A]
  type StateT[F[_], S, A]             = scalaz.IndexedStateT[F, S, S, A]
  type State[S, A]                    = scalaz.IndexedStateT[scalaz.Id.Id, S, S, A]
  type TheseT[F[_], A, B]             = scalaz.TheseT[F, A, B]
  type StreamT[M[_], A]               = scalaz.StreamT[M, A]
  type ContT[M[_], R, A]              = scalaz.ContsT[scalaz.Id.Id, M, R, A]
  type IdT[F[_], A]                   = scalaz.IdT[F, A]
  @inline final val MaybeT: scalaz.MaybeT.type   = scalaz.MaybeT
  @inline final val EitherT: scalaz.EitherT.type = scalaz.EitherT
  @inline final val Kleisli: scalaz.Kleisli.type = scalaz.Kleisli
  @inline final val ReaderT: scalaz.Kleisli.type = scalaz.ReaderT
  @inline final val WriterT: scalaz.WriterT.type = scalaz.WriterT
  @inline final val IndexedStateT: scalaz.IndexedStateT.type =
    scalaz.IndexedStateT
  @inline final val StateT: scalaz.StateT.type   = scalaz.StateT
  @inline final val State: scalaz.State.type     = scalaz.State
  @inline final val TheseT: scalaz.TheseT.type   = scalaz.TheseT
  @inline final val StreamT: scalaz.StreamT.type = scalaz.StreamT
  @inline final val ContT: scalaz.ContT.type     = scalaz.ContT
  @inline final val IdT: scalaz.IdT.type         = scalaz.IdT

  // ADT constructors / deconstructors (types not exposed)
  @inline final val Just: scalaz.Maybe.Just.type   = scalaz.Maybe.Just
  @inline final val Empty: scalaz.Maybe.Empty.type = scalaz.Maybe.Empty
  @inline final val -\/ : scalaz.-\/.type          = scalaz.-\/
  @inline final val \/- : scalaz.\/-.type          = scalaz.\/-
  @inline final val ICons: scalaz.ICons.type       = scalaz.ICons
  @inline final val INil: scalaz.INil.type         = scalaz.INil

  // Minimal `scalaz.Scalaz`
  object Z
      extends scalaz.syntax.ToTypeClassOps
      with scalaz.syntax.ToDataOps
      with scalaz.IdInstances

  // Modularised `scalaz.Scalaz`, stdlib interop
  object S
      extends scalaz.std.AllInstances
      with scalaz.std.AllFunctions
      with scalaz.syntax.std.ToAllStdOps {
    type OptionT[F[_], A] = scalaz.OptionT[F, A]
    @inline final val OptionT: scalaz.OptionT.type = scalaz.OptionT
  }

}
