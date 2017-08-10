
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

This book is for Scala developers with a Java background who wish to
learn the **Functional Programming** (FP) paradigm. We do not accept
that the merits of FP are obvious. Therefore, this book justifies
every concept with practical examples, in Scala.

There are many ways to do Functional Programming in Scala. This book
focuses on using [cats](http://typelevel.org/cats/) with [freestyle](http://frees.io/docs/), but you can instead use [scalaz](https://github.com/scalaz/scalaz)
or roll your own framework.

This book is designed to be read from cover to cover, in the order
presented, with a rest between chapters. To ensure that the book is
concise, important concepts are not always repeated. Re-read sections
that are confusing, they will be important later.

A computer is not necessary to follow along. If you would like
hands-on exercises, we recommend [scala-exercises.org](https://www.scala-exercises.org/)

We also recommend [The Red Book](https://www.manning.com/books/functional-programming-in-scala) as further reading. It teaches how to
write an FP library in Scala from first principles.

A> Cats is an evolving technical experiment under the Typelevel
A> sociopolitical project, which has moderators who enforce a Code of
A> Conduct to ensure that their spaces are welcoming and inclusive. Cats
A> has origins in the decade-long `scalaz` technical project.
A> 
A> Although this Early Access version of the book that you are reading is
A> making use of the cats library, we will probably switch to `scalaz`
A> before completion due to its maturity.
A> 
A> There will be a second book made available (at a discount to those who
A> buy this book) covering the *other* library, whatever that may be.

# Copyleft Notice

This book is **Libre** and follows the philosophy of [Free Software](https://www.gnu.org/philosophy/free-sw.en.html): you
can use this book as you like, the [source is available](https://github.com/fommil/fp-scala-mortals), you can
redistribute this book and you can distribute your own version. That
means you can print it, photocopy it, e-mail it, upload it to
websites, change it, translate it, remix it, delete bits, and draw all
over it. You can even sell it, although morally you should offer a
royalty share to the author.

This book is **Copyleft**: if you change the book and distribute your
own version, you must also pass these freedoms to its recipients.

This book uses the [Creative Commons Attribution ShareAlike 4.0
International](https://creativecommons.org/licenses/by-sa/4.0/legalcode) (CC BY-SA 4.0) license.

All code samples in this book are separately [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) licensed,
which is Libre but not Copyleft. Note that the example application
`drone-dynamic-agents` is distributed under the terms of the [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html):
only the snippets in this book are available under the permissive
license.

# Thanks

Diego Esteban Alonso Blas, Raúl Raja Martínez and Peter Neyens of 47
degrees for their help with understanding the principles of FP, cats
and freestyle. Yi Lin Wei and Zainab Ali for their tutorials at Hack
The Tower meetups.

Rory Graves, Dale Wijnand, Ani Chakraborty, Simon Souter, Sakib
Hadziavdic, for giving feedback on early drafts of this text.

Juan Manuel Serrano for [All Roads Lead to Lambda](https://skillsmatter.com/skillscasts/9904-london-scala-march-meetup#video), Pere Villega for [On
Free Monads](http://perevillega.com/understanding-free-monads), Dick Wall and Josh Suereth for [For: What is it Good For?](https://www.youtube.com/watch?v=WDaw2yXAa50),
John de Goes for [A Beginner Friendly Tour](http://degoes.net/articles/easy-monads), Erik Bakker for [Options in
Futures, how to unsuck them](https://www.youtube.com/watch?v=hGMndafDcc8), Noel Markham for [ADTs for the Win!](https://www.47deg.com/presentations/2017/06/01/ADT-for-the-win/), Rob
Norris for the [Cats Infographic](https://github.com/tpolecat/cats-infographic), Adam Rosien for the [Scalaz
Cheatsheet](http://arosien.github.io/scalaz-cheatsheets/typeclasses.pdf).

The helpul souls who patiently explained the concepts needed to write
the example project [drone-dynamic-agents](https://github.com/fommil/drone-dynamic-agents/issues?q=is%3Aissue+is%3Aopen+label%3A%22needs+guru%22): Merlin Göttlinger, Edmund
Noble, Fabio Labella, Vincent Marquez, Adelbert Chang, Kai(luo) Wang,
Michael Pilquist, Adam Chlupacek, Pavel Chlupacek, Paul Snively,
Daniel Spiewak.

# Practicalities

If you'd like to set up a project that uses the libraries presented in
this book, you will need to use a recent version of Scala with
FP-specific features enabled (e.g. in `build.sbt`):

{lang="text"}
~~~~~~~~
  scalaVersion in ThisBuild := "2.12.3"
  scalacOptions in ThisBuild ++= Seq(
    "-language:_",
    "-Ypartial-unification",
    "-Xfatal-warnings"
  )
~~~~~~~~

and add the following dependencies to your project's settings:

{lang="text"}
~~~~~~~~
  val circeVersion = "0.8.0"
  libraryDependencies ++= Seq(
    "io.circe"      %% "circe-core"    % circeVersion,
    "io.circe"      %% "circe-generic" % circeVersion,
    "io.circe"      %% "circe-parser"  % circeVersion,
    "io.circe"      %% "circe-fs2"     % circeVersion,
    "org.typelevel" %% "cats"          % "0.9.0",
    "org.typelevel" %% "kittens"       % "1.0.0-M10",
    "com.spinoco"   %% "fs2-http"      % "0.1.7",
    "io.frees"      %% "freestyle"     % "0.3.1"
  )
  
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
  addCompilerPlugin(
    "org.scalameta" % "paradise" % "3.0.0-M10" cross CrossVersion.patch
  )
~~~~~~~~

In order to keep our snippets short, we will omit the `import`
section. Unless told otherwise, assume that all snippets have the
following imports:

{lang="text"}
~~~~~~~~
  import cats._
  import cats.implicits._
  import freestyle._
  import freestyle.implicits._
  import fs2._
~~~~~~~~

# Giving Feedback

Please help raise awareness of this book by retweeting [the
announcement](https://twitter.com/fommil/status/855877100296953862) and buying it when it becomes available for Early Access
purchase.

If you would like to give feedback on this book, thank you! I ask of
you:

1.  if you are an FP beginner and something confused you, please point
    out the exact part of the text that confused you at
    [fommil/fp-scala-mortals](https://github.com/fommil/fp-scala-mortals/issues)
2.  if you are an expert in FP, please help by answering my questions
    at [fommil/drone-dynamic-agents](https://github.com/fommil/drone-dynamic-agents/issues) and pointing out factual errors in
    this text.
3.  if you understood a concept, but feel that it could be explained in
    a different way, let's park that thought for now.


