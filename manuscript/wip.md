
# Scalaz Typeclasses

In this chapter we will tour most of the typeclasses in `scalaz-core`.
We don't use everything in `drone-dynamic-agents` so we will give
standalone examples when appropriate.

There has been criticism of the naming in scalaz, and functional
programming in general. Most names follow the conventions introduced
in the Haskell programming language, based on *Category Theory*. Feel
free to set up `type` aliases in your own codebase if you would prefer
to use verbs based on the primary functionality of the typeclass (e.g.
`Mappable`, `Pureable`, `FlatMappable`) until you are comfortable with
the standard names.

Before we introduce the typeclass hierarchy, we will peek at the four
most important methods from a control flow perspective: the methods we
will use the most in typical FP applications:

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
least specific abstraction, we can reuse code for `List`, `Either`,
`Future` and more.

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


## Agenda

There are an overwhelming number of typeclasses, so we will cluster
them by common themes. Notably absent are typeclasses that extend
`Monad`, which get their own chapter.

Scalaz uses code generation instead of simulacrum. We'll present the
typeclasses as if simulacrum was used, but note that there are no
`ops` on the companions. All syntax is provided along with typeclasses
and data types when writing

{lang="text"}
~~~~~~~~
  import scalaz._, Scalaz._
~~~~~~~~

{width=100%}
![](images/scalaz-core-tree.png)

{width=100%}
![](images/scalaz-core-cliques.png)

{width=100%}
![](images/scalaz-core-loners.png)


## Appendable Things

