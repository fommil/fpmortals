
# Scalaz Core Typeclasses

In this chapter we will tour the typeclasses in `scalaz-core`. We
don't use everything in `drone-dynamic-agents` so we will give
standalone examples when appropriate.

There has been criticism of the naming in scalaz, and functional
programming in general. Most names follow the conventions introduced
in the Haskell programming language, based on *Category Theory*.

We will use the scalaz names in this book, but feel free to set up
`type` aliases in your own codebase if you would prefer to use verb
names based on the primary functionality of the typeclass (e.g.
`Mappable`, `Pureable`, `FlatMappable`) until you are comfortable with
the standard names.

Before we introduce the complete typeclass hierarchy, we will peek at
the four most important methods from a control flow perspective: the
methods we will use the most in typical FP applications:

| Typeclass     | Method     | From   | Given         | To        |
|------------- |---------- |------ |------------- |--------- |
| `Functor`     | `map`      | `F[A]` | `(A => B)`    | `F[B]`    |
| `Applicative` | `pure`     | `A`    |               | `F[A]`    |
| `Monad`       | `flatMap`  | `F[A]` | `(A => F[B])` | `F[B]`    |
| `Traverse`    | `traverse` | `F[A]` | `(A => G[B])` | `G[F[B]]` |

We know that operations which return a `F[_]` can be run sequentially
in a `for` comprehension by `.flatMap`, defined on its `Monad[F]`. The
context `F[_]` can be thought of as a container for an intentional
*effect* with `A` as the output: `flatMap` allows us to generate new
effects `F[B]` at runtime based on the results of evaluating previous
effects.

Of course, not all Higher Kinded Types `F[_]` are effectful, even if
they have a `Monad[F]`. Often they are data structures. By using the
least specific abstraction, our code can work for `List`, `Either`,
`Future` and more, without us needing to know.

If we only need to transform the output from an `F[_]`, that's just
`map`, introduced by `Functor`. In Chapter 3, we ran effects in
parallel by creating a product and mapping over them. In Functional
Programming, parallelisable computations are considered **less**
powerful than sequential ones.

In between `Monad` and `Functor` is `Applicative`, defining `pure`
that lets us lift a value into an effect, or create a data structure
from a single value.

`traverse` is useful for rearranging Higher Kinded Types (HKTs). If
you find yourself with an `F[G[_]]` but you really need a `G[F[_]]`
then you need `Traverse`. For example, say you have a
`List[Future[Int]]` but you need it to be a `Future[List[Int]]`, just
call `.traverse(identity)`, or its simpler sibling `.sequence`.


## Typeclasses

There is an overwhelming number of typeclasses, so we will visualise
similar clusters and discuss, with simplified definitions.

Scalaz uses code generation instead of simulacrum. We'll present the
typeclasses as if simulacrum was used, but note that there are no
`.ops.`, instead syntax is provided along with the classes and traits
when writing

{lang="text"}
~~~~~~~~
  import scalaz._, Scalaz._
~~~~~~~~

If you wish to explore what is available for a particular typeclass,
look under the `scalaz.syntax` package.


### Appendable Things

{width=30%}
![](images/scalaz-semigroup.png)

Defined roughly as:

{lang="text"}
~~~~~~~~
  @typeclass trait Semigroup[A] {
    @op("|+|") @op("mappend") def append(x: A, y: => A): A
  
    def multiply1(value: F, n: Int): F = ...
  }
  
  @typeclass trait Monoid[A] extends Semigroup[A] {
    def zero: A
  
    def multiply(value: F, n: Int): F =
      if (n <= 0) zero else multiply1(value, n - 1)
  }
  
  @typeclass trait Band[A] extends Semigroup[A]
~~~~~~~~

A `Semigroup` should exist for a set of elements that have an
*associative* operation `|+|`.

A> It is common to use `|+|` instead of `mappend`, known as the TIE
A> Fighter operator. There is an Advanced TIE Fighter in the next
A> section, which is very exciting.

*Associative* means that the order of applications should not matter,
i.e.

{lang="text"}
~~~~~~~~
  (a |+| b) |+| c == a |+| (b |+| c)
  
  (1 |+| 2) |+| 3 == 1 |+| (2 |+| 3)
~~~~~~~~

A `Monoid` is a `Semigroup` with a *zero* element (also called *empty*
or *identity*). Combining `zero` with any other `a` should give `a`.

{lang="text"}
~~~~~~~~
  a |+| zero == a
  
  a |+| 0 == a
~~~~~~~~

This is probably bringing back memories of `Numeric` from Chapter 4,
which tried to do too much and was unusable beyond the most basic of
number types. There are implementations of `Monoid` for all the
primitive numbers, but the concept of *appendable* things is useful
beyond numbers.

{lang="text"}
~~~~~~~~
  scala> "hello" |+| " " |+| "world!"
  res: String = "hello world!"
  
  scala> List(1, 2) |+| List(3, 4)
  res: List[Int] = List(1, 2, 3, 4)
~~~~~~~~

`Band` has the law that the `append` operation of the same two
elements is *idempotent*, i.e. gives the same value. Examples are
anything that can only be one value, such as `Unit`, least upper
bounds, or a `Set`. `Band` provides no further methods yet users can
make use of the guarantees for performance optimisation.

