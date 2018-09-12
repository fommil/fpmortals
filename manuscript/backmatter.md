{backmatter}


# Typeclass Cheatsheet

| Typeclass          | Method          | From            | Given                  | To             |
|------------------ |--------------- |--------------- |---------------------- |-------------- |
| `InvariantFunctor` | `xmap`          | `F[A]`          | `A => B, B => A`       | `F[B]`         |
| `Contravariant`    | `contramap`     | `F[A]`          | `B => A`               | `F[B]`         |
| `Functor`          | `map`           | `F[A]`          | `A => B`               | `F[B]`         |
| `Apply`            | `ap` / `<*>`    | `F[A]`          | `F[A => B]`            | `F[B]`         |
|                    | `apply2`        | `F[A], F[B]`    | `(A, B) => C`          | `F[C]`         |
| `Alt`              | `altly2`        | `F[A], F[B]`    | `(A \/ B) => C`        | `F[C]`         |
| `Divide`           | `divide2`       | `F[A], F[B]`    | `C => (A, B)`          | `F[C]`         |
| `Decidable`        | `choose2`       | `F[A], F[B]`    | `C => (A \/ B)`        | `F[C]`         |
| `Bind`             | `bind` / `>>=`  | `F[A]`          | `A => F[B]`            | `F[B]`         |
|                    | `join`          | `F[F[A]]`       |                        | `F[A]`         |
| `Cobind`           | `cobind`        | `F[A]`          | `F[A] => B`            | `F[B]`         |
|                    | `cojoin`        | `F[A]`          |                        | `F[F[A]]`      |
| `Applicative`      | `point`         | `A`             |                        | `F[A]`         |
| `Divisible`        | `conquer`       |                 |                        | `F[A]`         |
| `Comonad`          | `copoint`       | `F[A]`          |                        | `A`            |
| `Semigroup`        | `append`        | `A, A`          |                        | `A`            |
| `Plus`             | `plus` / `<+>`  | `F[A], F[A]`    |                        | `F[A]`         |
| `MonadPlus`        | `withFilter`    | `F[A]`          | `A => Boolean`         | `F[A]`         |
| `Align`            | `align`         | `F[A], F[B]`    |                        | `F[A \&/ B]`   |
|                    | `merge`         | `F[A], F[A]`    |                        | `F[A]`         |
| `Zip`              | `zip`           | `F[A], F[B]`    |                        | `F[(A, B)]`    |
| `Unzip`            | `unzip`         | `F[(A, B)]`     |                        | `(F[A], F[B])` |
| `Cozip`            | `cozip`         | `F[A \/ B]`     |                        | `F[A] \/ F[B]` |
| `Foldable`         | `foldMap`       | `F[A]`          | `A => B`               | `B`            |
|                    | `foldMapM`      | `F[A]`          | `A => G[B]`            | `G[B]`         |
| `Traverse`         | `traverse`      | `F[A]`          | `A => G[B]`            | `G[F[B]]`      |
|                    | `sequence`      | `F[G[A]]`       |                        | `G[F[A]]`      |
| `Equal`            | `equal` / `===` | `A, A`          |                        | `Boolean`      |
| `Show`             | `shows`         | `A`             |                        | `String`       |
| `Bifunctor`        | `bimap`         | `F[A, B]`       | `A => C, B => D`       | `F[C, D]`      |
|                    | `leftMap`       | `F[A, B]`       | `A => C`               | `F[C, B]`      |
|                    | `rightMap`      | `F[A, B]`       | `B => C`               | `F[A, C]`      |
| `Bifoldable`       | `bifoldMap`     | `F[A, B]`       | `A => C, B => C`       | `C`            |
| (with `MonadPlus`) | `separate`      | `F[G[A, B]]`    |                        | `(F[A], F[B])` |
| `Bitraverse`       | `bitraverse`    | `F[A, B]`       | `A => G[C], B => G[D]` | `G[F[C, D]]`   |
|                    | `bisequence`    | `F[G[A], G[B]]` |                        | `G[F[A, B]]`   |


