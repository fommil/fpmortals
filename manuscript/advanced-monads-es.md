# Mónadas avanzadas

Usted tiene que conocer cosas como las mónadas avanzadas para ser un
programador funcional avanzado.

Sin embargo, somos desarrolladores  buscando una vida simple, y nuestra
idea de "avanzado" es modesta. Para ponernos en contexto:
`scala.concurrent.Future` es más complicada y con más matices que
cualquier `Monad` en este capítulo.

En este capítulo estudiaremos algunas de las implementaciones más
importantes de `Monad`.

## `Future` siempre está en movimiento

El problema más grande con `Future` es que programa trabajo rápidamente
durante su construcción. Como descubrimos en la introducción, `Future`
mezcla la definición del programa con su interpretación (es decir, con
su ejecución).

`Future` también es malo desde una perspectiva de rendimiento: cada vez
que `.flatMap` es llamado, una cerradura se manda al `Ejecutor`, resultando
en una programación de hilos y en cambios de contexto innecesarios. No es
inusual ver 50% del poder de nuestro CPU lidiando con planeación de hilos.
Tanto es así que la paralelización de trabajo con `Futuro` puede volverlo
más lento.

En combinación, la evaluación estricta y el despacho de ejecutores significa
que es imposible de saber cuándo inició un trabajo, cuando terminó, o las
sub tareas que se despacharon para calcular el resultado final. No debería
sobreprendernos que las "soluciones" para el monitoreo de rendimiento para
frameworks basados en `Future` sean buenas opciones para las ventas.

Además, `Future.flatMap` requiere de un `ExecutionContext` en el ámbito
implícito: los usuarios están forzados a pensar sobre la lógica de negocios
y la semántica de ejecución a la vez.

A> Si `Future` fuera un personaje de Star Wars, sería Anakin Skywalker: el
A> malo de la película, apresuránduose y rompiendo cosas sin pensarlo.

## Efectos y efectos laterales

Si no podemos llamar métodos que realicen efectos laterales en nuestra lógica
de negocios, o en `Future` (or `Id`, or `Either`, o `Const`, etc), entonces,
¿*cuándo podemos escribirlos*? La respuesta es: en una `Monad` que retrasa la
ejecución hasta que es interpretada por el punto de entrada de la aplicación.
En este punto, podemos referirnos a I/O y a la mutación como un *efecto* en
el mundo, capturado por el sistema de tipos, en oposición a tener efectos
laterales ocultos.

La implementación simple de tal `Monad` es `IO`, formalizando la versión que
escribimos en la introducción:

{lang="text"}
~~~~~~~~
  final class IO[A](val interpret: () => A)
  object IO {
    def apply[A](a: =>A): IO[A] = new IO(() => a)
  
    implicit val monad: Monad[IO] = new Monad[IO] {
      def point[A](a: =>A): IO[A] = IO(a)
      def bind[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = IO(f(fa.interpret()).interpret())
    }
  }
~~~~~~~~

El método `.interpret` se llama únicamente una vez, en el punto de entrada de una
aplicación.

{lang="text"}
~~~~~~~~
  def main(args: Array[String]): Unit = program.interpret()
~~~~~~~~

Sin embargo, hay dos grandes problemas con este simple `IO`:

1. Puede ocasionar un sobreflujo de la pila
2. No soporta cómputos paralelos