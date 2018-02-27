// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/gpl-3.0.en.html

/** The prelude for the project */
package object std {
  // primitive types
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
  object Instant {
    @inline def parse(iso: String): Instant = java.time.Instant.parse(iso)
  }

  // scala stdlib data types
  //
  // Note that we try to avoid exposing subtypes, preferring to only see the ADT
  // and its constructors.
  type Option[A] = scala.Option[A]
  val Option = scala.Option
  val Some   = scala.Some
  val None   = scala.None

  type Either[A, B] = scala.Either[A, B]
  val Left  = scala.Left
  val Right = scala.Right

  import scala.collection.immutable
  type Map[K, V] = immutable.Map[K, V]
  type List[A]   = immutable.List[A]
  type Set[A]    = immutable.Set[A]
  type Seq[A]    = immutable.Seq[A]
  val Map  = immutable.Map
  val List = immutable.List
  val Nil  = immutable.Nil
  val Set  = immutable.Set
  val Seq  = immutable.Seq

  type Try[A] = scala.util.Try[A]
  val Try = scala.util.Try

  // Predef things
  import scala.Predef
  @inline def ???                      = Predef.???
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
  val Align            = scalaz.Align
  val Applicative      = scalaz.Applicative
  val ApplicativePlus  = scalaz.ApplicativePlus
  val Alternative      = scalaz.ApplicativePlus
  val Apply            = scalaz.Apply
  val Band             = scalaz.Band
  val Bifoldable       = scalaz.Bifoldable
  val Bifunctor        = scalaz.Bifunctor
  val Bind             = scalaz.Bind
  val Bitraverse       = scalaz.Bitraverse
  val Contravariant    = scalaz.Contravariant
  val Cozip            = scalaz.Cozip
  val Divide           = scalaz.Divide
  val Divisible        = scalaz.Divisible
  val Enum             = scalaz.Enum
  val Equal            = scalaz.Equal
  val Foldable         = scalaz.Foldable
  val Foldable1        = scalaz.Foldable1
  val Functor          = scalaz.Functor
  val InvariantFunctor = scalaz.InvariantFunctor
  val Monad            = scalaz.Monad
  val MonadError       = scalaz.MonadError
  val MonadListen      = scalaz.MonadListen
  val MonadPlus        = scalaz.MonadPlus
  val MonadReader      = scalaz.MonadReader
  val MonadState       = scalaz.MonadState
  val MonadTell        = scalaz.MonadTell
  val MonadTrans       = scalaz.MonadTrans
  val Monoid           = scalaz.Monoid
  val Nondeterminism   = scalaz.Nondeterminism
  val Optional         = scalaz.Optional
  val Order            = scalaz.Order
  val Plus             = scalaz.Plus
  val Semigroup        = scalaz.Semigroup
  val Show             = scalaz.Show
  val Traverse         = scalaz.Traverse
  val Traverse1        = scalaz.Traverse1
  val Unzip            = scalaz.Unzip
  val Zip              = scalaz.Zip

  // scalaz utilities
  type <~<[A, B]      = scalaz.Liskov.<~<[A, B]
  type ===[A, B]      = scalaz.Leibniz.===[A, B]
  type @@[T, Tag]     = scalaz.@@[T, Tag]
  type ~>[F[_], G[_]] = scalaz.NaturalTransformation[F, G]
  type Name[A]        = scalaz.Name[A]
  type Need[A]        = scalaz.Need[A]
  type Value[A]       = scalaz.Value[A]
  type Memo[K, V]     = scalaz.Memo[K, V]
  val Liskov  = scalaz.Liskov
  val Leibniz = scalaz.Leibniz
  val Name    = scalaz.Name
  val Need    = scalaz.Need
  val Value   = scalaz.Value
  val Memo    = scalaz.Memo

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
  val Maybe           = scalaz.Maybe
  val Disjunction     = scalaz.\/
  val Validation      = scalaz.Validation
  val These           = scalaz.\&/
  val Const           = scalaz.Const
  val IList           = scalaz.IList
  val NonEmptyList    = scalaz.NonEmptyList
  val EStream         = scalaz.EphemeralStream
  val CorecursiveList = scalaz.CorecursiveList
  val ImmutableArray  = scalaz.ImmutableArray
  val Dequeue         = scalaz.Dequeue
  val DList           = scalaz.DList
  val ISet            = scalaz.ISet
  val IMap            = scalaz.==>>
  val StrictTree      = scalaz.StrictTree
  val Tree            = scalaz.Tree
  val FingerTree      = scalaz.FingerTree
  val Cord            = scalaz.Cord
  val Heap            = scalaz.Heap
  val Diev            = scalaz.Diev
  val OneAnd          = scalaz.OneAnd
  val IO              = scalaz.effect.IO
  val Free            = scalaz.Free
  val Trampoline      = scalaz.Trampoline

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
  val MaybeT        = scalaz.MaybeT
  val EitherT       = scalaz.EitherT
  val Kleisli       = scalaz.Kleisli
  val ReaderT       = scalaz.ReaderT
  val WriterT       = scalaz.WriterT
  val IndexedStateT = scalaz.IndexedStateT
  val StateT        = scalaz.StateT
  val TheseT        = scalaz.TheseT
  val StreamT       = scalaz.StreamT
  val ContT         = scalaz.ContT
  val IdT           = scalaz.IdT

  // ADT constructors / deconstructors (types not exposed)
  val Just  = scalaz.Maybe.Just
  val Empty = scalaz.Maybe.Empty
  val -\/   = scalaz.-\/
  val \/-   = scalaz.\/-
  val ICons = scalaz.ICons
  val INil  = scalaz.INil

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
