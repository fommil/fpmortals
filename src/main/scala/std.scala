// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

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
  @inline def StringContext(parts: String*): StringContext =
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
  val Option: scala.Option.type = scala.Option
  val Some: scala.Some.type     = scala.Some
  val None: scala.None.type     = scala.None

  type Either[A, B] = scala.Either[A, B]
  val Left: scala.util.Left.type   = scala.Left
  val Right: scala.util.Right.type = scala.Right

  import scala.collection.immutable
  type Map[K, V] = immutable.Map[K, V]
  type List[A]   = immutable.List[A]
  type Set[A]    = immutable.Set[A]
  val Map: scala.collection.immutable.Map.type   = immutable.Map
  val List: scala.collection.immutable.List.type = immutable.List
  val Nil: scala.collection.immutable.Nil.type   = immutable.Nil
  val Set: scala.collection.immutable.Set.type   = immutable.Set

  type Try[A] = scala.util.Try[A]
  val Try: scala.util.Try.type = scala.util.Try

  // Predef things
  import scala.Predef
  @inline def ??? : scala.Nothing      = Predef.???
  @inline def identity[@sp A](x: A): A = x
  implicit final class ArrowAssoc[A](private val a: A) extends AnyVal {
    @inline def ->[
      @sp(scala.Int, scala.Long, scala.Double, scala.Char, scala.Boolean) B
    ](b: B): scala.Tuple2[A, B] = scala.Tuple2(a, b)
  }

  // third party libs
  type Refined[A, B] = eu.timepit.refined.api.Refined[A, B]
  //type @:@[A, B] = eu.timepit.refined.api.Refined[A, B]

  // macro annotations don't work: https://github.com/scalamacros/paradise/issues/8
  // type typeclass = simulacrum.typeclass
  // type deriving = scalaz.deriving

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
  val Align: scalaz.Align.type                       = scalaz.Align
  val Applicative: scalaz.Applicative.type           = scalaz.Applicative
  val ApplicativePlus: scalaz.ApplicativePlus.type   = scalaz.ApplicativePlus
  val Alternative: scalaz.ApplicativePlus.type       = scalaz.ApplicativePlus
  val Apply: scalaz.Apply.type                       = scalaz.Apply
  val Band: scalaz.Band.type                         = scalaz.Band
  val Bifoldable: scalaz.Bifoldable.type             = scalaz.Bifoldable
  val Bifunctor: scalaz.Bifunctor.type               = scalaz.Bifunctor
  val Bind: scalaz.Bind.type                         = scalaz.Bind
  val Bitraverse: scalaz.Bitraverse.type             = scalaz.Bitraverse
  val Contravariant: scalaz.Contravariant.type       = scalaz.Contravariant
  val Cozip: scalaz.Cozip.type                       = scalaz.Cozip
  val Divide: scalaz.Divide.type                     = scalaz.Divide
  val Divisible: scalaz.Divisible.type               = scalaz.Divisible
  val Enum: scalaz.Enum.type                         = scalaz.Enum
  val Equal: scalaz.Equal.type                       = scalaz.Equal
  val Foldable: scalaz.Foldable.type                 = scalaz.Foldable
  val Foldable1: scalaz.Foldable1.type               = scalaz.Foldable1
  val Functor: scalaz.Functor.type                   = scalaz.Functor
  val InvariantFunctor: scalaz.InvariantFunctor.type = scalaz.InvariantFunctor
  val Monad: scalaz.Monad.type                       = scalaz.Monad
  val MonadError: scalaz.MonadError.type             = scalaz.MonadError
  val MonadListen: scalaz.MonadListen.type           = scalaz.MonadListen
  val MonadPlus: scalaz.MonadPlus.type               = scalaz.MonadPlus
  val MonadReader: scalaz.MonadReader.type           = scalaz.MonadReader
  val MonadState: scalaz.MonadState.type             = scalaz.MonadState
  val MonadTell: scalaz.MonadTell.type               = scalaz.MonadTell
  val MonadTrans: scalaz.MonadTrans.type             = scalaz.MonadTrans
  val Monoid: scalaz.Monoid.type                     = scalaz.Monoid
  val Nondeterminism: scalaz.Nondeterminism.type     = scalaz.Nondeterminism
  val Optional: scalaz.Optional.type                 = scalaz.Optional
  val Order: scalaz.Order.type                       = scalaz.Order
  val Plus: scalaz.Plus.type                         = scalaz.Plus
  val Semigroup: scalaz.Semigroup.type               = scalaz.Semigroup
  val Show: scalaz.Show.type                         = scalaz.Show
  val Traverse: scalaz.Traverse.type                 = scalaz.Traverse
  val Traverse1: scalaz.Traverse1.type               = scalaz.Traverse1
  val Unzip: scalaz.Unzip.type                       = scalaz.Unzip
  val Zip: scalaz.Zip.type                           = scalaz.Zip

  // scalaz utilities
  type <~<[A, B]      = scalaz.Liskov.<~<[A, B]
  type ===[A, B]      = scalaz.Leibniz.===[A, B]
  type @@[T, Tag]     = scalaz.@@[T, Tag]
  type ~>[F[_], G[_]] = scalaz.NaturalTransformation[F, G]
  type Name[A]        = scalaz.Name[A]
  type Need[A]        = scalaz.Need[A]
  type Value[A]       = scalaz.Value[A]
  type Memo[K, V]     = scalaz.Memo[K, V]
  val Liskov: scalaz.Liskov.type   = scalaz.Liskov
  val Leibniz: scalaz.Leibniz.type = scalaz.Leibniz
  val Name: scalaz.Name.type       = scalaz.Name
  val Need: scalaz.Need.type       = scalaz.Need
  val Value: scalaz.Value.type     = scalaz.Value
  val Memo: scalaz.Memo.type       = scalaz.Memo

  // scalaz data types
  type Maybe[A]            = scalaz.Maybe[A]
  type \/[A, B]            = scalaz.\/[A, B]
  type Disjunction[A, B]   = scalaz.\/[A, B]
  type Validation[A, B]    = scalaz.Validation[A, B]
  type ValidationNel[E, X] = scalaz.Validation[scalaz.NonEmptyList[E], X]
  type \?/[A, B]           = scalaz.Validation[A, B]
  type \&/[A, B]           = scalaz.\&/[A, B]
  type These[A, B]         = scalaz.\&/[A, B]
  type Const[A, B]         = scalaz.Const[A, B]
  type IList[A]            = scalaz.IList[A]
  type NonEmptyList[A]     = scalaz.NonEmptyList[A]
  type EStream[A]          = scalaz.EphemeralStream[A]
  type CorecursiveList[A]  = scalaz.CorecursiveList[A]
  type ImmutableArray[A]   = scalaz.ImmutableArray[A]
  type Dequeue[A]          = scalaz.Dequeue[A]
  type DList[A]            = scalaz.DList[A]
  type ISet[A]             = scalaz.ISet[A]
  type ==>>[A, B]          = scalaz.==>>[A, B]
  type IMap[A, B]          = scalaz.==>>[A, B]
  type StrictTree[A]       = scalaz.StrictTree[A]
  type Tree[A]             = scalaz.Tree[A]
  type FingerTree[V, A]    = scalaz.FingerTree[V, A]
  type Cord                = scalaz.Cord
  type Heap[A]             = scalaz.Heap[A]
  type Diev[A]             = scalaz.Diev[A]
  type OneAnd[F[_], A]     = scalaz.OneAnd[F, A]
  type IO[A]               = scalaz.effect.IO[A]
  type Free[S[_], A]       = scalaz.Free[S, A]
  type Trampoline[A]       = scalaz.Free.Trampoline[A]
  val Maybe: scalaz.Maybe.type                     = scalaz.Maybe
  val Disjunction: scalaz.\/.type                  = scalaz.\/
  val Validation: scalaz.Validation.type           = scalaz.Validation
  val These: scalaz.\&/.type                       = scalaz.\&/
  val Const: scalaz.Const.type                     = scalaz.Const
  val IList: scalaz.IList.type                     = scalaz.IList
  val NonEmptyList: scalaz.NonEmptyList.type       = scalaz.NonEmptyList
  val EStream: scalaz.EphemeralStream.type         = scalaz.EphemeralStream
  val CorecursiveList: scalaz.CorecursiveList.type = scalaz.CorecursiveList
  val ImmutableArray: scalaz.ImmutableArray.type   = scalaz.ImmutableArray
  val Dequeue: scalaz.Dequeue.type                 = scalaz.Dequeue
  val DList: scalaz.DList.type                     = scalaz.DList
  val ISet: scalaz.ISet.type                       = scalaz.ISet
  val IMap: scalaz.==>>.type                       = scalaz.==>>
  val StrictTree: scalaz.StrictTree.type           = scalaz.StrictTree
  val Tree: scalaz.Tree.type                       = scalaz.Tree
  val FingerTree: scalaz.FingerTree.type           = scalaz.FingerTree
  val Cord: scalaz.Cord.type                       = scalaz.Cord
  val Heap: scalaz.Heap.type                       = scalaz.Heap
  val Diev: scalaz.Diev.type                       = scalaz.Diev
  val OneAnd: scalaz.OneAnd.type                   = scalaz.OneAnd
  val IO: scalaz.effect.IO.type                    = scalaz.effect.IO
  val Free: scalaz.Free.type                       = scalaz.Free
  val Trampoline: scalaz.Trampoline.type           = scalaz.Trampoline

  // scalaz MTL
  type MaybeT[F[_], A]                = scalaz.MaybeT[F, A]
  type EitherT[F[_], A, B]            = scalaz.EitherT[F, A, B]
  type Kleisli[M[_], A, B]            = scalaz.Kleisli[M, A, B]
  type ReaderT[F[_], E, A]            = scalaz.Kleisli[F, E, A]
  type WriterT[F[_], W, A]            = scalaz.WriterT[F, W, A]
  type IndexedStateT[F[_], S1, S2, A] = scalaz.IndexedStateT[F, S1, S2, A]
  type StateT[F[_], S, A]             = scalaz.IndexedStateT[F, S, S, A]
  type TheseT[F[_], A, B]             = scalaz.TheseT[F, A, B]
  type StreamT[M[_], A]               = scalaz.StreamT[M, A]
  type ContT[M[_], R, A]              = scalaz.ContsT[scalaz.Id.Id, M, R, A]
  type IdT[F[_], A]                   = scalaz.IdT[F, A]
  val MaybeT: scalaz.MaybeT.type               = scalaz.MaybeT
  val EitherT: scalaz.EitherT.type             = scalaz.EitherT
  val Kleisli: scalaz.Kleisli.type             = scalaz.Kleisli
  val ReaderT: scalaz.Kleisli.type             = scalaz.ReaderT
  val WriterT: scalaz.WriterT.type             = scalaz.WriterT
  val IndexedStateT: scalaz.IndexedStateT.type = scalaz.IndexedStateT
  val StateT: scalaz.`package`.StateT.type     = scalaz.StateT
  val TheseT: scalaz.TheseT.type               = scalaz.TheseT
  val StreamT: scalaz.StreamT.type             = scalaz.StreamT
  val ContT: scalaz.`package`.ContT.type       = scalaz.ContT
  val IdT: scalaz.IdT.type                     = scalaz.IdT

  // ADT constructors / deconstructors (types not exposed)
  val Just: scalaz.Maybe.Just.type   = scalaz.Maybe.Just
  val Empty: scalaz.Maybe.Empty.type = scalaz.Maybe.Empty
  val -\/ : scalaz.-\/.type          = scalaz.-\/
  val \/- : scalaz.\/-.type          = scalaz.\/-
  val ICons: scalaz.ICons.type       = scalaz.ICons
  val INil: scalaz.INil.type         = scalaz.INil

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
  }

}