As a realistic example for `Monoid`, consider a trading system that
has a large database of reusable trade templates. Creating the default
values for a new trade involves picking a sequence of off-the-shelf
templates and combining them with a "last rule wins" merge policy in
case of conflict.

We'll create a simple template to demonstrate the principle, but keep
in mind that a realistic system would have hundreds of parameters
within nested `case class`.

{lang="text"}
~~~~~~~~
  sealed abstract class Currency
  case object EUR extends Currency
  case object USD extends Currency
  
  final case class TradeTemplate(
    payments: List[java.time.LocalDate],
    ccy: Option[Currency],
    otc: Option[Boolean]
  )
~~~~~~~~

If we write a method that takes `templates: List[TradeTemplate]`, we
only need to call

{lang="text"}
~~~~~~~~
  val zero = Monoid[TradeTemplate].zero
  templates.foldLeft(zero)(_ |+| _)
~~~~~~~~

and our job is done!

But to get `zero` or call `|+|` we must have an instance of
`Monoid[TradeTemplate]`. Although we can generically derive this
later, for now we'll create an explicit instance on the companion:

{lang="text"}
~~~~~~~~
  implicit val monoid: Monoid[TradeTemplate] = Monoid.instance(
   (a, b) => TradeTemplate(
               a.payments |+| b.payments,
               a.ccy |+| b.ccy,
               a.otc |+| b.otc
             ),
   TradeTemplate(Nil, None, None) 
  )
~~~~~~~~

However, this fails to compile because `Monoid[Option[T]]` defers to
`Monoid[T]` and we have neither a `Monoid[Currency]` (we did not
provide one) nor a `Monoid[Boolean]` (inclusive or exclusive logic
must be explicitly chosen).

To explain what we mean by "defers to", consider
`Monoid[Option[Int]]`:

{lang="text"}
~~~~~~~~
  scala> Option(2) |+| None
  res: Option[Int] = Some(2)
  scala> Option(2) |+| Option(1)
  res: Option[Int] = Some(3)
~~~~~~~~

We can see the content's `append` has been called, which for `Int` is
integer addition.

But our business rules state that we use "last rule wins" on
conflicts, so we introduce a higher priority implicit
`Monoid[Option[T]]` instance and use it during our generic derivation
instead of the default one:

{lang="text"}
~~~~~~~~
  implicit def lastWins[A]: Monoid[Option[A]] = Monoid.instance(
    { 
      case (None, None)   => None
      case (only, None)   => only
      case (None, only)   => only
      case (_   , winner) => winner
    },
    None
  )
~~~~~~~~

Let's try it out...

{lang="text"}
~~~~~~~~
  scala> import java.time.{LocalDate => LD}
  scala> val templates = List(
           TradeTemplate(Nil,                     None,      None),
           TradeTemplate(Nil,                     Some(EUR), None),
           TradeTemplate(List(LD.of(2017, 8, 5)), Some(USD), None),
           TradeTemplate(List(LD.of(2017, 9, 5)), None,      Some(true)),
           TradeTemplate(Nil,                     None,      Some(false))
         )
  
  scala> templates.foldLeft(Monoid[TradeTemplate].zero)(_ |+| _)
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

All we needed to do was implement one piece of business logic and
`Monoid` took care of everything else for us!

Note that the list of `payments` are concatenated. This is because the
default `Monoid[List]` uses concatenation of elements and happens to
be the desired behaviour. If the business requirement was different,
it would be a simple case of providing a custom
`Monoid[List[LocalDate]]`. Recall from Chapter 4 that with compiletime
polymorphism we can have a different implementation of `append`
depending on the `E` in `List[E]`, not just the base runtime class
`List`.


### Objecty Things

In the chapter on Data and Functionality we said that the JVM's notion
of equality breaks down for many things that you can put into an ADT.
The problem is that the JVM was designed for Java, and `equals` is
defined on `java.lang.Object` whether it makes sense or not. There is
no way to erase `equals` and no way to guarantee that it is
implemented.

However, in FP we prefer typeclasses for polymorphic functionality and
even concepts as simple equality are captured at compiletime.