{width=30%}
![](images/scalaz-semigroup.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Semigroup[A] {
    @op("|+|") def append(x: A, y: => A): A
  
    def multiply1(value: F, n: Int): F = ...
  }
  
  @typeclass trait Monoid[A] extends Semigroup[A] {
    def zero: A
  
    def multiply(value: F, n: Int): F =
      if (n <= 0) zero else multiply1(value, n - 1)
  }
  
  @typeclass trait Band[A] extends Semigroup[A]
~~~~~~~~

A> `|+|` is known as the TIE Fighter operator. There is an Advanced TIE
A> Fighter in an upcoming section, which is very exciting.

A `Semigroup` should exist for a type if two elements can be combined
to produce another element of the same type. The operation must be
*associative*, meaning that the order of nested operations should not
matter, i.e.

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
values for a new trade involves selecting and combining templates with
a "last rule wins" merge policy (e.g. if templates have a value for
the same field).

We'll create a simple template schema to demonstrate the principle,
but keep in mind that a realistic system would have a more complicated
ADT.

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
`Monoid[TradeTemplate]`. Although we will generically derive this in a
later chapter, for now we'll create an instance on the companion:

{lang="text"}
~~~~~~~~
  implicit val monoid: Monoid[TradeTemplate] = Monoid.instance(
    (a, b) => TradeTemplate(a.payments |+| b.payments,
                            a.ccy |+| b.ccy,
                            a.otc |+| b.otc),
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

We can see the content's `append` has been called, integer addition.

But our business rules state that we use "last rule wins" on
conflicts, so we introduce a higher priority implicit
`Monoid[Option[T]]` instance and use it instead of the default:

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

Now everything compiles, let's try it out...

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
  
  scala> templates.foldLeft(zero)(_ |+| _)
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


## Objecty Things

In the chapter on Data and Functionality we said that the JVM's notion
of equality breaks down for many things that we can put into an ADT.
The problem is that the JVM was designed for Java, and `equals` is
defined on `java.lang.Object` whether it makes sense or not. There is
no way to erase `equals` and no way to guarantee that it is
implemented.

However, in FP we prefer typeclasses for polymorphic functionality and
even concepts as simple equality are captured at compiletime.

{width=20%}
![](images/scalaz-comparable.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Equal[F]  {
    @op("===") def equal(a1: F, a2: F): Boolean
    @op("/==") def notEqual(a1: F, a2: F): Boolean = !equal(a1, a2)
  }
~~~~~~~~

Indeed `===` (*triple equals*) is more typesafe than `==` (*double
equals*) because it can only be compiled when the types are the same
on both sides of the comparison. You'd be surprised how many bugs this
catches.

`equal` has the same implementation requirements as `Object.equals`

-   *commutative* `f1 === f2` implies `f2 === f1`
-   *reflexive* `f === f`
-   *transitive* `f1 === f2 && f2 === f3` implies `f1 === f3`

By throwing away the universal concept of `Object.equals` we don't
take equality for granted when we construct an ADT, stopping us at
compiletime from expecting equality when there is none.

Continuing the trend of replacing old Java concepts, rather than data
*being a* `java.lang.Comparable`, they now *have an* `Order` according
to:

{lang="text"}
~~~~~~~~
  @typeclass trait Order[F] extends Equal[F] {
    @op("?|?") def order(x: F, y: F): Ordering
  
    override  def equal(x: F, y: F): Boolean = ...
    @op("<" ) def lt(x: F, y: F): Boolean = ...
    @op("<=") def lte(x: F, y: F): Boolean = ...
    @op(">" ) def gt(x: F, y: F): Boolean = ...
    @op(">=") def gte(x: F, y: F): Boolean = ...
  
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
    @op("|-->") def fromStepToL(from: F, step: Int, to: F): List[F] = ...
    @op("|=>" ) def fromToL(from: F, to: F): EphemeralStream[F] = ...
    @op("|==>") def fromStepToL(from: F, step: Int, to: F): EphemeralStream[F] = ...
  }
~~~~~~~~

{lang="text"}
~~~~~~~~
  scala> 10 |--> (2, 20)
  res: List[Int] = List(10, 12, 14, 16, 18, 20)
  
  scala> 'm' |-> 'u'
  res: List[Char] = List(m, n, o, p, q, r, s, t, u)
~~~~~~~~

A> `|==>` is scalaz's Lightsaber. This is the syntax of a Functional
A> Programmer. Not as clumsy or random as `fromStepToL`. An elegant
A> syntax... for a more civilised age.

We'll discuss `EphemeralStream` in the next chapter, for now you just
need to know that it is a potentially infinite data structure that
avoids memory retention problems in the stdlib `Stream`.

Similarly to `Object.equals`, the concept of a `.toString` on every
`class` does not make sense in Java. We would like to enforce
stringyness at compiletime and this is exactly what `Show` achieves:

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


## Mappable Things

We're focusing on things that can be mapped over, or traversed, in
some sense:

{width=100%}
![](images/scalaz-mappable.png)


### Functor

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
       => B` and a value `A`, then you can get an `F[B]`. It has a similar
    signature to `pure` but requires the caller to provide the `F[A =>
       B]`.

`fpair`, `strengthL` and `strengthR` are here because they are simple
examples of reading type signatures, but they are pretty useless in
the wild. For the remaining typeclasses, we'll skip the niche methods.

`Functor` also has some special syntax

{lang="text"}
~~~~~~~~
  implicit class FunctorOps[F[_]: Functor, A](val self: F[A]) {
    def as[B](b: => B): F[B] = Functor[F].map(self)(_ => b)
    def >|[B](b: => B): F[B] = as(b)
  }
~~~~~~~~

`as` and `>|` are a way of replacing the output with a constant.

In our example application, as a nasty hack (which we didn't even
admit to until now), we defined `start` and `stop` to return their
input:

{lang="text"}
~~~~~~~~
  def start(node: MachineNode): F[MachineNode]
  def stop (node: MachineNode): F[MachineNode]
~~~~~~~~

This allowed us to write terse business logic such as

{lang="text"}
~~~~~~~~
  for {
    _      <- m.start(node)
    update = world.copy(pending = Map(node -> world.time))
  } yield update
~~~~~~~~

and

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(m.stop)
    updates = stopped.map(_ -> world.time).toList.toMap
    update  = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~

But this hack pushes unnecessary complexity into the interpreters. It
is better if we let our algebras return `F[Unit]` and use `as`:

{lang="text"}
~~~~~~~~
  m.start(node) as world.copy(pending = Map(node -> world.time))
~~~~~~~~

and

{lang="text"}
~~~~~~~~
  for {
    stopped <- nodes.traverse(a => m.stop(a) as a)
    updates = stopped.map(_ -> world.time).toList.toMap
    update  = world.copy(pending = world.pending ++ updates)
  } yield update
~~~~~~~~

As a bonus, we are now using the less powerful `Functor` instead of
`Monad` when starting a node.


### Foldable

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
summary value of type `B`. There is no enforced operation order,
allowing for parallel computation.

`foldRight` does not require its parameters to have a `Monoid`,
meaning that it needs a starting value `z` and a way to combine each
element of the data structure with the summary value. The order for
traversing the elements is from right to left and therefore it cannot
be parallelised.

A> `foldRight` is conceptually the same as the `foldRight` in the Scala
A> stdlib. However, there is a problem with the stdlib `foldRight`
A> signature, solved in scalaz: very large data structures can stack
A> overflow. `List.foldRight` cheats by implementing `foldRight` as a
A> reversed `foldLeft`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   override def foldRight[B](z: B)(op: (A, B) => B): B =
A>     reverse.foldLeft(z)((right, left) => op(left, right))
A> ~~~~~~~~
A> 
A> but the concept of reversing is not universal and this workaround
A> cannot be used for all data structures. Let's say we want to find
A> out if there is a small number in a `Stream`, with an early exit:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> def isSmall(i: Int): Boolean = i < 10
A>   scala> (1 until 100000).toStream.foldRight(false) {
A>            (el, acc) => isSmall(el) || acc
A>          }
A>   java.lang.StackOverflowError
A>     at scala.collection.Iterator.toStream(Iterator.scala:1403)
A>     ...
A> ~~~~~~~~
A> 
A> Scalaz solves the problem by taking a *byname* parameter for the
A> aggregate value
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> (1 |=> 100000).foldRight(false)(el => acc => isSmall(el) || acc )
A>   res: Boolean = true
A> ~~~~~~~~
A> 
A> which means that the `acc` is not evaluated unless it is needed.
A> 
A> It is worth baring in mind that not all operations are stack safe in
A> `foldRight`. If we were to require evaluation of all elements, we can
A> also get a `StackOverflowError` with scalaz's `EphemeralStream`
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   scala> (1L |=> 100000L).foldRight(0L)(el => acc => el |+| acc )
A>   java.lang.StackOverflowError
A>     at scalaz.Foldable.$anonfun$foldr$1(Foldable.scala:100)
A>     ...
A> ~~~~~~~~

`foldLeft` traverses elements from left to right. `foldLeft` can be
implemented in terms of `foldMap`, but most instances choose to
implement it because it is such a basic operation. Since it is usually
implemented with tail recursion, there are no *byname* parameters.

The only law for `Foldable` is that `foldLeft` and `foldRight` should
each be consistent with `foldMap` for monoidal operations. e.g.
appending an element to a list for `foldLeft` and prepending an
element to a list for `foldRight`. However, `foldLeft` and `foldRight`
do not need to be consistent with each other: in fact they often
produce the reverse of each other.

The simplest thing to do with `Foldable` is to use the `identity`
function, the natural sum of the monoidal elements, giving us `fold`
(and left/right variants to allow choosing specific performance
criteria):

{lang="text"}
~~~~~~~~
  def fold[A: Monoid](t: F[A]): A = ...
  def sumr[A: Monoid](fa: F[A]): A = ...
  def suml[A: Monoid](fa: F[A]): A = ...
~~~~~~~~

Recall that when we learnt about `Monoid`, we wrote this:

{lang="text"}
~~~~~~~~
  scala> templates.foldLeft(Monoid[TradeTemplate].zero)(_ |+| _)
~~~~~~~~

We now know this is silly and we should have written:

{lang="text"}
~~~~~~~~
  scala> templates.toIList.fold
  res: TradeTemplate = TradeTemplate(
                         List(2017-08-05,2017-09-05),
                         Some(USD),
                         Some(false))
~~~~~~~~

`.fold` doesn't work on stdlib `List` because it already has a method
called `fold` that does it's own thing in its own special way.

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

Remember that scalaz is a pure library of only *total functions* so
`index` returns an `Option`, not an exception like `.apply` in the
stdlib. `index` is like `.get`, `indexOr` is like `.getOrElse` and
`element` is like `.contains` (requiring an `Equal`).

These methods *really* sound like a collections API. And, of course,
anything with a `Foldable` can be converted into a `List`

{lang="text"}
~~~~~~~~
  def toList[A](fa: F[A]): List[A] = ...
~~~~~~~~

There are also conversions to other stdlib and scalaz data types such
as `.toSet`, `.toVector`, `.toStream`, `.to[T <: TraversableLike]`,
`.toIList` and so on.

There are useful predicate checks

{lang="text"}
~~~~~~~~
  def filterLength[A](fa: F[A])(f: A => Boolean): Int = ...
  def all[A](fa: F[A])(p: A => Boolean): Boolean = ...
  def any[A](fa: F[A])(p: A => Boolean): Boolean = ...
~~~~~~~~

`filterLength` is a way of counting how many elements are `true` for a
predicate, `all` and `any` return `true` if all (or any) element meets
the predicate, and may exit early.

A> We've seen the `NonEmptyList` in previous chapters. For the sake of
A> brevity we use a type alias `Nel` in place of `NonEmptyList`.
A> 
A> We've also seen `IList` in previous chapters, recall that it's an
A> alternative to stdlib `List` with invariant type parameters and all
A> the impure methods, like `apply`, removed.

We can split an `F[A]` into parts that result in the same `B` with
`splitBy`

{lang="text"}
~~~~~~~~
  def splitBy[A, B: Equal](fa: F[A])(f: A => B): IList[(B, Nel[A])] = ...
  def splitByRelation[A](fa: F[A])(r: (A, A) => Boolean): IList[Nel[A]] = ...
  def splitWith[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  def selectSplit[A](fa: F[A])(p: A => Boolean): List[Nel[A]] = ...
  
  def findLeft[A](fa: F[A])(f: A => Boolean): Option[A] = ...
  def findRight[A](fa: F[A])(f: A => Boolean): Option[A] = ...
~~~~~~~~

for example

{lang="text"}
~~~~~~~~
  scala> IList("foo", "bar", "bar", "faz", "gaz", "baz").splitBy(_.charAt(0))
  res = [(f, [foo]), (b, [bar, bar]), (f, [faz]), (g, [gaz]), (b, [baz])]
~~~~~~~~

noting that there are two parts indexed by `f`.

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
can make use of ordering and therefore use a quicksort-esque algorithm
that is much faster than the stdlib's naive `List.distinct`. Data
structures (such as sets) can implement `distinct` in their `Foldable`
without doing any work.

`distinctBy` allows grouping by the result of applying a function to
the elements. For example, grouping names by their first letter.

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

For example we can ask which `String` is maximum `By` length, or what
is the maximum length `Of` the elements.

{lang="text"}
~~~~~~~~
  scala> List("foo", "fazz").maximumBy(_.length)
  res: Option[String] = Some(fazz)
  
  scala> List("foo", "fazz").maximumOf(_.length)
  res: Option[Int] = Some(4)
~~~~~~~~

This concludes the key features of `Foldable`. You are forgiven for
already forgetting all the methods you've just seen: the key takeaway
is that anything you'd expect to find in a collection library is
probably on `Foldable` and if it isn't already, it [probably should be](https://github.com/scalaz/scalaz/issues/1448).

We'll conclude with some variations of the methods we've already seen.
First there are methods that take a `Semigroup` instead of a `Monoid`:

{lang="text"}
~~~~~~~~
  def fold1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def foldMap1Opt[A, B: Semigroup](fa: F[A])(f: A => B): Option[B] = ...
  def sumr1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  def suml1Opt[A: Semigroup](fa: F[A]): Option[A] = ...
  ...
~~~~~~~~

returning `Option` to account for empty data structures (recall that
`Semigroup` does not have a `zero`). Note that the methods read
"one-Option", not `10 pt`, a subtle typesetting joke for the
(un-)observant.

The typeclass `Foldable1` contains a lot more `Semigroup` variants of
the `Monoid` methods shown here (all suffixed `1`) and makes sense for
data structures which are never empty, without requiring a `Monoid` on
the elements.

Very importantly, there are variants that take monadic return values.
We already used `foldLeftM` when we first wrote the business logic of
our application, now you know that `Foldable` is where it came from:

{lang="text"}
~~~~~~~~
  def foldLeftM[G[_]: Monad, A, B](fa: F[A], z: B)(f: (B, A) => G[B]): G[B] = ...
  def foldRightM[G[_]: Monad, A, B](fa: F[A], z: => B)(f: (A, => B) => G[B]): G[B] = ...
  def foldMapM[G[_]: Monad, A, B: Monoid](fa: F[A])(f: A => G[B]): G[B] = ...
  def findMapM[M[_]: Monad, A, B](fa: F[A])(f: A => M[Option[B]]): M[Option[B]] = ...
  def allM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
  def anyM[G[_]: Monad, A](fa: F[A])(p: A => G[Boolean]): G[Boolean] = ...
  ...
~~~~~~~~

You may also see Curried versions, e.g.

{lang="text"}
~~~~~~~~
  def foldl[A, B](fa: F[A], z: B)(f: B => A => B): B = ...
  def foldr[A, B](fa: F[A], z: => B)(f: A => (=> B) => B): B = ...
  ...
~~~~~~~~


### Traverse

`Traverse` is what happens when you cross a `Functor` with a `Foldable`

{lang="text"}
~~~~~~~~
  trait Traverse[F[_]] extends Functor[F] with Foldable[F] {
    def traverse[G[_]: Applicative, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
    def sequence[G[_]: Applicative, A](fga: F[G[A]]): G[F[A]] = ...
  
    def reverse[A](fa: F[A]): F[A] = ...
  
    def zipL[A, B](fa: F[A], fb: F[B]): F[(A, Option[B])] = ...
    def zipR[A, B](fa: F[A], fb: F[B]): F[(Option[A], B)] = ...
    def indexed[A](fa: F[A]): F[(Int, A)] = ...
    def zipWithL[A, B, C](fa: F[A], fb: F[B])(f: (A, Option[B]) => C): F[C] = ...
    def zipWithR[A, B, C](fa: F[A], fb: F[B])(f: (Option[A], B) => C): F[C] = ...
  
    def mapAccumL[S, A, B](fa: F[A], z: S)(f: (S, A) => (S, B)): (S, F[B]) = ...
    def mapAccumR[S, A, B](fa: F[A], z: S)(f: (S, A) => (S, B)): (S, F[B]) = ...
  }
~~~~~~~~

At the beginning of the chapter we showed the importance of `traverse`
and `sequence` for swapping around HKTs to fit a requirement (e.g.
`List[Future[_]]` to `Future[List[_]]`). You will use these methods
more than you could possibly imagine.

In `Foldable` we weren't able to assume that `reverse` was a universal
concept, but now we can reverse a thing.

We can also `zip` together two things that have a `Traverse`, getting
back `None` when one side runs out of elements, using `zipL` or `zipR`
to decide which side to truncate when the lengths don't match. A
special case of `zip` is to add an index to every entry with
`indexed`.

`zipWithL` and `zipWithR` allow combining the two sides of a `zip`
into a new type, and then returning just an `F[C]`.

`mapAccumL` and `mapAccumR` are regular `map` combined with an
accumulator. If you find your old Java sins are making you want to
reach for a `var`, and refer to it from a `map`, you want `mapAccumL`.

For example, let's say we have a list of words and we want to blank
out words we've already seen. The filtering algorithm is not allowed
to process the list of words a second time so it can be scaled to an
infinite stream:

{lang="text"}
~~~~~~~~
  scala> val freedom =
  """We campaign for these freedoms because everyone deserves them.
     With these freedoms, the users (both individually and collectively)
     control the program and what it does for them."""
     .split("\\s+")
     .toList
  
  scala> def clean(s: String): String = s.toLowerCase.replaceAll("[,.()]+", "")
  
  scala> freedom
         .mapAccumL(Set.empty[String]) { (seen, word) =>
           val cleaned = clean(word)
           (seen + cleaned, if (seen(cleaned)) "_" else word)
         }
         ._2
         .intercalate(" ")
  
  res: String =
  """We campaign for these freedoms because everyone deserves them.
     With _ _ the users (both individually and collectively)
     control _ program _ what it does _ _"""
~~~~~~~~

Finally `Traverse1`, like `Foldable1`, provides variants of these
methods for data structures that cannot be empty, accepting the weaker
`Semigroup` instead of a `Monoid`, and an `Apply` instead of an
`Applicative`.


### Align

`Align` is about merging and padding anything with a `Functor`. Before
looking at `Align`, meet the `\&/` data type (spoken as *These*, or
*hurray!*).

{lang="text"}
~~~~~~~~
  sealed abstract class \&/[+A, +B]
  final case class This[A](aa: A) extends (A \&/ Nothing)
  final case class That[B](bb: B) extends (Nothing \&/ B)
  final case class Both[A, B](aa: A, bb: B) extends (A \&/ B)
~~~~~~~~

i.e. it's a data encoding of `XOR` (eXclusive `OR`).

{lang="text"}
~~~~~~~~
  @typeclass trait Align[F[_]] extends Functor[F] {
    def alignWith[A, B, C](f: A \&/ B => C): (F[A], F[B]) => F[C]
    def align[A, B](a: F[A], b: F[B]): F[A \&/ B] = ...
  
    def merge[A: Semigroup](a1: F[A], a2: F[A]): F[A] = ...
  
    def pad[A, B]: (F[A], F[B]) => F[(Option[A], Option[B])] = ...
    def padWith[A, B, C](f: (Option[A], Option[B]) => C): (F[A], F[B]) => F[C] = ...
~~~~~~~~

Hopefully by this point you are becoming more capable of reading the
type signatures to understand the purpose of the method.

`alignWith` takes a function from either an `A` or a `B` (or both) to
a `C` and returns a lifted function from a tuple of `F[A]` and `F[B]`
to an `F[C]`. `align` constructs a `\&/` out of two `F[_]`.

`merge` allows us to combine two `F[A]` when `A` has a `Semigroup`. A
practical example is the merging of multi-maps and independent tallies

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> List(1)) merge Map("foo" -> List(1), "bar" -> List(2))
  res = Map(foo -> List(1, 1), bar -> List(2))
  
  scala> Map("foo" -> 1) merge Map("foo" -> 1, "bar" -> 2)
  res = Map(foo -> 2, bar -> 2)
~~~~~~~~

`pad` and `padWith` are for creating lifted functions that can merge
two data structures that might run out of values, for example if we
wanted to aggregate some independent votes in a bucket and retain
the knowledge of where the votes came from. e.g. in

{lang="text"}
~~~~~~~~
  scala> Map("foo" -> 1) pad Map("foo" -> 1, "bar" -> 2)
  res = Map(foo -> (Some(1),Some(1)), bar -> (None,Some(2)))
~~~~~~~~

we have access to all the votes for `bar` and we also know that the
first bucket had no votes for `bar`.

There are some variants of `align` that make use of the structure of
`\&/`

{lang="text"}
~~~~~~~~
  ...
    def alignSwap[A, B](a: F[A], b: F[B]): F[B \&/ A] = ...
    def alignA[A, B](a: F[A], b: F[B]): F[Option[A]] = ...
    def alignB[A, B](a: F[A], b: F[B]): F[Option[B]] = ...
    def alignThis[A, B](a: F[A], b: F[B]): F[Option[A]] = ...
    def alignThat[A, B](a: F[A], b: F[B]): F[Option[B]] = ...
    def alignBoth[A, B](a: F[A], b: F[B]): F[Option[(A, B)]] = ...
  }
~~~~~~~~

which should make sense from their type signatures. Examples:

{lang="text"}
~~~~~~~~
  scala> List(1,2,3) alignSwap List(4,5)
  res = List(Both(4,1), Both(5,2), That(3))
  
  scala> List(1,2,3) alignA List(4,5)
  res = List(Some(1), Some(2), Some(3))
  
  scala> List(1,2,3) alignB List(4,5)
  res = List(Some(4), Some(5), None)
  
  scala> List(1,2,3) alignThis List(4,5)
  res = List(None, None, Some(3))
  
  scala> List(1,2,3) alignThat List(4,5)
  res = List(None, None, None)
  
  scala> List(1,2,3) alignBoth List(4,5)
  res = List(Some((1,4)), Some((2,5)), None)
~~~~~~~~

`alignThis` and `alignThat` perhaps require the reminder that they are
exclusive, so return `None` if there is a value in both sides, or no
value on either side.


## Variance

We must return to `Functor` for a moment and discuss an ancestor that
we previously ignored:

{width=100%}
![](images/scalaz-variance.png)

`InvariantFunctor`, also known as the *exponential functor*, has a
method `xmap` which says that given a function from `A` to `B`, and a
function from `B` to `A`, then we can convert `F[A]` to `F[B]`.

`Functor` is a short name for what should be *covariant functor*. But
since `Functor` is so popular it gets the nickname. Likewise
`Contravariant` should really be *contravariant functor*.

`Functor` implements `xmap` with `map` and ignores the function from
`B` to `A`. `Contravariant`, on the other hand, implements `xmap` with
`contramap` and ignores the function from `A` to `B`:

{lang="text"}
~~~~~~~~
  @typeclass trait InvariantFunctor[F[_]] {
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B]
    ...
  }
  
  @typeclass trait Functor[F[_]] extends InvariantFunctor[F] {
    def map[A, B](fa: F[A])(f: A => B): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = map(fa)(f)
    ...
  }
  
  @typeclass trait Contravariant[F[_]] extends InvariantFunctor[F] {
    def contramap[A, B](fa: F[A])(f: B => A): F[B]
    def xmap[A, B](fa: F[A], f: A => B, g: B => A): F[B] = contramap(fa)(f)
    ...
  }
~~~~~~~~

It is important to note that, although related at a theoretical level,
the words *covariant*, *contravariant* and *invariant* do not directly
refer to type variance (i.e. `+` and `-` prefixes that may be written
in type signatures). *Invariance* here means that it is possible to
map the contents of a structure `F[A]` into `F[B]`.

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
      def encodeJson(b: B): Json = self(f(b))
    }
  }
~~~~~~~~

Now consider the case where we want to write an instance of an
`Encoder[B]` in terms of another `Encoder[A]`. That's exactly what
`contramap` is for (recall that it is safe to call `Some.get`, but not
`Option.get`):

{lang="text"}
~~~~~~~~
  implicit def some[A: Encoder]: Encoder[Some[A]] = Encoder[A].contramap(_.get)
~~~~~~~~

On the other hand, a `Decoder` typically has a `Functor`:

{lang="text"}
~~~~~~~~
  @typeclass trait Decoder[A] { self =>
    def decodeJson(j: Json): Decoder.Result[A]
  
    def map[B](f: A => B): Decoder[B] = new Decoder[B] {
      def decodeJson(j: Json): Decoder.Result[B] = self.decodeJson(j).map(f)
    }
  }
  object Decoder {
    type Result[A] = Either[String, A]
  }
~~~~~~~~

Methods on a typeclass can have their type parameters in
*contravariant position* (method parameters) or in *covariant
position* (return type). If a typeclass has a combination of covariant
and contravariant positions, it might have an *invariant functor*.

Consider what happens if we combine `Encoder` and `Decoder` into one
typeclass. We can no longer construct a `Format` by using `map` or
`contramap` alone, we need `xmap`:

{lang="text"}
~~~~~~~~
  @typeclass trait Format[A] extends Encoder[A] with Decoder[A] { self =>
    def xmap[B](f: A => B, g: B => A): Format[B] = new Format[B] {
      def encodeJson(b: B): Json = self(g(b))
      def decodeJson(j: Json): Decoder.Result[B] = self.decodeJson(j).map(f)
    }
  }
~~~~~~~~

A> Although `Encoder` implements `contramap`, `Decoder` implements `map`,
A> and `Format` implements `xmap` we are not saying that these
A> typeclasses extend `InvariantFunctor`, rather they *have an*
A> `InvariantFunctor`.
A> 
A> We could implement instances of
A> 
A> -   `Functor[Decoder]`
A> -   `Contravariant[Encoder]`
A> -   `InvariantFunctor[Format]`
A> 
A> on our companions, and use scalaz syntax to have the exact same `map`,
A> `contramap` and `xmap`.
A> 
A> However, since we don't need anything else that the invariants provide
A> (and it's a lot of boilerplate for a textbook), we just implement the
A> bare minimum on the typeclasses themselves. The invariant instance
A> [could be generated automatically](https://github.com/mpilquist/simulacrum/issues/85).

One of the most compelling uses for `xmap` is to provide typeclasses
for *value types*. A value type is a compiletime wrapper for another
type, that does not incur any object allocation costs (subject to some
rules of use).

For example we can provide context around some numbers to avoid
getting them mixed up:

{lang="text"}
~~~~~~~~
  final case class Alpha(value: Double) extends AnyVal
  final case class Beta (value: Double) extends AnyVal
  final case class Rho  (value: Double) extends AnyVal
  final case class Nu   (value: Double) extends AnyVal
~~~~~~~~

If we want to put these types in a JSON message, we'd need to write a
custom `Format` for each type, which is tedious. But our `Format`
implements `xmap`, allowing `Format` to be constructed from a simple
pattern:

{lang="text"}
~~~~~~~~
  implicit val double: Format[Double] = ...
  
  implicit val alpha: Format[Alpha] = double.xmap(Alpha(_), _.value)
  implicit val beta : Format[Beta]  = double.xmap(Beta(_) , _.value)
  implicit val rho  : Format[Rho]   = double.xmap(Rho(_)  , _.value)
  implicit val nu   : Format[Nu]    = double.xmap(Nu(_)   , _.value)
~~~~~~~~

Macros can automate the construction of these instances, so we don't
need to write them: we'll revisit this later in a dedicated chapter on
Typeclass Derivation.


### Composition

Invariants can be composed via methods with intimidating type
signatures. There are many permutations of `compose` on most
typeclasses, we will not list them all.

{lang="text"}
~~~~~~~~
  @typeclass trait Functor[F[_]] extends Invariant[F] {
    def compose[G[_]: Functor]: Functor[λ[α => F[G[α]]]] = ...
    def icompose[G[_]: Contravariant]: Contravariant[λ[α => F[G[α]]]] = ...
    ...
  }
  @typeclass trait Contravariant[F[_]] extends Invariant[F] {
    def compose[G[_]: Contravariant]: Functor[λ[α => F[G[α]]]] = ...
    def icompose[G[_]: Functor]: Contravariant[λ[α => F[G[α]]]] = ...
    ...
  }
~~~~~~~~

The arrow syntax is a `kind-projector` *type lambda* that says, for
example, if `Functor[F]` is composed with a type `G[_]` (that has a
`Functor[G]`), we get a `Functor[F[G[_]]]` that can operate on
`F[G[A]]`.

An example of `Functor.compose` is where `F[_]` is `List`, `G[_]` is
`Option`, and we want to be able to map over the `Int` inside a
`List[Option[Int]]` without changing the two structures:

{lang="text"}
~~~~~~~~
  scala> val lo = List(Some(1), None, Some(2))
  scala> Functor[List].compose[Option].map(lo)(_ + 1)
  res: List[Option[Int]] = List(Some(2), None, Some(3))
~~~~~~~~

This lets us jump into nested effects and structures and apply a
function at the layer we want.


## Everything But Pure

`Apply` is `Applicative` without the `pure` method, and `Bind` is
`Monad` without `pure`. Consider this the warm-up act, with an
Advanced TIE Fighter for entertainment.

{width=100%}
![](images/scalaz-apply.png)


### Apply

`Apply` extends `Functor` by adding a method named `ap` which is
similar to `map` in that it applies a function to values. However,
with `ap`, the function is in the same context as the values.

{lang="text"}
~~~~~~~~
  @typeclass trait Apply[F[_]] extends Functor[F] {
    @op("<*>") def ap[A, B](fa: => F[A])(f: => F[A => B]): F[B]
  
    def apply2[A,B,C](fa: =>F[A],fb: =>F[B])(f: (A,B) =>C): F[C] = ...
    def apply3[A,B,C,D](fa: =>F[A],fb: =>F[B],fc: =>F[C])(f: (A,B,C) =>D): F[D] = ...
    ...
    def apply12[...]
~~~~~~~~

The `applyX` boilerplate allows us to combine parallel functions and
then map over their combined output. Although it's *possible* to use
`<*>` on data structures, it is far more valuable when operating on
*effects* like the drone and google algebras we created in Chapter 3.

`Apply` has special syntax:

{lang="text"}
~~~~~~~~
  implicit class ApplyOps[F[_]: Apply, A](val self: F[A]) {
    def *>[B](fb: F[B]): F[B] = Apply[F].apply2(self,fb)((_,b) => b)
    def <*[B](fb: F[B]): F[A] = Apply[F].apply2(self,fb)((a,_) => a)
    def |@|[B](fb: F[B]): ApplicativeBuilder[F, A, B] = ...
  }
  
  class ApplicativeBuilder[F[_]: Apply, A, B](a: F[A], b: F[B]) {
    def tupled: F[(A, B)] = Apply[F].apply2(a, b)(f)
    def |@|[C](cc: F[C]): ApplicativeBuilder3[C] = ...
  
    sealed abstract class ApplicativeBuilder3[C](c: F[C]) {
      ..ApplicativeBuilder4
        ...
          ..ApplicativeBuilder12
  }
~~~~~~~~

which is exactly what we used in Chapter 3:

{lang="text"}
~~~~~~~~
  (d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime)
~~~~~~~~

A> The `|@|` operator has many names. Some call it the *Cartesian Product
A> Syntax*, others call it the *Cinnamon Bun*, the *Admiral Ackbar* or
A> the *Macaulay Culkin*. We prefer to call it *The Scream* operator,
A> after the Munch painting, because it is also the sound your CPU makes
A> when it is parallelising All The Things.

The syntax `*>` and `<*` offer a convenient way to ignore the output
from one of two parallel effects.

Unfortunately, although the `|@|` syntax is clear, there is a problem
in that a new `ApplicativeBuilder` object is allocated for each
additional effect. If the work is I/O-bound, the memory allocation
cost is insignificant. However, when performing CPU-bound work, use
the alternative *lifting with arity* syntax, which does not produce
any intermediate objects:

{lang="text"}
~~~~~~~~
  def ^[F[_]: Apply,A,B,C](fa: =>F[A],fb: =>F[B])(f: (A,B) =>C): F[C] = ...
  def ^^[F[_]: Apply,A,B,C,D](fa: =>F[A],fb: =>F[B],fc: =>F[C])(f: (A,B,C) =>D): F[D] = ...
  ...
  def ^^^^^^[F[_]: Apply, ...]
~~~~~~~~

used like

{lang="text"}
~~~~~~~~
  ^^^^(d.getBacklog, d.getAgents, m.getManaged, m.getAlive, m.getTime)
~~~~~~~~

or directly call `applyX`

{lang="text"}
~~~~~~~~
  Apply[F].apply5(d.getBacklog, d.getAgents, m.getManaged, m.getAlive, m.getTime)
~~~~~~~~

Despite being of most value for dealing with effects, `Apply` provides
convenient syntax for dealing with data structures. Consider rewriting

{lang="text"}
~~~~~~~~
  for {
    foo <- data.foo: Option[String]
    bar <- data.bar: Option[Int]
  } yield foo + bar.shows
~~~~~~~~

as

{lang="text"}
~~~~~~~~
  (data.foo |@| data.bar)(_ + _.shows) : Option[String]
~~~~~~~~

If we only want the combined output as a tuple, methods exist to do
just that:

{lang="text"}
~~~~~~~~
  @op("tuple") def tuple2[A,B](fa: =>F[A],fb: =>F[B]): F[(A,B)] = ...
  def tuple3[A,B,C](fa: =>F[A],fb: =>F[B],fc: =>F[C]): F[(A,B,C)] = ...
  ...
  def tuple12[...]
~~~~~~~~

{lang="text"}
~~~~~~~~
  (data.foo tuple data.bar) : Option[(String, Int)]
~~~~~~~~

There are also the generalised versions of `ap` for more than two
parameters:

{lang="text"}
~~~~~~~~
  def ap2[A,B,C](fa: =>F[A],fb: =>F[B])(f: F[(A,B) =>C]): F[C] = ...
  def ap3[A,B,C,D](fa: =>F[A],fb: =>F[B],fc: =>F[C])(f: F[(A,B,C) =>D]): F[D] = ...
  ...
  def ap12[...]
~~~~~~~~

along with `lift` methods that take normal functions and lift them into the
`F[_]` context, the generalisation of `Functor.lift`

{lang="text"}
~~~~~~~~
  def lift2[A,B,C](f: (A,B) =>C): (F[A],F[B]) =>F[C] = ...
  def lift3[A,B,C,D](f: (A,B,C) =>D): (F[A],F[B],F[C])=>F[D] = ...
  ...
  def lift12[...]
~~~~~~~~

and `apF`, a partially applied syntax for `ap`

{lang="text"}
~~~~~~~~
  def apF[A,B](f: => F[A => B]): F[A] => F[B] = ...
~~~~~~~~

Finally `forever`

{lang="text"}
~~~~~~~~
  def forever[A, B](fa: F[A]): F[B] = ...
~~~~~~~~

repeating an effect without stopping. The instance of `Apply` must be
stack safe or we'll get `StackOverflowError`.


### Bind and BindRec

`Bind` introduces `bind`, synonymous with `flatMap`, which allows
functions over the result of an effect to return a new effect, or for
functions over the values of a data structure to return new data
structures that are then joined.

{lang="text"}
~~~~~~~~
  @typeclass trait Bind[F[_]] extends Apply[F] {
  
    @op(">>=") def bind[A, B](fa: F[A])(f: A => F[B]): F[B]
    def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B] = bind(fa)(f)
  
    def join[A](ffa: F[F[A]]): F[A] = bind(ffa)(identity)
  
    def mproduct[A, B](fa: F[A])(f: A => F[B]): F[(A, B)] = ...
    def ifM[B](value: F[Boolean], t: =>F[B], f: =>F[B]): F[B] = ...
  
  }
~~~~~~~~

The `join` may be familiar if you have ever used `flatten` in the
stdlib, it takes nested contexts and squashes them into one.

Although not necessarily implemented as such, we can think of `bind`
as being a `Functor.map` followed by `join`

{lang="text"}
~~~~~~~~
  def bind[A, B](fa: F[A])(f: A => F[B]): F[B] = join(map(fa)(f))
~~~~~~~~

`mproduct` is like `Functor.fproduct` and pairs the function's input
with its output, inside the `F`.

`ifM` is a way to construct a conditional data structure or effect:

{lang="text"}
~~~~~~~~
  scala> List(true, false, true).ifM(List(0), List(1, 1))
  res: List[Int] = List(0, 1, 1, 0)
~~~~~~~~

`ifM` and `ap` are optimised to cache and reuse code branches, compare
to the longer form

{lang="text"}
~~~~~~~~
  scala> List(true, false, true).flatMap { b => if (b) List(0) else List(1, 1) }
~~~~~~~~

which produces a fresh `List(0)` or `List(1, 1)` every time the branch
is invoked.

A> These kinds of optimisations are possible in FP because all methods
A> are deterministic, also known as *referentially transparent*.
A> 
A> If a method returns a different value every time it is called, it is
A> *impure* and breaks the reasoning and optimisations that we can
A> otherwise make.
A> 
A> If the `F` is an effect, perhaps one of our drone or Google algebras,
A> it does not mean that the output of the call to the algebra is cached.
A> Rather the reference to the operation is cached. The performance
A> optimisation of `ifM` is only noticeable for data structures, and more
A> pronounced with the difficulty of the work in each branch.
A> 
A> We will explore the concept of determinism and value caching in more
A> detail in the next chapter.

`Bind` also has some special syntax

{lang="text"}
~~~~~~~~
  implicit class BindOps[F[_]: Bind, A] (val self: F[A]) {
    def >>[B](b: => F[B]): F[B] = Bind[F].bind(self)(_ => b)
    def >>![B](f: A => F[B]): F[A] = Bind[F].bind(self)(a => f(a).map(_ => a))
  }
~~~~~~~~

`>>` is when we wish to discard the input to `bind` and `>>!` is when
we want to run an effect but discard its output.


### BindRec

`BindRec` is a `Bind` that must use constant stack space when doing
recursive `bind`. i.e. it's stack safe and can loop `forever` without
blowing up the stack:

{lang="text"}
~~~~~~~~
  trait BindRec[F[_]] extends Bind[F] {
    def tailrecM[A, B](f: A => F[A \/ B])(a: A): F[B]
  
    override def forever[A, B](fa: F[A]): F[B] = ...
  }
~~~~~~~~

Arguably `forever` should only be introduced by `BindRec`, not `Apply`
or `Bind`.

This is what we need to be able to implement the "loop forever" logic
of our application.

`\/`, called *disjunction*, is a data structure that we will discuss
in the next chapter. It is an improvement of stdlib's `Either`.


## Applicative and Monad

From a functionality point of view, `Applicative` is `Apply` with a
`pure` method, and `Monad` extends `Applicative` with `Bind`.

{width=100%}
![](images/scalaz-applicative.png)

{lang="text"}
~~~~~~~~
  @typeclass trait Applicative[F[_]] extends Apply[F] {
    def point[A](a: => A): F[A]
    def pure[A](a: => A): F[A] = point(a)
  }
  
  @typeclass trait Monad[F[_]] extends Applicative[F] with Bind[F]
~~~~~~~~

In many ways, `Applicative` and `Monad` are the culmination of
everything we've seen in this chapter. `pure` (or `point` as it is
more commonly known for data structures) allows us to create effects
or data structures from values.

Instances of `Applicative` must meet some laws, effectively asserting
that all the methods are consistent:

-   **Identity**: `fa <*> pure(identity) === fa`, (where `fa` is an
    `F[A]`) i.e. applying `pure(identity)` does nothing.
-   **Homomorphism**: `pure(a) <*> pure(ab) === pure(ab(a))` (where `ab`
    is an `A => B`), i.e. applying a `pure` function to a `pure` value
    is the same as applying the function to the value and then using
    `pure` on the result.
-   **Interchange**: `pure(a) <*> ab === ab <*> pure(f => f(a))`, (where
    `fab` is an `F[A => B]`), i.e. `pure` is a left and right identity
-   **Mappy**: `map(fa)(f) === fa <*> pure(f)`

`Monad` adds additional laws:

-   **Left Identity**: `pure(a).bind(f) === f(a)`
-   **Right Identity**: `a.bind(pure(_)) === a`
-   **Associativity**: `fa.bind(f).bind(g) === fa.bind(a =>
      f(a).bind(g))` where `fa` is an `F[A]`, `f` is an `A => F[B]` and
    `g` is a `B => F[C]`.

Associativity says that chained `bind` calls must agree with nested
`bind`. However, it does not mean that we can rearrange the order,
which would be *commutativity*. For example, recalling that `flatMap`
is an alias to `bind`, we cannot rearrange

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.start(node1)
    _ <- machine.stop(node1)
  } yield true
~~~~~~~~

as

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.stop(node1)
    _ <- machine.start(node1)
  } yield true
~~~~~~~~

`start` and `stop` are **non**-*commutative*, because the intended
effect of starting then stopping a node is different to stopping then
starting it!

But `start` is commutative with itself, and `stop` is commutative with
itself, so we can rewrite

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.start(node1)
    _ <- machine.start(node2)
  } yield true
~~~~~~~~

as

{lang="text"}
~~~~~~~~
  for {
    _ <- machine.start(node2)
    _ <- machine.start(node1)
  } yield true
~~~~~~~~

which are equivalent. We're making a lot of assumptions about the
Google Container API here, but this is a reasonable choice to make.

A practical consequence is that a `Monad` must be *commutative* if its
`applyX` methods can be allowed to run in parallel. We cheated in
Chapter 3 when we ran these effects in parallel

{lang="text"}
~~~~~~~~
  (d.getBacklog |@| d.getAgents |@| m.getManaged |@| m.getAlive |@| m.getTime)
~~~~~~~~

because we know that they are commutative among themselves. When it
comes to interpreting our application, later in the book, we will have
to provide evidence that these effects are in fact commutative, or an
asynchronous interpreter may choose to sequence the operations to be
on the safe side.

The subtleties of how we deal with (re)-ordering of effects, and what
those effects are, deserves a dedicated chapter on Advanced Monads.


## Division

{width=100%}
![](images/scalaz-divide.png)


### Divide and Conquer

`Divide` is the `Contravariant` analogue of `Apply`

{lang="text"}
~~~~~~~~
  @typeclass trait Divide[F[_]] extends Contravariant[F] {
    def divide[A, B, C](fa: F[A], fb: F[B])(f: C => (A, B)): F[C]
  
    def divide1[A1, Z](a1: F[A1])(f: Z => A1): F[Z] = ...
    ...
    def divide22[...] = ...
~~~~~~~~

`divide` says that if we can break a `C` into an `A` and a `B`, and
we're given an `F[A]` and an `F[B]`, then we can get an `F[C]`. Hence
*divide and conquer*.

This is a great way to generate typeclass instances by breaking their
type parameter into smaller pieces. Scalaz comes with an instance of
`Divide[Equal]` so let's use `Equal` as an example to construct an
`Equal` for a new product type `Foo`

{lang="text"}
~~~~~~~~
  scala> case class Foo(s: String, i: Int)
  scala> implicit val fooEqual: Divide[Foo] =
           Divide[Equal].divide(Equal[String], Equal[Int]) {
             (foo: Foo) => (foo.s, foo.i)
           }
  scala> Foo("foo", 1) === Foo("bar", 1)
  res: Boolean = false
~~~~~~~~

which is boilerplate that can be generated by tooling.

`Divide` also comes with versions for support for tuples

{lang="text"}
~~~~~~~~
  ...
    def tuple2[A1, A2](a1: F[A1], a2: F[A2]): F[(A1, A2)] = ...
    ...
    def tuple22[...] = ...
  
    def deriving2[A1: F, A2: F, Z](f: Z => (A1, A2)): F[Z] = ...
    ...
    def deriving22[...] = ...
  }
~~~~~~~~

and `deriving`, which is even more convenient to use for our example:

{lang="text"}
~~~~~~~~
  implicit val fooEqual: Equal[Foo] = Divide[Equal].deriving2(f => (f.s, f.i))
~~~~~~~~

`Divisible` is the `Contravariant` analogue of `Applicative` and
introduces `conquer`, the equivalent of `pure`

{lang="text"}
~~~~~~~~
  trait Divisible[F[_]] extends Divide[F] {
    def conquer[A]: F[A]
  }
~~~~~~~~

`conquer` is not used by any of the other methods but offers a
convenient way to obtain an instance of the `F[A]`.

Generally, if encoder typeclasses provide an instance of `Divisible`,
rather than stopping at `Contravariant`, it makes it easy to derive
instances for arbitrary ADTs. Similarly, decoder typeclasses could
provide an `Apply` instance.


# What's Next?

You've reached the end of this Early Access book. Please check the
website regularly for updates.

You can expect to see chapters covering the following topics:

-   Scalaz Typeclasses (completed)
-   Scalaz Data Types
-   Scalaz Advanced Monads
-   Scalaz Utilities
-   Functional Streams
-   Type Refinement
-   Generic Derivation
-   Recursion Schemes
-   Dependent Types
-   Optics
-   Category Theory

while continuing to build out the example application.


