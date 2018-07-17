
# Typeclass Derivation

Typeclasses provide polymorphic functionality to our applications. But to use a
typeclass we need instances for our business domain objects.

The creation of a typeclass instance from existing instances is known as
*typeclass derivation* and is the topic of this chapter.

There are five approaches to typeclass derivation:

1.  Manual instances for every domain object. This is infeasible for real world
    applications as it results in hundreds of lines of code of boilerplate for
    every line of a `case class`. It is useful only for educational purposes.

2.  Write macros for each typeclass. This is not a maintainable solution, since
    the macro API is known to change with Scala major releases, and in any case
    requires an advanced and experienced developer to write each macro.

3.  Many typeclasses (and algebras!) can be abstracted by an existing scalaz
    typeclass, producing automated tests of the typeclass itself and derivations
    for business domain objects. This is the approach of `scalaz-deriving`.

4.  Jon Pretty's [Magnolia](https://github.com/propensive/magnolia) macro provides a convenient API that lets typeclass
    authors support ADTs. It is effectively an abstraction over hand-rolled
    macros, with all the macro maintenance burden in a single shared library.

5.  Via generic programs with the [Shapeless](https://github.com/milessabin/shapeless/) library. The `implicit` mechanism is
    a language within the Scala language and can be used to write programs at the
    type level, incurring a significant compiler performance penalty.

In this chapter we will study increasingly complex typeclasses and their
derivations. We will begin with `scalaz-deriving` as the most principled
mechanism, then Magnolia (the easiest to use) for encoder and decoder formats,
finishing with Shapeless (the most powerful) for typeclasses with complex
derivation logic.


## TODO `scalaz-deriving`

{width=100%}
![](images/scalaz-deriving-base.png)


## TODO Magnolia


## TODO Shapeless


# TODO Implementing the Application

Pad out the application implementation with everything we've learnt.

Will probably be a big chapter. Maybe best to leave it for a final
part of the book?


# TODO Haskell


# TODO Scala

The following should suffice (in this order)

-   object, val, def, if, primitives, types
-   trait, class, abstract class (construction, methods, inheritance / type hierarchy)
-   String, List and Future as examples (including a brief look at for)
-   case class and pattern matching
-   a brief introduction to implicit

ADTs, for, typeclasses and implicit, and algebras are covered in detail in the main body of my book. As well as many warnings about Future.