{width=30%}
![](images/scalaz-comparable.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Equal[F]  {
    @op("===") def equal(a1: F, a2: F): Boolean
    @op("/==") def notEqual(a1: F, a2: F): Boolean = !equal(a1, a2)
  }
~~~~~~~~

Indeed `===` (`Equal` *triple equals*) is a more typesafe than `==`
(`Object` *double equals*) because it can only be compiled when the
types are the same on both sides of the comparison. This entirely
bypasses the classic problems with equals and sub-classing as
described in *Effective Java* by Josh Bloch.

`equal` has the same implementation requirements as `Object.equals`

-   *commutative* `f1 === f2` implies `f2 === f1`
-   *reflexive* `f === f`
-   *transitive* `f1 === f2 && f2 === f3` implies `f1 === f3`

By doing away with the universal concept of `Object.equals` we don't
take equality for granted when we construct an ADT, stopping us at
compiletime from expecting equality where there is no such concept.

Continuing the trend of replacing old Java concepts, rather than data
*being a* `java.lang.Comparable`, they now *have an* `Order` according
to:

{lang="text"}
~~~~~~~~
  @typeclass trait Order[F] extends Equal[F] {
    @op("?|?") def order(x: F, y: F): Ordering
  
    override  def equal(x: F, y: F): Boolean = ...
    @op("<" ) def lessThan(x: F, y: F): Boolean = ...
    @op("<=") def lessThanOrEqual(x: F, y: F): Boolean = ...
    @op(">" ) def greaterThan(x: F, y: F): Boolean = ...
    @op(">=") def greaterThanOrEqual(x: F, y: F): Boolean = ...
  
    def max(x: F, y: F): F = ...
    def min(x: F, y: F): F = ...
    def sort(x: F, y: F): (F, F) = ...
  }
  
  sealed abstract class Ordering
  object Ordering {
    case object LT extends Ordering
    case object EQ extends Ordering
    case object GT extends Ordering
  }
~~~~~~~~

Things that have an order may also be discrete, allowing us to walk
successors and predecessors:

{lang="text"}
~~~~~~~~
  @typeclass trait Enum[F] extends Order[F] {
    def succ(a: F): F
    def pred(a: F): F
    def min: Option[F]
    def max: Option[F]
  
    @op("-+-") def succn(n: Int, a: F): F = ...
    @op("---") def predn(n: Int, a: F): F = ...
  
    @op("|->" ) def fromToL(from: F, to: F): List[F] = ...
    @op("|-->") def fromStepToL(from: Int, step: F, to: F): List[F] = ...
    // with variants for other collection types
  }
~~~~~~~~

Similarly to `Object.equals`, the concept of a `toString` on every
`class` does not even make sense in Java. We would like to enforce
stringyness at compiletime. This is exactly what `Show` achieves:

{lang="text"}
~~~~~~~~
  trait Show[F] {
    def show(f: F): Cord = Cord(shows(f))
    def shows(f: F): String = show(f).toString
  }
~~~~~~~~

We'll explore `Cord` in more detail in the chapter on data types, you
need only know that it is an efficient data structure for storing and
manipulating `String`.

Unfortunately, due to Scala's default implicit conversions in
`Predef`, and language level support for `toString` in interpolated
strings, it can be incredibly hard to remember to use `shows` instead
of `toString`.


### Mappable Things

We're focusing on things that can be "mapped over" in some sense,
highlighted in this diagram:

{width=100%}
![](images/scalaz-mappable.png)


#### Functor

{lang="text"}
~~~~~~~~
  @typeclass trait Functor[F[_]] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
  
    def void[A](fa: F[A]): F[Unit] = map(fa)(_ => ())
    def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)] = map(fa)(a => (a, f(a)))
  
    def fpair[A](fa: F[A]): F[(A, A)] = map(fa)(a => (a, a))
    def strengthL[A, B](a: A, f: F[B]): F[(A, B)] = map(f)(b => (a, b))
    def strengthR[A, B](f: F[A], b: B): F[(A, B)] = map(f)(a => (a, b))
  
    def lift[A, B](f: A => B): F[A] => F[B] = map(_)(f)
    def mapply[A, B](a: A)(f: F[A => B]): F[B] = map(f)((ff: A => B) => ff(a))
    def compose[G[_]: Functor]: Functor[λ[α => F[G[α]]]] = ...
  }
~~~~~~~~

The only abstract method is `map`, and it must *compose*, i.e. mapping
with `f` and then again with `g` is the same as mapping once with the
composition of `f` and `g`:

{lang="text"}
~~~~~~~~
  fa.map(f).map(g) == fa.map(f.andThen(g))
~~~~~~~~

The `map` should also perform a no-op if the provided function is
`identity` (i.e. `x => x`)

{lang="text"}
~~~~~~~~
  fa.map(identity) == fa
  
  fa.map(x => x) == fa
~~~~~~~~

`Functor` defines some convenience methods around `map` that can be
optimised by specific instances. The documentation has been
intentionally omitted in the above definitions to encourage you to
guess what a method does before looking at the implementation. Please
spend a moment studying only the type signature of the following
before reading further:

{lang="text"}
~~~~~~~~
  def void[A](fa: F[A]): F[Unit]
  def fproduct[A, B](fa: F[A])(f: A => B): F[(A, B)]
  
  def fpair[A](fa: F[A]): F[(A, A)]
  def strengthL[A, B](a: A, f: F[B]): F[(A, B)]
  def strengthR[A, B](f: F[A], b: B): F[(A, B)]
  
  // harder
  def lift[A, B](f: A => B): F[A] => F[B]
  def mapply[A, B](a: A)(f: F[A => B]): F[B]
~~~~~~~~

1.  `void` takes an instance of the `F[A]` and always returns an
    `F[Unit]`, it forgets all the values whilst preserving the
    structure.
2.  `fproduct` takes the same input as `map` but returns `F[(A, B)]`,
    i.e. it tuples the contents with the result of applying the
    function. This is useful when we wish to retain the input.
3.  `fpair` twins all the elements of `A` into a tuple `F[(A, A)]`
4.  `strengthL` pairs the contents of an `F[B]` with a constant `A` on
    the left.
5.  `strengthR` pairs the contents of an `F[A]` with a constant `B` on
    the right.
