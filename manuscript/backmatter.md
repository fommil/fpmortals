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
`.apply` and `.unapply` but no type (such as would be created with a `case
class`): there is no subtyping in Haskell. Note that we refer to `List` as an
ADT but in Scala we would call it a GADT, this is because the type parameter is
shared between all type constructors. There are Haskell language extensions
(called `GADTs` and `DataKinds`) that produce something closer to the typical
Scala GADT encoding, but they are an advanced topic.

We can also use infix types in Haskell, a nicer definition might be

{lang="text"}
~~~~~~~~
  data List t = Nil | t :. List t
  
  infixr 5 :.
~~~~~~~~

where we specify a *fixity*, which can be `infix`, `infixl` or `infixr` for non,
left and right associativity, with a number from 0 (loose) to 9 (tight)
precedence. We can now create a list of integers by typing

{lang="text"}
~~~~~~~~
  1 :. 2 :. Nil
~~~~~~~~

However, a linked list is built into the language using square bracket notation

{lang="text"}
~~~~~~~~
  data [] a = [] | a : [a]
  infixr 5 :
~~~~~~~~

and a terse constructor syntax where the following are equivalent:

{lang="text"}
~~~~~~~~
  1 : 2 : 3 : []
  
  [1, 2, 3]
~~~~~~~~

The primitive data types are

-   `Char` a unicode character
-   `Text` for blocks of unicode text
-   `Int` machine dependent, fixed precision signed integer
-   `Word` an unsigned `Int`, and specific `Word8` / `Word16` / `Word32` / `Word64`
-   `Float` / `Double` IEEE single and double precision numbers
-   `Integer` / `Natural` arbitrary precision signed / non-negative integer
-   `(,)` the tuple type
-   along with niche primitives such as arrays

with honorary mentions for

{lang="text"}
~~~~~~~~
  data Bool       = True | False
  data Maybe a    = Nothing | Just a
  data Either a b = Left a | Right b
  data Ordering   = LT | EQ | GT
~~~~~~~~

Haskell also has type aliases, which are entirely for convenience and do not
signal new type definitions, so an alias or the expanded form can be used
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
signature of a function, which is the name of the function followed by its type.
For example if we wanted to define `foldLeft` specifically for a linked list

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
keep it surrounded by brackets.

{lang="text"}
~~~~~~~~
  product :: [Int] -> Int
  
  -- the thing (only one can be defined)
  product = (*) `foldLeft` 1
  product = foldLeft (*) 1
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
  mapMaybe _ Empty    = Empty
~~~~~~~~

Underscores are a placeholder for parameters that are ignored.

We can also define anonymous lambda functions using backslashes, which are
supposed to look like a Greek lambda. The following are equivalent:

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

In the body of a function we can create local value bindings with the `let` or
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

The choice of which to use is often stylistic, although `where` allows for
multiple definitions and type annotations. Note that an apostrophe is a valid
identifier name in a function.

Typical `if` / `else` / `then` logic is keyword based:

{lang="text"}
~~~~~~~~
  filter :: (a -> Bool) -> [a] -> [a]
  filter _ [] = []
  filter f (head : tail) = if f head
                           then head : filter f tail
                           else filter f tail
~~~~~~~~

but it is more common to use *case guards* with the pipe symbol

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
                  Empty         -> []
~~~~~~~~

Guards can be used within matches. For example, say we want to special case
zeros:

{lang="text"}
~~~~~~~~
  unfoldrInt :: (a -> Maybe (Int, a)) -> a -> [Int]
  unfoldrInt f b = case f b of
                     Just (i, a') | i == 0    -> unfoldr f a'
                                  | otherwise -> i : unfoldr f a'
                     Empty                    -> []
~~~~~~~~

A function that is worth noting is `($)`

{lang="text"}
~~~~~~~~
  ($) :: (a -> b) -> a -> b
  infixr 0
~~~~~~~~

This is given the weakest fixity of all infix functions and therefore serves as
an alternative to parenthesis. We could be forgiven for thinking that `$` is
part of the Haskell language, but it is just a stylistic alternative:

{lang="text"}
~~~~~~~~
  -- equivalent
  Just (f a)
  Just $ f a
~~~~~~~~


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