# Haskell

Scalaz documentation often cites libraries or papers written in the Haskell
programming language. In this short chapter, we will learn enough Haskell to be
able to understand the source material, and to attend Haskell talks at
functional programming conferences.


## Data

Haskell has a very clean syntax for ADTs. This is a linked list structure:

{lang="text"}
~~~~~~~~
  data List a = Nil | Cons a (List a)
~~~~~~~~

The `List` is the ADT, `a` is the type parameter, `|` separates the *type
constructors*, which are: `Nil` the empty list and a `Cons` cell. `Cons` takes
two parameters, which are separated by whitespace: no commas and no parameter
brackets.

Roughly translated to Scala:

{lang="text"}
~~~~~~~~
  sealed abstract class List[A]
  object Nil {
    def apply[A]: List[A] = ...
    def unapply[A](as: List[A]): Option[Unit] = ...
  }
  object Cons {
    def apply[A](head: A, tail: List[A]): List[A] = ...
    def unapply[A](as: List[A]): Option[(A, List[A])] = ...
  }
~~~~~~~~

i.e. `data` is like a `sealed abstract class`, and a type constructor is like an
`.apply` and `.unapply` but no type, which would have been created by a `case
class`. There is no subtyping in Haskell.

We can also use infix types in Haskell, a nicer definition might use the symbol
`:.` instead of `Cons`

{lang="text"}
~~~~~~~~
  data List t = Nil | t :. List t
  infixr 5 :.
~~~~~~~~

where we specify a *fixity*, which can be `infix`, `infixl` or `infixr` for no,
left and right associativity, respectively. A number from 0 (loose) to 9 (tight)
specifies precedence. We can now create a list of integers by typing

{lang="text"}
~~~~~~~~
  1 :. 2 :. Nil
~~~~~~~~

Haskell already comes with a linked list, which is so fundamental to functional
programming that it gets language-level square bracket syntax `[a]`

{lang="text"}
~~~~~~~~
  data [] a = [] | a : [a]
  infixr 5 :
~~~~~~~~

and a convenient multi-argument value constructor: `[1, 2, 3]` instead of
requiring `1 : 2 : 3 : []`.

Ultimately our ADTs need to hold primitive values. The most common primitive
data types are:

-   `Char` a unicode character
-   `Text` for blocks of unicode text
-   `Int` machine dependent, fixed precision signed integer
-   `Word` an unsigned `Int`, and fixed size `Word8` / `Word16` / `Word32` / `Word64`
-   `Float` / `Double` IEEE single and double precision numbers
-   `Integer` / `Natural` arbitrary precision signed / non-negative integers
-   `(,)` tuples, from 0 (also known as *unit*) to 62 fields
-   `IO` the inspiration for Scalaz's `IO`, implemented in the runtime.

with honorary mentions for

{lang="text"}
~~~~~~~~
  data Bool       = True | False
  data Maybe a    = Nothing | Just a
  data Either a b = Left a  | Right b
  data Ordering   = LT | EQ | GT
~~~~~~~~

Like Scala, Haskell has type aliases: an alias or its expanded form can be used
interchangeably. For legacy reasons, `String` is defined as a linked list of
`Char`

{lang="text"}
~~~~~~~~
  type String = [Char]
~~~~~~~~

which is very inefficient and we always want to use `Text` instead.

Finally we can define field names on ADTs using *record syntax*, which means we
contain the type parameters in curly brackets and use double colon *type
annotations* to indicate the types

{lang="text"}
~~~~~~~~
  -- raw ADT
  data Resource = Human Int String | Robot Int
  data Company  = Company String [Resource]
  
  -- with record syntax
  data Resource = Human { humanId :: Int, name :: String } |
                  Robot { robotId :: Int }
  data Company  = Company { name :: String, employees :: [Resource] }