6.  `lift` takes a function `A => B` and returns a `F[A] => F[B]`. In
    other words, it takes a function over the contents of an `F[A]` and
    returns a function that operates **on** the `F[A]` directly.
7.  `mapply` is a mind bender. Say you have an `F[_]` of functions `A
       => B` and a value `A`, then you can get an `F[B]`. `mapply` has a
    similar signature to `pure` but requires the caller to provide the
    `F[A => B]`.

`fpair`, `strengthL` and `strengthR` are here because they are simple
examples of reading type signatures, but they are pretty useless in
the wild. For the remaining typeclasses, we'll skip the niche methods.

Finally we have `compose`, which has the most complex type signature
on `Functor`. The arrow syntax is a `kind-projector` *type lambda*
that says if this `Functor[F]` is composed with a type `G[_]` (that
has a `Functor[G]`), we get a `Functor[F[G[_]]]` that can operate on
`F[G[A]]`.

An example of `compose` is where `F[_]` is `List`, `G[_]` is `Option`,
and we want to be able to map over the `Int` inside a
`List[Option[Int]]` without changing the two structures:

{lang="text"}
~~~~~~~~
  scala> val lo = List(Some(1), None, Some(2))
  scala> Functor[List].compose[Option].map(lo)(_ + 1)
  res: List[Option[Int]] = List(Some(2), None, Some(3))
~~~~~~~~

This lets us jump into nested effects and structures and apply a
function at the layer we want.


#### Foldable

Technically, `Foldable` is for data structures that can be walked to
produce a summary value. However, this undersells the fact that it is
a one-typeclass army that can provide most of what you'd expect to see
in a Collections API.

There are so many methods we are going to have to split them out,
beginning with the abstract methods:

