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
focuses on using [scalaz](https://github.com/scalaz/scalaz), but you can instead use [cats](http://typelevel.org/cats/) or roll your own
framework.

This book is designed to be read from cover to cover, in the order
presented, with a rest between chapters. To ensure that the book is
concise, important concepts are not always repeated. Re-read sections
that are confusing, they will be important later.

A computer is not necessary to follow along.

We also recommend [The Red Book](https://www.manning.com/books/functional-programming-in-scala) as further reading. It teaches how to
write an FP library in Scala from first principles.

A> Scalaz is a principled Functional Programming library that has evolved
A> over a decade of industry use.
A> 
A> Cats is an experiment to rewrite scalaz. Cats is a member of the
A> [Typelevel](http://typelevel.org/about.html) community, organised around functional programming in Scala.
A> Typelevel is a political organisation who enforce a Code of Conduct to
A> ensure that their spaces are welcoming, inclusive and safe.
A> 
A> Although this book uses scalaz, a variant using cats will be created,
A> subject to demand. In the meantime, you can obtain a gratis copy of
A> [Advanced Scala with Cats](http://underscore.io/books/advanced-scala/) by Underscore Consultants. At this point, the
A> cats API is mostly a clone of scalaz, so this book is relevant for
A> either library.


# Copyleft Notice

This book is **Libre** and follows the philosophy of [Free Software](https://www.gnu.org/philosophy/free-sw.en.html): you
can use this book as you like, the [source is available](https://github.com/fommil/fp-scala-mortals), you can
redistribute this book and you can distribute your own version. That
means you can print it, photocopy it, e-mail it, upload it to
websites, change it, translate it, remix it, delete bits, and draw all
over it.

You can even sell this book or your own version (although, morally,
you should offer a royalty share to the author). If you received this
book without paying for it, I would appreciate it if you donated what
you feel it is worth at <https://leanpub.com/fp-scala-mortals>.

This book is **Copyleft**: if you change the book and distribute your
own version, you must also pass these freedoms to its recipients.

This book uses the [Creative Commons Attribution ShareAlike 4.0
International](https://creativecommons.org/licenses/by-sa/4.0/legalcode) (CC BY-SA 4.0) license.

All original code snippets in this book are separately [CC0](https://wiki.creativecommons.org/wiki/CC0) licensed,
you may use them without restriction. Excerpts from `scalaz` and
related libraries maintain their license, reproduced in full in the
appendix.

The example application `drone-dynamic-agents` is distributed under
the terms of the [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html): only the snippets in this book are available
without restriction.


# Thanks

Diego Esteban Alonso Blas, Raúl Raja Martínez and Peter Neyens of 47
degrees, Rúnar Bjarnason and Tony Morris for their help explaining the
principles of FP. Kenji Yoshida and Jason Zaugg for being the main
authors of scalaz, and Miles Sabin for implementing critical FP
features in the scala compiler.

The readers who gave feedback on early drafts of this text.

Juan Manuel Serrano for [All Roads Lead to Lambda](https://skillsmatter.com/skillscasts/9904-london-scala-march-meetup#video), Pere Villega for [On
Free Monads](http://perevillega.com/understanding-free-monads), Dick Wall and Josh Suereth for [For: What is it Good For?](https://www.youtube.com/watch?v=WDaw2yXAa50),
John de Goes for [A Beginner Friendly Tour](http://degoes.net/articles/easy-monads), Erik Bakker for [Options in
Futures, how to unsuck them](https://www.youtube.com/watch?v=hGMndafDcc8), Noel Markham for [ADTs for the Win!](https://www.47deg.com/presentations/2017/06/01/ADT-for-the-win/), Adam
Rosien for the [Scalaz Cheatsheet](http://arosien.github.io/scalaz-cheatsheets/typeclasses.pdf), Yi Lin Wei and Zainab Ali for their
tutorials at Hack The Tower meetups.

The helpul souls who patiently explained the concepts needed to write
the example project [drone-dynamic-agents](https://gitlab.com/fommil/drone-dynamic-agents) Merlin Göttlinger, Edmund
Noble, Fabio Labella, Vincent Marquez, Adelbert Chang, Kai(luo) Wang,
Michael Pilquist, Adam Chlupacek, Pavel Chlupacek, Paul Snively,
Daniel Spiewak, Stephen Compall.


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
  
  libraryDependencies ++= Seq(
    "com.github.mpilquist" %% "simulacrum"  % "0.11.0",
    "com.chuusai"          %% "shapeless"   % "2.3.2" ,
    "com.fommil"           %% "stalactite"  % "0.0.4" ,
    "org.scalaz"           %% "scalaz"      % "7.2.15"
  )
  
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
  addCompilerPlugin(
    "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full
  )
~~~~~~~~

In order to keep our snippets short, we will omit the `import`
section. Unless told otherwise, assume that all snippets have the
following imports:

{lang="text"}
~~~~~~~~
  import scalaz._
  import Scalaz._
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
    at [fommil/drone-dynamic-agents](https://gitlab.com/fommil/drone-dynamic-agents/issues) and pointing out factual errors in
    this text.
3.  if you understood a concept, but feel that it could be explained in
    a different way, let's park that thought for now.