~~~~~~~~

which generates the equivalent of a field accessor and a copy method.

{lang="text"}
~~~~~~~~
  -- construct
  bender = Robot 0
  -- field access
  robotId bender -- returns 0
  
  adam = Human 0 "Adam"
  -- copy syntax
  eve = adam { name = "Eve" }
~~~~~~~~

A more efficient alternative to single field `data` definitions is to use a
`newtype`, which has no runtime overhead:

{lang="text"}
~~~~~~~~
  newtype Alpha = Alpha { underlying :: Double }
~~~~~~~~

equivalent to `extends AnyVal` but without the caveats.

A> A limitation of Haskell's record syntax is that a field name cannot be used more
A> than once in the same type. However, we can workaround this by enabling a
A> `LANGUAGE` extension:
A> 
A> {lang="text"}
A> ~~~~~~~~
A>   {-# LANGUAGE DuplicateRecordFields #-}
A>   
A>   data Resource = Human { id :: Int, name :: String } |
A>                   Robot { id :: Int }
A> ~~~~~~~~
A> 
A> There are a lot of language extensions and it is not uncommon to have 20 or more
A> in a small project. Haskell is extremely conservative and new language features
A> are opt in for a long period of time before they can be accepted into the
A> vanilla language.


## Functions

Although not necessary, it is good practice to explicitly write the type
signature of a function: its name followed by its type. For example `foldLeft`
specialised for a linked list

{lang="text"}
~~~~~~~~
  foldLeft :: (b -> a -> b) -> b -> [a] -> b
~~~~~~~~

All functions are *curried* in Haskell, each parameter is separated by a `->`
and the final type is the return type. This is equivalent to the following Scala
signature:

{lang="text"}
~~~~~~~~
  def foldLeft[A, B](f: (B, A) => B)(b: B)(as: List[A]): B
~~~~~~~~

Some observations:

-   there is no keyword to denote that what follows is a function
-   there is no need to declare the types that are introduced
-   there is no need to name the parameters

which makes for terse code.

Infix functions are defined in parenthesis and need a fixity definition much
like infix type constructors

{lang="text"}
~~~~~~~~
  (++) :: [a] -> [a] -> [a]
  infixr 5 ++
~~~~~~~~

Regular functions can be called in infix position by surrounding their name with
backticks, and an infix function can be called like a regular function if we
keep it surrounded by brackets. The following are equivalent:

{lang="text"}
~~~~~~~~
  product = (*) `foldLeft` 1
  product = foldLeft (*) 1
~~~~~~~~

An infix function can be curried on either the left or the right, often giving
different semantics:

{lang="text"}
~~~~~~~~
  invert = (1.0 /)
  half   = (/ 2.0)
~~~~~~~~

Functions are typically written with the most general parameter first, to enable
maximum reuse of the curried forms.

The definition of a function may use pattern matching, with one line per case.
This is where we may name the parameters, using the type constructors to extract
parameters much like a Scala `case` clause:

{lang="text"}
~~~~~~~~
  mapMaybe :: (a -> b) -> Maybe a -> Maybe b
  mapMaybe f (Just a) = Just (f a)
  mapMaybe _ Nothing  = Nothing
~~~~~~~~

Underscores are a placeholder for ignored parameters and extractors can be in
infix position:

{lang="text"}
~~~~~~~~
  (<+>) :: Maybe a -> Maybe a -> Maybe a
  Just a <+> _      = Just a
  Empty  <+> Just a = Just a
  Empty  <+> Empty  = Empty
~~~~~~~~

We can define anonymous lambda functions with a backslash, which looks like the
Greek letter λ. The following are equivalent:

{lang="text"}
~~~~~~~~
  foldLeft (*)
  foldLeft (\a1 -> \a2 -> a1 * a2)
  foldLeft (\a1 a2     -> a1 * a2)
~~~~~~~~

Pattern matched Haskell functions are just syntax sugar for nested lambda
functions. Consider a simple function that creates a tuple when given three
inputs:

{lang="text"}
~~~~~~~~
  tuple a -> b -> c -> (a, b, c)
~~~~~~~~

The implementation

{lang="text"}
~~~~~~~~
  tuple a b c = (a, b, c)
~~~~~~~~

desugars into

{lang="text"}
~~~~~~~~
  tuple = \a ->
            \b ->
              \c -> (a, b, c)
~~~~~~~~

In the body of a function we can create local value bindings with `let` or
`where` clauses. The following are equivalent definitions of `map` for a linked
list

{lang="text"}
~~~~~~~~
  map :: (a -> b) -> [a] -> [b]
  
  -- very explicit
  map f as = foldRight map' [] as
             where map' a bs = f a : bs
  
  -- terser, making use of currying
  map f    = foldRight map' []
             where map' a = (f a :)
  
  -- using let / in
  map f    = let map' a = (f a :)
             in foldRight map' []
~~~~~~~~

Note that an apostrophe is a valid identifier name in a function.

`if` / `then` / `else` are keywords for conditional statements:

{lang="text"}
~~~~~~~~
  filter :: (a -> Bool) -> [a] -> [a]
  filter _ [] = []
  filter f (head : tail) = if f head
                           then head : filter f tail
                           else filter f tail
~~~~~~~~

It is considered better style to use *case guards*

{lang="text"}
~~~~~~~~
  filter f (head : tail) | f head    = head : filter f tail
                         | otherwise = filter f tail
~~~~~~~~

Pattern matching is with `case ... of`

{lang="text"}
~~~~~~~~
  unfoldr :: (a -> Maybe (b, a)) -> a -> [b]
  unfoldr f b = case f b of
                  Just (b', a') -> b' : unfoldr f a'
                  Nothing       -> []
~~~~~~~~

Guards can be used within matches. For example, say we want to special case
zeros:

{lang="text"}
~~~~~~~~
  unfoldrInt :: (a -> Maybe (Int, a)) -> a -> [Int]
  unfoldrInt f b = case f b of
                     Just (i, a') | i == 0    -> unfoldr f a'
                                  | otherwise -> i : unfoldr f a'
                     Nothing                  -> []
~~~~~~~~

Two functions that are worth noting are `($)` and `(.)`

{lang="text"}
~~~~~~~~
  -- application operator
  ($) :: (a -> b) -> a -> b
  infixr 0
  
  -- function composition
  (.) :: (b -> c) -> (a -> b) -> a -> c
  infixr 9
~~~~~~~~

Both of these functions are stylistic alternatives to nested parenthesis.

The following are equivalent:

{lang="text"}
~~~~~~~~
  Just (f a)
  Just $ f a
~~~~~~~~

as are

{lang="text"}
~~~~~~~~
  putStrLn (show (1 + 1))
  putStrLn $ show $ 1 + 1
~~~~~~~~

There is a tendency to prefer function composition with `.` instead of multiple
`$`

{lang="text"}
~~~~~~~~
  (putStrLn . show) $ 1 + 1
~~~~~~~~


## Typeclasses

A `class` is conceptually identical to a Scalaz `@typeclass`, and developers
typically say "typeclass" rather than "class".

To define a typeclass we use the `class` keyword, followed by the name of the
typeclass, its type parameter, then the required members in a `where` clause. If
there are dependencies between typeclasses, i.e. `Applicative` requires a
`Functor`, use `=>` notation

{lang="text"}
~~~~~~~~
  class Functor f where
    (<$>) :: (a -> b) -> f a -> f b
    infixl 4 <$>
  
  class Functor f => Applicative f where
    pure  :: a -> f a
    (<*>) :: f (a -> b) -> f a -> f b
    infixl 4 <*>
  
  class Applicative f => Monad f where
    (=<<) :: (a -> f b) -> f a -> f b
    infixr 1 =<<
~~~~~~~~

We provide an implementation of a typeclass with the `instance` keyword. Like
all functions, it is not necessary to repeat the type signature, but it can be
helpful for clarity

{lang="text"}
~~~~~~~~
  data List a = Nil | a :. List a
  
  -- defined elsewhere
  (++) :: List a -> List a -> List a
  map :: (a -> b) -> List a -> List b
  flatMap :: (a -> List b) -> List a -> List b
  foldLeft :: (b -> a -> b) -> b -> List a -> b
  
  instance Functor List where
    (<$>) :: (a -> b) -> List a -> List b
    f <$> as = map f as
  
  instance Applicative List where
    pure a = a :. Nil
  
    Nil <*> _  = Nil
    fs  <*> as = foldLeft (++) Nil $ (<$> as) <$> fs
  
  instance Monad List where
    f =<< list = flatMap f list
~~~~~~~~

If we want to make use of a typeclass in a function we require it with `=>`. For
example we can define something similar to Scalaz's `Apply.apply2`

{lang="text"}
~~~~~~~~
  apply2 :: Applicative f => (a -> b -> c) -> f a -> f b -> f c
  apply2 f fa fb = f <$> fa <*> fb
~~~~~~~~

Note that because of currying, `applyX` is much easier to implement in Haskell
than in Scala. Alternative implementations in comments to demonstrate the
principle:

{lang="text"}
~~~~~~~~
  apply3 :: Applicative f => (a -> b -> c -> d) -> f a -> f b -> f c -> f d
  apply3 f fa fb fc = f <$> fa <*> fb <*> fc
  -- apply3 f fa fb fc = apply2 f fa fb <*> fc
  
  apply4 :: Applicative f => (a -> b -> c -> d -> e) -> f a -> f b -> f c -> f d -> f e
  apply4 f fa fb fc fd = f <$> fa <*> fb <*> fc <*> fd
  -- apply4 f fa fb fc fd = apply3 f fa fb fc <*> fd
~~~~~~~~

Haskell has typeclass derivation with the `deriving` keyword, the inspiration
for `@scalaz.deriving`. Defining the derivation rules is an advanced topic, but
it is easy to derive a typeclass for an ADT:

{lang="text"}
~~~~~~~~
  data List a = Nil | a :. List a
                deriving (Eq, Ord)
~~~~~~~~

Since we are talking about `Monad`, it is a good time to introduce `do`
notation, which was the inspiration for Scala's `for` comprehensions:

{lang="text"}
~~~~~~~~
  do
    a <- f
    b <- g
    c <- h
    return (a, b, c)
~~~~~~~~

desugars to

{lang="text"}
~~~~~~~~
  f >>= \a ->
    g >>= \b ->
      h >>= \c ->
        return (a, b, c)
~~~~~~~~

where `>>=` is `=<<` with parameters flipped

{lang="text"}
~~~~~~~~
  (>>=) :: Monad f => f a -> (a -> f b) -> f b
  (>>=) = flip (=<<)
  infixl 1 >>=
  
  -- from the stdlib
  flip :: (a -> b -> c) -> b -> a -> c
~~~~~~~~

and `return` is a synonym for `pure`.

Unlike Scala, we do not need to bind unit values with `_ <-`. Non-monadic values
can be bound with the `let` keyword:

{lang="text"}
~~~~~~~~
  nameReturn :: IO String
  nameReturn = do putStr "What is your first name? "
                  first <- getLine
                  putStr "And your last name? "
                  last  <- getLine
                  let full = first ++ " " ++ last
                  putStrLn ("Pleased to meet you, " ++ full ++ "!")
                  return full
~~~~~~~~


## Modules

Haskell source code is arranged into hierarchical modules, with a `module`,
similar to Scala's `package` but with the restriction that all contents of a
module must live in a single file. The top of a file declares the module name

{lang="text"}
~~~~~~~~
  module Silly.Tree where
~~~~~~~~

Directories are used on disk to organise the code, so this file would go into
`Silly/Tree.hs`.

By default all symbols in the file are exported but we can restrict this by
explicitly listing the public entries. For example, we can export this `Tree`
ADT, its type constructors, and a `fringe` function, but not the `sapling`
helper function:

{lang="text"}
~~~~~~~~
  module Silly.Tree (Tree(Leaf, Branch), fringe) where
  
  data Tree a = Leaf a | Branch (Tree a) (Tree a)
  
  fringe :: Tree a -> [a]
  fringe (Leaf x)            = [x]
  fringe (Branch left right) = fringe left ++ fringe right
  
  sapling :: Tree String
  sapling = Leaf ""
~~~~~~~~

Interestingly, we can use explicit exports to export symbols that are not
defined in this module. This allows library authors to package up their entire
API into a single importable module, regardless of how it is implemented.

In a different file we can import all the exported members from `Silly.Tree`

{lang="text"}
~~~~~~~~
  import Silly.Tree
~~~~~~~~

which is roughly equivalent to Scala's `import silly.tree._` syntax. If we want
to restrict the symbols that we import we can provide an explicit list in
parentheses after the import

{lang="text"}
~~~~~~~~
  import Silly.Tree (Tree, fringe)
~~~~~~~~

If we have a name collision on a symbol we can use a `qualified` import, with an
optional list of symbols to import

{lang="text"}
~~~~~~~~
  import qualified Silly.Tree (fringe)
~~~~~~~~

and now to call the `fringe` function we have to type `Tree.fringe` instead of
just `fringe`. We can also change the name of the module when importing it

{lang="text"}
~~~~~~~~
  import qualified Silly.Tree as T
~~~~~~~~

The `fringe` function is now `T.fringe`.

If we must disambiguate between two different third party libraries, that use
exactly the same module names, we can do so with the `PackageImports` language
extension:

{lang="text"}
~~~~~~~~
  {-# LANGUAGE PackageImports #-}
  
  import qualified "fommil-tree" Silly.Tree as F
  import qualified "scalaz-tree" Silly.Tree as Z
~~~~~~~~

Alternatively, rather than select what we want to import, we can choose what to
**not** import

{lang="text"}
~~~~~~~~
  import Silly.Tree hiding (fringe)
~~~~~~~~

By default the `Prelude` module is implicitly imported but if we add an explicit
import from the `Prelude` module, only our version is used. We can use this
technique to hide unsafe legacy functions

{lang="text"}
~~~~~~~~
  import Prelude hiding ((!!), head)
~~~~~~~~

or use a custom prelude and disable the default prelude with a language extension

{lang="text"}
~~~~~~~~
  {-# LANGUAGE NoImplicitPrelude #-}
~~~~~~~~


## Evaluation

Haskell compiles to native code, there is no virtual machine, but there is a
garbage collector. A fundamental aspect of the runtime is that all parameters
are **lazily evaluated** by default, i.e. evaluated when needed and cached, not
strict like Scala.

A huge advantage of weak evaluation is that there are no stack overflows! It is
as if all parameters were wrapped in `scalaz.Need` and `Trampoline`, but with
much less overhead. A disadvantage is that there is an overhead compared to
strict evaluation, which is why Haskell allows us to opt in to strict evaluation
on a per parameter and per-module basis.

Haskell is also nuanced about what strict evaluation means: a term is said to be
in *weak head normal-form* (WHNF) if the outermost code blocks cannot be reduced
further, and *normal form* if the term is fully evaluated. Scala's default
evaluation strategy roughly corresponds to normal form.

For example, these terms are normal form:

{lang="text"}
~~~~~~~~
  42
  (2, "foo")
  \x -> x + 1
~~~~~~~~

whereas these are not in normal form (they can be reduced further):

{lang="text"}
~~~~~~~~
  1 + 2            -- reduces to 3
  (\x -> x + 1) 2  -- reduces to 3
  "foo" ++ "bar"   -- reduces to "foobar"
  (1 + 1, "foo")   -- reduces to (2, "foo")
~~~~~~~~

The following terms are in WHNF because the outer code cannot be reduced further
(even though the inner parts can be):

{lang="text"}
~~~~~~~~
  (1 + 1, "foo")
  \x -> 2 + 2
  'f' : ("oo" ++ "bar")
~~~~~~~~

and the following are not in WHNF

{lang="text"}
~~~~~~~~
  1 + 1              -- reduces to 2
  (\x y -> x + y) 2  -- reduces to (\y -> 2 + y)
  "foo" ++ "bar"     -- reduces to "foobar"
~~~~~~~~

The default evaluation strategy is to perform no reductions when passing a term
as a parameter. Language level support allows us to request WHNF for any term
via `seq` and `($!)`

{lang="text"}
~~~~~~~~
  -- evaluate the first argument to WHNF
  seq :: a -> b -> b
  
  -- evaluates `a` to WHNF, then calls the function with that value
  ($!) :: (a -> b) -> a -> b
  infixr 0
~~~~~~~~

More conveniently, we can use an exclamation mark `!` on the `data` type
annotations

{lang="text"}
~~~~~~~~
  data Employee = Employee { name :: !Text, age :: !Int}
~~~~~~~~

The `StrictData` language extension enables strict parameters for all data in
the module.

Another extension, `BangPatterns`, allows `!` to be used on the arguments of
functions.

The extreme `Strict` language extension makes all functions and data parameters
in the module strict by default.

However, the cost of strictness is that Haskell behaves like any other strict
language and can stack overflow. Opting in to strictness must therefore be done
with great care, and only for performance reasons. If in doubt, be lazy and
stick with the defaults.

A> There is one big gotcha with lazy evaluation: if an I/O action is performed that
A> populates a lazy data structure, the action will be performed when the data is
A> read, which can fail at an unexpected part of the code and outside of the
A> resource handling logic. To avoid this gotcha, only read into strict data
A> structures when performing I/O.
A> 
A> Thankfully this gotcha only affects developers writing low-level I/O code, with
A> third party libraries such as `pipes-safe` and `conduits` providing safe
A> abstractions for the typical Haskeller. Most raw byte and `Text` primitives are
A> strict, with `Lazy` variants.


## Next Steps

Haskell is a faster, safer and simpler language than Scala and has proven itself
in industry. Consider taking the [data61 course on functional programming](https://github.com/data61/fp-course), and
ask questions in the `#qfpl` chat room on `freenode.net`.

If you enjoy using Haskell, then tell your managers! That way, the small
percentage of managers who commission Haskell projects will be able to attract
functional programming talent from the many teams who do not.


# Third Party Licenses

Some of the source code in this book has been copied from free / libre
software projects. The license of those projects require that the
following texts are distributed with the source that is presented in
this book.


## Scala License

{lang="text"}
~~~~~~~~
  Copyright (c) 2002-2017 EPFL
  Copyright (c) 2011-2017 Lightbend, Inc.
  
  All rights reserved.
  
  Redistribution and use in source and binary forms, with or without modification,
  are permitted provided that the following conditions are met:
  
    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of the EPFL nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.
  
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
~~~~~~~~


## Scalaz License

{lang="text"}
~~~~~~~~
  Copyright (c) 2009-2014 Tony Morris, Runar Bjarnason, Tom Adams,
                          Kristian Domagala, Brad Clow, Ricky Clarkson,
                          Paul Chiusano, Trygve Laugstøl, Nick Partridge,
                          Jason Zaugg
  All rights reserved.
  
  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:
  
  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
  3. Neither the name of the copyright holder nor the names of
     its contributors may be used to endorse or promote products derived from
     this software without specific prior written permission.
  
  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
~~~~~~~~