{lang="text"}
~~~~~~~~
  @typeclass trait Foldable[F[_]] {
    def foldMap[A, B: Monoid](fa: F[A])(f: A => B): B
    def foldRight[A, B](fa: F[A], z: => B)(f: (A, => B) => B): B
    def foldLeft[A, B](fa: F[A], z: B)(f: (B, A) => B): B = ...
~~~~~~~~

An instance of `Foldable` need only implement `foldMap` and
`foldRight` to get all of the functionality in this typeclass,
although methods are typically optimised for specific data structures.

You might recognise `foldMap` by its marketing buzzword name,
**MapReduce**. Given an `F[A]`, a function from `A` to `B`, a zero `B`
and a way to combine `B` (provided by the `Monoid`), we can produce a
summary value of type `B`. There is no enforced order to operation
evaluation, allowing for parallel computation.

`foldRight` does not require its parameters to have a `Monoid`,
meaning that it needs a starting value `z` and a way to combine each
element of the data structure with the summary value. The order for
traversing the elements in the data structure from right to left and
therefore cannot be parallelised.

\#+BEGIN<sub>ASIDE</sub>

`foldRight` is conceptually the same as the `foldRight` in the Scala
stdlib. However, there is a problem with the `foldRight` API, solved
in scalaz: very large data structures can stack overflow.
`List.foldRight` cheats by implementing `foldRight` as a reversed
`foldLeft`

{lang="text"}
~~~~~~~~
  override def foldRight[B](z: B)(op: (A, B) => B): B =
    reverse.foldLeft(z)((right, left) => op(left, right))
~~~~~~~~

but the concept of reversing is not universal and this workaround
cannot be used for all data structures.

{lang="text"}
~~~~~~~~
  scala> (1 until 100000).toStream.foldRight(0L)(_ + _)
  java.lang.StackOverflowError
    at scala.collection.Iterator.toStream(Iterator.scala:1403)
    ...
~~~~~~~~

Scalaz solves the problem by taking a *byname* parameter for the
aggregate value and moving the computation to the heap with a
technique known as *trampolining*.


##### FIXME example of a trampolined foldRight

We'll explore scalaz's data types and trampolining in the next
chapter. It is worth baring in mind that not all data structures have
a stack safe `foldRight`, [even within scalaz](https://github.com/scalaz/scalaz/issues/1447).

\#+END<sub>ASIDE</sub>

`foldLeft` traverses elements from left to right. `foldLeft` can be
implemented in terms of `foldMap`, so it is not left abstract, but
most instances choose to implement it since it is such a basic
operation within the `Foldable` feature set. Since it is usually
implemented with tail recursion, there are no *byname* parameters.

The only law for `Foldable` is that `foldLeft` and `foldRight` should
each be consistent with `foldMap` for monoidal operations. e.g.
appending an element to a list for `foldLeft` and prepending an
element to a list for `foldRight`. However, `foldLeft` and `foldRight`
do not need to be consistent with each other: in fact they often
produce the reverse of each other.

The simplest thing to do with `Foldable` is to use the `identity`
function, giving us `fold`

{lang="text"}
~~~~~~~~
  def fold[A: Monoid](t: F[A]): A = ...
  
  def sumr[A: Monoid](fa: F[A]): A = ...
  def suml[A: Monoid](fa: F[A]): A = ...
~~~~~~~~

This is the natural sum of monoidal elements. Variants for doing this
from the left or right are provided for when order matters for
performance reasons (but it should always produce the same result).

The strangely named `intercalate` inserts a specific `A` between each
element before performing the `fold`

{lang="text"}
~~~~~~~~
  def intercalate[A: Monoid](fa: F[A], a: A): A = ...
~~~~~~~~

which is a generalised version of the stdlib's `mkString`:

{lang="text"}
~~~~~~~~
  scala> List("foo", "bar").intercalate(",")
  res: String = "foo,bar"
~~~~~~~~

The `foldLeft` provides the means to obtain any element by traversal
index, including a bunch of other related methods:

{lang="text"}
~~~~~~~~
  def index[A](fa: F[A], i: Int): Option[A] = ...
  def indexOr[A](fa: F[A], default: => A, i: Int): A = ...
  def length[A](fa: F[A]): Int = ...
  def count[A](fa: F[A]): Int = length(fa)
  def empty[A](fa: F[A]): Boolean = ...
  def element[A: Equal](fa: F[A], a: A): Boolean = ...
~~~~~~~~

Remember that scalaz is a pure library of only *Total* functions so
`index` returns an `Option`, not an exception like the stdlib. `index`
is like `.get`, `indexOr` is like `.getOrElse`. `element` is like
`contains` and requires the notion of equality.

These methods are *really* beginning to sound like what you'd seen in
a Collection Library, of course anything with a `Foldable` can be
converted into a stdlib `List`

{lang="text"}
~~~~~~~~
  def toList[A](fa: F[A]): List[A] = ...
~~~~~~~~

There are also conversions to other stdlib and scalaz data types such
as `.toSet`, `.toVector`, `.toStream`, `.to[CanBuildFrom]`, `.toIList`
and so on.

There are some useful predicate checks

{lang="text"}
~~~~~~~~
  def filterLength[A](fa: F[A])(f: A => Boolean): Int = ...
  def all[A](fa: F[A])(p: A => Boolean): Boolean = ...
  def any[A](fa: F[A])(p: A => Boolean): Boolean = ...
~~~~~~~~

`filterLength` is a way of counting how many elements are `true` for a
predicate, `all` and `any` return `true` if all (or any) element meets
the predicate, and may exit early.

A> We've seen the `NonEmptyList` in previous chapters despite have fully
A> explored its feature set. For the sake of brevity we use a type alias
A> `Nel` in place of `NonEmptyList`.
A> 
A> We've also seen `IList` in previous chapters, recall that it's an
A> alternative to stdlib `List` with invariant type parameters and all
A> the impure methods, like `apply`, removed.

We can split an `F[A]` into parts that result in the same `B` with
`splitBy`, which is the generalised stdlib `groupBy`

{lang="text"}
~~~~~~~~
  def splitBy[A, B: Equal](fa: F[A])(f: A => B): IList[(B, Nel[A])] = ...
  def splitByRelation[A](fa: F[A])(r: (A, A) => Boolean): IList[Nel[A]] = ...
  def splitWith[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  def selectSplit[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  
  def findLeft[A](fa: F[A])(f: A => Boolean): Option[A] = ...
  def findRight[A](fa: F[A])(f: A => Boolean): Option[A] = ...
~~~~~~~~

`splitByRelation` avoids the need for an `Equal` but we must provide
the comparison operator.

`splitWith` splits the elements into groups that alternatively satisfy
and don't satisfy the predicate. `selectSplit` selects groups of
elements that satisfy the predicate, discarding others. This is one of
those rare occasions when two methods share the same type signature
but have different meanings.

`findLeft` and `findRight` are for extracting the first element (from
the left, or right, respectively) that matches a predicate.

Making further use of `Equal` and `Order`, we have the `distinct`
methods which return groupings.

{lang="text"}
~~~~~~~~
  def distinct[A: Order](fa: F[A]): IList[A] = ...
  def distinctE[A: Equal](fa: F[A]): IList[A] = ...
  def distinctBy[A, B: Equal](fa: F[A])(f: A => B): IList[A] =
~~~~~~~~

`distinct` is implemented more efficiently than `distinctE` because it
can make use of ordering.

`distinctBy` allows grouping by the result of applying a function to
the elements. For example, grouping names by their first letter. If
you find yourself using `splitBy` but throwing away the common data,
you probably meant to use `distinctBy`.

We can make further use of `Order` by extracting the minimum or
maximum element (or both extrema) including variations using the `Of`
or `By` pattern to first map to another type or to use a different
type to do the order comparison.

{lang="text"}
~~~~~~~~
  def maximum[A: Order](fa: F[A]): Option[A] = ...
  def maximumOf[A, B: Order](fa: F[A])(f: A => B): Option[B] = ...
  def maximumBy[A, B: Order](fa: F[A])(f: A => B): Option[A] = ...
  
  def minimum[A: Order](fa: F[A]): Option[A] = ...
  def minimumOf[A, B: Order](fa: F[A])(f: A => B): Option[B] = ...
  def minimumBy[A, B: Order](fa: F[A])(f: A => B): Option[A] = ...
  
  def extrema[A: Order](fa: F[A]): Option[(A, A)] = ...
  def extremaOf[A, B: Order](fa: F[A])(f: A => B): Option[(B, B)] = ...
  def extremaBy[A, B: Order](fa: F[A])(f: A => B): Option[(A, A)] =
~~~~~~~~

for example we can ask which `String` is maximum `By` length, or what
is the maximum length `Of` the elements.

{lang="text"}
~~~~~~~~
  scala> List("foo", "fazz").maximumBy(_.length)
  res: Option[String] = Some(fazz)
  
  scala> List("foo", "fazz").maximumOf(_.length)
  res: Option[Int] = Some(4)
~~~~~~~~

This concludes the key features of `Foldable`. You'd be forgiven for
forgetting everything that is available, so the key takeaway is that
anything you'd expect to find in a collection library is probably on
`Foldable`.

We'll conclude with a few variations on the above with weaker type
constraints. First there are variants of some methods that take a
`Monoid`, instead taking a `Semigroup`, returning `Option` for empty
data structures. Note that the methods are `1` (one) `Opt` (option),
not `10 pt`, a subtle typesetting joke for the observant.

{lang="text"}
~~~~~~~~
  def fold1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def foldMap1Opt[A, B: Semigroup](fa: F[A])(f: A => B): Option[B] = ...
  def sumr1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def suml1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
~~~~~~~~

There are variants allowing for monadic return values. We already used
`nodes.foldLeftM(world)` when we first wrote the business logic of our
application, `Foldable` is where the methods are defined:

{lang="text"}
~~~~~~~~
  def foldMapM[G[_]: Monad, A, B: Monoid](fa: F[A])(f: A => G[B]): G[B] = ...
    def foldRightM[G[_]: Monad, A, B](fa: F[A], z: => B)
                                     (f: (A, => B) => G[B]): G[B] = ...
    def foldLeftM[G[_]: Monad, A, B](fa: F[A], z: B)
                                    (f: (B, A) => G[B]): G[B] = ...
    def findMapM[M[_]: Monad, A, B](fa: F[A])
                                   (f: A => M[Option[B]]): M[Option[B]] = ...
    def allM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
    def anyM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
  
  }
~~~~~~~~

You may also see Curried versions of the above methods, e.g.

{lang="text"}
~~~~~~~~
  def foldl[A, B](fa: F[A], z: B)(f: B => A => B): B = ...
  def foldr[A, B](fa: F[A], z: => B)(f: A => (=> B) => B): B = ...
~~~~~~~~

which are for those who prefer the Curried style.


#### Traverse


### Variance

We must return to `Functor` for a moment and reveal a typeclass parent
and method that we previously ignored:


#### go back to Functor for compose / lift / xmap

{lang="text"}
~~~~~~~~
  @typeclass trait Invariant[F[_]] {
    def imap[A, B](fa: F[A])(f: A => B)(g: B => A): F[B]
  
    def compose[G[_]: Invariant]: Invariant[λ[α => F[G[α]]]] = ...
    def composeFunctor[G[_]: Functor]: Invariant[λ[α => F[G[α]]]] = ...
    def composeContravariant[G[_]: Contravariant]: Invariant[λ[α => F[G[α]]]] = ...
  }
  
  @typeclass trait Functor[F[_]] extends Invariant[F] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
  
    def imap[A, B](fa: F[A])(f: A => B)(fi: B => A): F[B] = map(fa)(f)
  
    def compose[G[_]: Functor]: Functor[λ[α => F[G[α]]]] = ...
    ...
  }
  
  @typeclass trait Contravariant[F[_]] extends Invariant[F] {
    def contramap[A, B](fa: F[A])(f: B => A): F[B]
  
    def imap[A, B](fa: F[A])(f: A => B)(fi: B => A): F[B] = contramap(fa)(fi)
  
    def narrow[A, B <: A](fa: F[A]): F[B] = fa.asInstanceOf[F[B]]
  
    def compose[G[_]: Contravariant]: Functor[λ[α => F[G[α]]]] = ...
  }
~~~~~~~~

`Functor` is a short name for what should be *covariant functor*. But
since `Functor` is so popular it gets the nickname. Likewise
`Contravariant` should really be *contravariant functor* and
`Invariant` should be *invariant functor*.

It is important to note that, although related at a theoretical level,
the words *covariant*, *contravariant* and *invariant* do not directly
refer to type variance (i.e. `+` and `-` prefixes that may be written
in type signatures).

*Invariance* here means that it is possible to map the contents of a
structure `F[A]` into `F[B]` if we can provide a map from `A => B` and
a map from `B => A`. A normal, covariant, functor only needs the `A =>
B` to be able to do this, exemplified in its implementation of `imap`,
but a bizarro contravariant functor only needs a map from `B => A`.

This is so ridiculously abstract and seemingly impossible that it
needs a practical example immediately, before we can continue on good
terms. In Chapter 4 we used circe to derive a JSON encoder for our
data types and we gave a brief description of the `Encoder` typeclass.
This is an expanded version:

{lang="text"}
~~~~~~~~
  @typeclass trait Encoder[A] { self =>
    def encodeJson(a: A): Json
  
    def contramap[B](f: B => A): Encoder[B] = new Encoder[B] {
      final def apply(a: B) = self(f(a))
    }
  }
~~~~~~~~

Now consider the case where we want to write an instance of an
`Encoder[B]` in terms of another `Encoder[A]`, that's exactly what
`contramap` is for (recall that it is safe to call `Some.get`, but not
`Option.get`):

{lang="text"}
~~~~~~~~
  implicit def encodeSome[A: Encoder]: Encoder[Some[A]] =
    Encoder[A].contramap(_.get)
~~~~~~~~

<http://typelevel.org/blog/2016/02/04/variance-and-functors.html>

A `Functor` is documented in cats as a "covariant functor". This seems
immediately contradictory since it inherits from `Invariant`. TODO

the sub typing relationship does make sense, functors answer the question: "what do I need, to go from an F[A] to an F[B]" ?

1.  invariant: you need both an A => B, and a B => A

2 ) covariant: you only need an A => B

1.  contravariant: you only need a B => A

The requirements for 2) are a subset of the requirements for 1), so 2) is a subtype of 1)
The requirements for 3) are a subset of the requirements for 1) so 3) is a subtype of 1)

