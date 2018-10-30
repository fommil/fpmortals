{frontmatter}

> "El amor es sabio; el odio es tonto. En este mundo, que está interconectado cada
> vez más, tenemos que aprender a tolerarnos unos a otros, tenemos que aprender a
> soportar el hecho de que algunas personas dirán cosas que no nos agraden. Es la
> única manera en la que podemos vivir juntos. Pero si hemos de vivir juntos, y no
> morir juntos, debemos aprender una clase de caridad y clase de tolerancia, que
> es absolutamente vital para la continuidad de la vida humana en este planeta."
>
> ― Bertrand Russell

# Acerca de este libro

Este libro es para el desarrollador de Scala típico, probablemente con conocimientos
previos de Java, que tiene escepticismo y curiosidad sobre el paradigma de
**Programación Funcional** (PF). Este libro justifica cada concepto con ejemplos
prácticos, incluyendo la escritura de una aplicación web.

Este libro usa [Scalaz 7.2](https://github.com/scalaz/scalaz), la librería para
Programación Funcional en Scala más exhaustiva, popular, estable y basada en
principios.

Este libro está diseñado para leerse de principio a fin, en el orden presentado,
con un descanso entre capítulos. Los primeros capítulos incentivan estilos de programación
que más tarde serán desacreditados: de manera similar a cómo aprendimos la teoría la
gravedad de Newton cuando eramos niños, y progresamos a Riemann/Einstein/Maxwell si
nos convertimos en estudiantes de física.

No es necesaria una computadora mientras se lee el libro, pero se recomienda el estudio
del código fuente de Scalaz. Algunos de los ejemplos de código más complejos se encuentran
con [el código fuente del libro](https://github.com/fommil/fpmortals) y se anima a 
aquellos que deseen ejercicios prácticos a reimplementar Scalaz (y la aplicación de
ejemplo) usando las descripciones parciales presentadas en este libro.

También recomendamos [El libro rojo](https://www.manning.com/books/functional-programming-in-scala)
como lectura adicional. Éste libro enseña cómo escribir una librería de Programación
Funcional en Scala usando principios fundamentales.

# Aviso de Copyleft

Este libro es **Libre** y sigue la filosofía de [Free Software](https://www.gnu.org/philosophy/free-sw.es.html):
usted puede usar este libro como desee, el [código fuente está disponible](https://github.com/fommil/fpmortals/)
y puede redistribuir este libro y puede distribuir su propia versión. Esto significa que
puede imprimirlo, fotocopiarlos, enviarlo por correo electrónico, subirlo a sitios web,
cambiarlo, traducirlo, cobrar por él, mezclarlo, borrar partes de él, y dibujar encima
de él.

Este libro es **Copyleft**: si usted cambia el libro y distribuye su propia version,
también debe pasar estas libertades a quienes lo reciban.

Este libro usa la licencia
[Atribución/Reconocimiento-CompartirIgual 4.0 Internacional](https://creativecommons.org/licenses/by-sa/4.0/legalcode.es)
(CC BY-SA 4.0).

Todos los fragmentos de código en este libro están licenciadas de manera separada de
acuerdo con [CC0](https://wiki.creativecommons.org/wiki/CC0), y usted puede usarlos sin
restricción. Los fragmentos de Scalaz y librerías relacionadas mantienen su propia licencia,
que se reproduce de manera completa en el apéndice.

La aplicación de ejemplo `drone-dynamic-agents` se distribuye bajo los términos de la
licencia [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html): sólo los fragmentos de
código en este libro están disponibles sin restricción alguna.

# Agradecimientos

Diego Esteban Alonso Blas, Raúl Raja Martínez y Peter Neyens de 47 degrees, Rúnar Bjarnason,
Tony Morris, John de Goes y Edward Kmett por su ayuda explicando los principios de la
Programación Funcional. Kenji Yoshida y Jason Zaugg por ser los principales autores de Scalaz,
y Paul Chiusano / Miles Sabin por arreglar un error crítico en el compilador de Scala
([SI-2712](https://issues.scala-lang.org/browse/SI-2712)).

Muchas gracias a los lectores que dieron retroalimentación de los primeros bosquejos de
este texto.

Cierto material fue particularmente valioso para mi propio entendimiento de los conceptos
que están en este libro. Gracias a Juan Manuel Serrano por
[All Roads Lead to Lambda](https://skillsmatter.com/skillscasts/9904-london-scala-march-meetup#video),
Pere Villega por [On Free Monads](http://perevillega.com/understanding-free-monads),
Dick Wall y Josh Suereth por
[For: What is it Good For?](https://www.youtube.com/watch?v=WDaw2yXAa50),
Erik Bakker por
[Options in Futures, how to unsuck them](https://www.youtube.com/watch?v=hGMndafDcc8),
Noel Markham por [ADTs for the Win!](https://www.47deg.com/presentations/2017/06/01/ADT-for-the-win/),
Sukant Hajra por [Classy Monad Transformers](https://www.youtube.com/watch?v=QtZJATIPB0k),
Luka Jacobowitz por [Optimizing Tagless Final](https://lukajcb.github.io/blog/functional/2018/01/03/optimizing-tagless-final.html),
Vincent Marquez por [Index your State](https://www.youtube.com/watch?v=JPVagd9W4Lo),
Gabriel Gonzalez por
[The Continuation Monad](http://www.haskellforall.com/2012/12/the-continuation-monad.html),
y Yi Lin Wei / Zainab Ali por sus tutoriales en los meetups Hack The Tower.

A las almas serviciales que pacientemente me explicaron cosas a mi: Merlin Göttlinger,
Edmund Noble, Fabio Labella, Adelbert Chang, Michael Pilquist, Paul Snively,
Daniel Spiewak, Stephen Compall, Brian McKenna, Ryan Delucchi, Pedro Rodriguez,
Emily Pillmore, Aaron Vargo, Tomas Mikula, Jean-Baptiste Giraudeau, Itamar Ravid,
Ross A. Baker, Alexander Konovalov, Harrison Houghton, Alexandre Archambault,
Christopher Davenport, Jose Cardona, Isaac Elliott.

# Aspectos prácticos

Para configurar un proyecto que use las librerías presentadas en este libro, use una versión
reciente de Scala con características específicas de Programación Funcional habilitadas
(por ejemplo, en `build.sbt`):

{lang="text"}
~~~~~~~~
  scalaVersion in ThisBuild := "2.12.6"
  scalacOptions in ThisBuild ++= Seq(
    "-language:_",
    "-Ypartial-unification",
    "-Xfatal-warnings"
  )
  
  libraryDependencies ++= Seq(
    "com.github.mpilquist" %% "simulacrum"     % "0.13.0",
    "org.scalaz"           %% "scalaz-core"    % "7.2.26"
  )
  
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
~~~~~~~~

Con el objetivo de mantener la brevedad en los fragmentos de código, omitiremos la sección
de `import`. A menos que se diga lo contrario, asumimos que todos los fragmentos tienen
las siguientes sentencias de *import*:

{lang="text"}
~~~~~~~~
  import scalaz._, Scalaz._
  import simulacrum._
~~~~~~~~
