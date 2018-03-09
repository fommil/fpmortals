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
  @inline def Option: scala.Option.type = scala.Option
  @inline def Some: scala.Some.type     = scala.Some
  @inline def None: scala.None.type     = scala.None

  type Either[A, B] = scala.Either[A, B]
  @inline def Left: scala.util.Left.type   = scala.Left
  @inline def Right: scala.util.Right.type = scala.Right

  import scala.collection.immutable
  type Map[K, V] = immutable.Map[K, V]
  type List[A]   = immutable.List[A]
  type Set[A]    = immutable.Set[A]
  @inline def Map: scala.collection.immutable.Map.type   = immutable.Map
  @inline def List: scala.collection.immutable.List.type = immutable.List
  @inline def Nil: scala.collection.immutable.Nil.type   = immutable.Nil
  @inline def Set: scala.collection.immutable.Set.type   = immutable.Set

  type Try[A] = scala.util.Try[A]
  @inline def Try: scala.util.Try.type = scala.util.Try

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
  @inline def Align: scalaz.Align.type             = scalaz.Align
  @inline def Applicative: scalaz.Applicative.type = scalaz.Applicative
  @inline def ApplicativePlus: scalaz.ApplicativePlus.type =
    scalaz.ApplicativePlus
  @inline def Alternative: scalaz.ApplicativePlus.type = scalaz.ApplicativePlus
  @inline def Apply: scalaz.Apply.type                 = scalaz.Apply
  @inline def Band: scalaz.Band.type                   = scalaz.Band
  @inline def Bifoldable: scalaz.Bifoldable.type       = scalaz.Bifoldable
  @inline def Bifunctor: scalaz.Bifunctor.type         = scalaz.Bifunctor
  @inline def Bind: scalaz.Bind.type                   = scalaz.Bind
  @inline def Bitraverse: scalaz.Bitraverse.type       = scalaz.Bitraverse
  @inline def Contravariant: scalaz.Contravariant.type = scalaz.Contravariant
  @inline def Cozip: scalaz.Cozip.type                 = scalaz.Cozip
  @inline def Divide: scalaz.Divide.type               = scalaz.Divide
  @inline def Divisible: scalaz.Divisible.type         = scalaz.Divisible
  @inline def Enum: scalaz.Enum.type                   = scalaz.Enum
  @inline def Equal: scalaz.Equal.type                 = scalaz.Equal
  @inline def Foldable: scalaz.Foldable.type           = scalaz.Foldable
  @inline def Foldable1: scalaz.Foldable1.type         = scalaz.Foldable1
  @inline def Functor: scalaz.Functor.type             = scalaz.Functor
  @inline def InvariantFunctor: scalaz.InvariantFunctor.type =
    scalaz.InvariantFunctor
  @inline def Monad: scalaz.Monad.type                   = scalaz.Monad
  @inline def MonadError: scalaz.MonadError.type         = scalaz.MonadError
  @inline def MonadListen: scalaz.MonadListen.type       = scalaz.MonadListen
  @inline def MonadPlus: scalaz.MonadPlus.type           = scalaz.MonadPlus
  @inline def MonadReader: scalaz.MonadReader.type       = scalaz.MonadReader
  @inline def MonadState: scalaz.MonadState.type         = scalaz.MonadState
  @inline def MonadTell: scalaz.MonadTell.type           = scalaz.MonadTell
  @inline def MonadTrans: scalaz.MonadTrans.type         = scalaz.MonadTrans
  @inline def Monoid: scalaz.Monoid.type                 = scalaz.Monoid
  @inline def Nondeterminism: scalaz.Nondeterminism.type = scalaz.Nondeterminism
  @inline def Optional: scalaz.Optional.type             = scalaz.Optional
  @inline def Order: scalaz.Order.type                   = scalaz.Order
  @inline def Plus: scalaz.Plus.type                     = scalaz.Plus
  @inline def Semigroup: scalaz.Semigroup.type           = scalaz.Semigroup
  @inline def Show: scalaz.Show.type                     = scalaz.Show
  @inline def Traverse: scalaz.Traverse.type             = scalaz.Traverse
  @inline def Traverse1: scalaz.Traverse1.type           = scalaz.Traverse1
  @inline def Unzip: scalaz.Unzip.type                   = scalaz.Unzip
  @inline def Zip: scalaz.Zip.type                       = scalaz.Zip

  // scalaz utilities
  type <~<[A, B]      = scalaz.Liskov.<~<[A, B]
  type ===[A, B]      = scalaz.Leibniz.===[A, B]
  type @@[T, Tag]     = scalaz.@@[T, Tag]
  type ~>[F[_], G[_]] = scalaz.NaturalTransformation[F, G]
  type Name[A]        = scalaz.Name[A]
  type Need[A]        = scalaz.Need[A]
  type Value[A]       = scalaz.Value[A]
  type Memo[K, V]     = scalaz.Memo[K, V]
  @inline def Liskov: scalaz.Liskov.type   = scalaz.Liskov
  @inline def Leibniz: scalaz.Leibniz.type = scalaz.Leibniz
  @inline def Name: scalaz.Name.type       = scalaz.Name
  @inline def Need: scalaz.Need.type       = scalaz.Need
  @inline def Value: scalaz.Value.type     = scalaz.Value
  @inline def Memo: scalaz.Memo.type       = scalaz.Memo

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
  @inline def Maybe: scalaz.Maybe.type               = scalaz.Maybe
  @inline def Disjunction: scalaz.\/.type            = scalaz.\/
  @inline def Validation: scalaz.Validation.type     = scalaz.Validation
  @inline def These: scalaz.\&/.type                 = scalaz.\&/
  @inline def Const: scalaz.Const.type               = scalaz.Const
  @inline def IList: scalaz.IList.type               = scalaz.IList
  @inline def NonEmptyList: scalaz.NonEmptyList.type = scalaz.NonEmptyList
  @inline def EStream: scalaz.EphemeralStream.type   = scalaz.EphemeralStream
  @inline def CorecursiveList: scalaz.CorecursiveList.type =
    scalaz.CorecursiveList
  @inline def ImmutableArray: scalaz.ImmutableArray.type = scalaz.ImmutableArray
  @inline def Dequeue: scalaz.Dequeue.type               = scalaz.Dequeue
  @inline def DList: scalaz.DList.type                   = scalaz.DList
  @inline def ISet: scalaz.ISet.type                     = scalaz.ISet
  @inline def IMap: scalaz.==>>.type                     = scalaz.==>>
  @inline def StrictTree: scalaz.StrictTree.type         = scalaz.StrictTree
  @inline def Tree: scalaz.Tree.type                     = scalaz.Tree
  @inline def FingerTree: scalaz.FingerTree.type         = scalaz.FingerTree
  @inline def Cord: scalaz.Cord.type                     = scalaz.Cord
  @inline def Heap: scalaz.Heap.type                     = scalaz.Heap
  @inline def Diev: scalaz.Diev.type                     = scalaz.Diev
  @inline def OneAnd: scalaz.OneAnd.type                 = scalaz.OneAnd
  @inline def IO: scalaz.effect.IO.type                  = scalaz.effect.IO
  @inline def Free: scalaz.Free.type                     = scalaz.Free
  @inline def Trampoline: scalaz.Trampoline.type         = scalaz.Trampoline

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
  @inline def MaybeT: scalaz.MaybeT.type               = scalaz.MaybeT
  @inline def EitherT: scalaz.EitherT.type             = scalaz.EitherT
  @inline def Kleisli: scalaz.Kleisli.type             = scalaz.Kleisli
  @inline def ReaderT: scalaz.Kleisli.type             = scalaz.ReaderT
  @inline def WriterT: scalaz.WriterT.type             = scalaz.WriterT
  @inline def IndexedStateT: scalaz.IndexedStateT.type = scalaz.IndexedStateT
  @inline def StateT: scalaz.`package`.StateT.type     = scalaz.StateT
  @inline def TheseT: scalaz.TheseT.type               = scalaz.TheseT
  @inline def StreamT: scalaz.StreamT.type             = scalaz.StreamT
  @inline def ContT: scalaz.`package`.ContT.type       = scalaz.ContT
  @inline def IdT: scalaz.IdT.type                     = scalaz.IdT

  // ADT constructors / deconstructors (types not exposed)
  @inline def Just: scalaz.Maybe.Just.type   = scalaz.Maybe.Just
  @inline def Empty: scalaz.Maybe.Empty.type = scalaz.Maybe.Empty
  @inline def -\/ : scalaz.-\/.type          = scalaz.-\/
  @inline def \/- : scalaz.\/-.type          = scalaz.\/-
  @inline def ICons: scalaz.ICons.type       = scalaz.ICons
  @inline def INil: scalaz.INil.type         = scalaz.INil

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