{lang="text"}
~~~~~~~~
  sealed trait Foo[A]
  case class Bar[A](a: A) extends Foo[A]
  case class Baz[A](f: A => Int) extends Foo[A]
~~~~~~~~

-   Invariant


### Bifunctor


### Applicative Things

These were cut from Foldable, revisit them...

{lang="text"}
~~~~~~~~
  def msuml[G[_]: PlusEmpty, A](fa: F[G[A]]): G[A] = ...
  def collapse[X[_]: ApplicativePlus, A](x: F[A]): X[A] = ...
~~~~~~~~


### Monads

Or should this live in the Effects chapter?


### Comparable Things


### Very Abstract Things

Category, etc


### [single page fp book](https://github.com/vil1/single_page_fp_book)


### cheat sheet


### Other


# Data Types

Adjunction
Alpha
Alter
Ap
Band
Bias
BijectionT
CaseInsensitive
Codensity
Cofree
Cokleisli
ComonadTrans
Composition
Const
ContravariantCoyoneda
Coproduct
Cord
CorecursiveList
Coyoneda
Dequeue
Diev
Digit  // revisit the Foldable method
Distributive
DList
Dual
Either3
Either
EitherT
Endomorphic
Endo
EphemeralStream
FingerTree
Forall
FreeAp
Free
FreeT
Generator
Heap
Id
IdT
IList
ImmutableArray
IndexedContsT
Injective
Inject
ISet
Isomorphism
Kan
Kleisli
LazyEither
LazyEitherT
LazyOption
LazyOptionT
LazyTuple
Leibniz
Lens
Liskov
ListT
Map
Maybe
MaybeT
Memo
MonadListen
MonadTrans
MonoidCoproduct
Name
NaturalTransformation
NonEmptyList
NotNothing
NullArgument
NullResult
OneAnd
OneOr
OptionT
Ordering
PLens
Product
ReaderWriterStateT
Reducer
Representable
State
StateT
StoreT
StreamT
StrictTree
Tag
Tags
These
TheseT
TracedT
TreeLoc
Tree
Unapply
UnwriterT
Validation
WriterT
Yoneda
Zap
Zipper


