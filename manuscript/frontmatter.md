{frontmatter}

> "Love is wise; hatred is foolish. In this world, which is getting more
> and more closely interconnected, we have to learn to tolerate each
> other, we have to learn to put up with the fact that some people say
> things that we don't like. We can only live together in that way. But
> if we are to live together, and not die together, we must learn a kind
> of charity and a kind of tolerance, which is absolutely vital to the
> continuation of human life on this planet."
> 
> ― Bertrand Russell


# About This Book

This book is for the typical Scala developer, probably with a Java background,
who is both sceptical and curious about the **Functional Programming** (FP)
paradigm. This book justifies every concept with practical examples, including
writing a web application.

There are many ways to do Functional Programming in Scala. This book uses [scalaz
7.2](https://github.com/scalaz/scalaz), the most popular, stable, principled and comprehensive framework. You could
instead use the Cats political fork of scalaz, or roll your own framework.

This book is designed to be read from cover to cover, in the order presented,
with a rest between chapters. Earlier chapters encourage coding styles that we
will later discredit: similar to how we learn Newton's theory of gravity as
children, and progress to Riemann / Einstein / Maxwell if we become students of
physics.

A computer is not necessary to follow along, although we hope that you will gain
the confidence to independently study the scalaz source code. Some of the more
complex code snippets are available with [the book's source code](https://bitbucket.org/fommil/fpmortals/src/) and those who
want practical exercises are encouraged to (re-)implement scalaz (and the
example application) using the partial descriptions presented in this book.

We also recommend [The Red Book](https://www.manning.com/books/functional-programming-in-scala) as further reading. It teaches how to write an FP
library in Scala from first principles. Try to attend a Fantasyland Institute of
Learning training course if you can.


# Copyleft Notice

This book is **Libre** and follows the philosophy of [Free Software](https://www.gnu.org/philosophy/free-sw.en.html): you can use
this book as you like, the [source is available](https://bitbucket.org/fommil/fpmortals/src/), you can redistribute this book
and you can distribute your own version. That means you can print it, photocopy
it, e-mail it, upload it to websites, change it, translate it, charge for it,
remix it, delete bits, and draw all over it.

This book is **Copyleft**: if you change the book and distribute your own version,
you must also pass these freedoms to its recipients.

This book uses the [Creative Commons Attribution ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/legalcode) (CC
BY-SA 4.0) license.

All original code snippets in this book are separately [CC0](https://wiki.creativecommons.org/wiki/CC0) licensed, you may use
them without restriction. Excerpts from `scalaz` and related libraries maintain
their license, reproduced in full in the appendix.

The example application `drone-dynamic-agents` is distributed under the terms of
the [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html): only the snippets in this book are available without restriction.


# Thanks

Diego Esteban Alonso Blas, Raúl Raja Martínez and Peter Neyens of 47
degrees, Rúnar Bjarnason, Tony Morris, John de Goes and Edward Kmett
for their help explaining the principles of FP. Kenji Yoshida and
Jason Zaugg for being the main authors of scalaz, and Paul Chuisano /
Miles Sabin for fixing a critical bug in the scala compiler ([SI-2712](https://issues.scala-lang.org/browse/SI-2712)).

Thank you to the readers who gave feedback on early drafts of this text.

Some material was particularly helpful for my own understanding of the concepts
that are in this book. Thanks to Juan Manuel Serrano for [All Roads Lead to
Lambda](https://skillsmatter.com/skillscasts/9904-london-scala-march-meetup#video), Pere Villega for [On Free Monads](http://perevillega.com/understanding-free-monads), Dick Wall and Josh Suereth for [For:
What is it Good For?](https://www.youtube.com/watch?v=WDaw2yXAa50), Erik Bakker for [Options in Futures, how to unsuck them](https://www.youtube.com/watch?v=hGMndafDcc8),
Noel Markham for [ADTs for the Win!](https://www.47deg.com/presentations/2017/06/01/ADT-for-the-win/), Sukant Hajra for [Classy Monad Transformers](https://www.youtube.com/watch?v=QtZJATIPB0k),
Luka Jacobowitz for [Optimizing Tagless Final](https://typelevel.org/blog/2017/12/27/optimizing-final-tagless.html), Vincent Marquez for [Index your
State](https://www.youtube.com/watch?v=JPVagd9W4Lo), Gabriel Gonzalez for [The Continuation Monad](http://www.haskellforall.com/2012/12/the-continuation-monad.html), and Yi Lin Wei / Zainab Ali
for their tutorials at Hack The Tower meetups.

The helpul souls who patiently explained things to me: Merlin Göttlinger, Edmund
Noble, Fabio Labella, Adelbert Chang, Michael Pilquist, Paul Snively, Daniel
Spiewak, Stephen Compall, Brian McKenna, Ryan Delucchi, Pedro Rodriguez, Emily
Pillmore, Aaron Vargo, Tomas Mikula, Jean-Baptiste Giraudeau, Itamar Ravid, Ross
A. Baker and Alexander Konovalov.


# Practicalities

If you'd like to set up a project that uses the libraries presented in
this book, you will need to use a recent version of Scala with
FP-specific features enabled (e.g. in `build.sbt`):

{lang="text"}
~~~~~~~~
  scalaVersion in ThisBuild := "2.12.6"
  scalacOptions in ThisBuild ++= Seq(
    "-language:_",
    "-Ypartial-unification",
    "-Xfatal-warnings"
  )
  
  libraryDependencies ++= Seq(
    "com.github.mpilquist" %% "simulacrum"     % "0.12.0",
    "org.scalaz"           %% "scalaz-core"    % "7.2.22"
  )
  
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
~~~~~~~~

In order to keep our snippets short, we will omit the `import`
section. Unless told otherwise, assume that all snippets have the
following imports:

{lang="text"}
~~~~~~~~
  import scalaz._, Scalaz._
  import simulacrum._
~~~~~~~~