### NonEmptyList


### NonEmptyVector


### Validated

A> This ADT has methods on it, but in Chapter 4 we said that ADTs
A> shouldn't have methods on them and that the functionality should live
A> on typeclasses! You caught us red handed. There are several reasons
A> for doing it this way.
A> 
A> Sorry, but there are more methods than the `value` and `memoize` on
A> `Eval` shown here: it also has `map` and `flatMap`. The reason they
A> live on the ADT and not in an instance of `Monad` is because it is
A> slightly more efficient for the compiler to find these methods instead
A> of looking for `Monad.ops._`, and it is slightly more efficient at
A> runtime. This is an optimisation step that is absolutely vital in a
A> core library such as cats, but please do not perform these
A> optimisations in user code unless you have profiled and found a
A> performance bottleneck. There is a significant cost to code
A> readability.


### Ior


### Esoteric / Advanced

Maybe leave until after typeclasses

-   Cokleisli
-   Const
-   Coproduct
-   Func
-   Kleisli
-   Nested
-   OneAnd
-   Prod


### Monad Transformers

-   EitherT
-   IdT
-   OptionT
-   StateT
-   WriterT


# Monad Transformers

maybe a separate chapter?

functor and applicative compose, monad doesn't, it's annoying, one or two detailed examples but mostly just listing what is available.


# Laws


# Utilities

e.g. conversion utilities between things


# Extensions


# Free Monad

-   FIXME this is old text, need to rewrite Chapter 3 using explicit scalaz Free Monad boilerplate

What we've been doing in this chapter is using the *free monad*,
`cats.free.Free`, to build up the definition of our program as a data
type and then we interpret it. Freestyle calls it `FS`, which is just
a type alias to `Free`, hiding an irrelevant type parameter.

The reason why we use `Free` instead of just implementing `cats.Monad`
directly (e.g. for `Id` or `Future`) is an unfortunate consequence of
running on the JVM. Every nested call to `map` or `flatMap` adds to
the stack, eventually resulting in a `StackOverflowError`.

`Free` is a `sealed abstract class` that roughly looks like:

{lang="text"}
~~~~~~~~
  sealed abstract class Free[S[_], A] {
    def pure(a: A): Free[S, A] = Pure(a)
    def map[B](f: A => B): Free[S, B] = flatMap(a => Pure(f(a)))
    def flatMap[B](f: A => Free[S, B]): Free[S, B] = FlatMapped(this, f)
  }
  
  final case class Pure[S[_], A](a: A) extends Free[S, A]
  final case class Suspend[S[_], A](a: S[A]) extends Free[S, A]
  final case class FlatMapped[S[_], B, C](
                                    c: Free[S, C],
                                    f: C => Free[S, B]) extends Free[S, B]
~~~~~~~~

Its definition of `pure` / `map` / `flatMap` do not do any work, they
just build up data types that live on the heap. Work is delayed until
Free is *interpreted*. This technique of using heap objects to
eliminate stack growth is known as *trampolining*.

When we use the `@free` annotation, a `sealed abstract class` data
type is generated for each of our algebras, with a `final case class`
per method, allowing trampolining. When we write a `Handler`,
Freestyle is converting pattern matches over heap objects into method
calls.


## Free as in Monad

`Free[S[_], A]` can be *generated freely* for any choice of `S`, hence
the name. However, from a practical point of view, there needs to be a
`Monad[S]` in order to interpret it --- so it's more like an interest
only mortgage where you still have to buy the house at the end.


# Advanced Monads

i.e. Effects

And also the issue of parallelisation of applicatives vs the sequential nature of Monad

<https://www.irccloud.com/pastebin/dx1r05od/>

{lang="text"}
~~~~~~~~
  trait ApMonad[F[_], G[_]] {
    def to[A](fa: F[A]): G[A]
    def from[A](ga: G[A]): F[A]
    implicit val fmonad: Monad[F]
    implicit val gap: Applicative[G]
  }
~~~~~~~~


# FS2

Task, Stream

The basics, and covering the Effect, which can be our free monad.

Why streams are so awesome. I'd like a simple example here of reading
from a huge data source, doing parallel work and then writing out in
order to a (slower) device to demonstrate backpressure and constant
memory overhead. Maybe compare this vs hand rolled and akka streams
for a perf test?

Rewrite our business logic to be streaming, convert our GET api into a
`Stream` by polling.


# Implementing the Application

Pad out the application implementation with everything we've learnt.

May need union types, see <https://github.com/propensive/totalitarian>

Will probably be a big chapter. Maybe best to leave it for a final
part of the book?


## Spotting patterns, refactoring

Note that some of our algebras are actually common things and can be
rewritten: reader / writer / state / error / indexed monad. It's ok
that this is a step you can do later.


### perf numbers


# Dependent Types

Jons talks are usually good for this <https://www.youtube.com/watch?v=a1whaMzrtsY>


# Type Refinement

instead of needing those `error` calls in the first place, just don't
allow them to happen at your layer if you can get away with it.

Protect yourself from mistyping


# Generic Programming

-   a mini Shapeless for Mortals
-   typeclass derivation (UrlEncoding, QueryEncoding)
-   scalacheck-shapeless
-   cachedImplicit into a val
-   downside is compile time speeds for ADTs of 50+
-   alternative is <https://github.com/propensive/magnolia>
-   export-hook
-   some advanced cases, e.g. spray-json-shapeless stuff, typeclass
    hierarchy / ambiguities
-   <https://issues.scala-lang.org/browse/SI-2509>
-   gotchas with nested `object` and knownSubclasses
-   semi-auto


# Recursion Schemes


# Optics

not sure what the relevance to this project would be yet.


# Category Theory

Just some of the high level concepts, where to get started if you're interested.
Not needed to write FP but it is needed if you want to read any academic papers.


## Reality Check

In this chapter we've experienced some of the practical benefits of FP
when designing and testing applications:

1.  clean separation of components
2.  isolated, fast and reproducible tests of business logic: extreme mocking
3.  easy parallelisation

However, even if we look past the learning curve of FP, there are
still some real challenges that remain:

1.  trampolining has a performance impact due to increased memory churn
    and garbage collection pressure.
2.  there is not always IDE support for the advanced language features,
    macros or compiler plugins.
3.  implementation details --- as we have already seen with `for`
    syntax sugar, `@module`, and `Free` --- can introduce mental
    overhead and become a blocker when they don't work.
4.  the distinction between pure / side-effecting code, or stack-safe /
    stack-unsafe, is not enforced by the scala compiler. This requires
    developer discipline.
5.  the developer community is still small. Getting help from the
    community can often be a slow process.

As with any new technology, there are rough edges that will be fixed
with time. Most of the problems are because there is a lack of
commercially-funded tooling in FP scala. If you see the benefit of FP,
you can help out by getting involved.

Although FP Scala cannot be as fast as streamlined Java using
mutation, the performance impact is unlikely to affect you if you're
already considering targetting the JVM. Measure the impact before
making a decision if it is important to you.

In the following chapters we are going to learn some of the vast
library of functionality provided by the ecosystem, how it is
organised and how you can find what you need (e.g. how did we know to
use `foldM` or `traverse` when we implemented `act`?). This will allow
us to complete the implementation of our application by building
additional layers of `@module`, use better alternatives to `Future`,
and remove redundancy that we've accidentally introduced.


