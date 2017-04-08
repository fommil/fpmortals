

# TODO Brief history of abstraction

Inspired by [Roads to Lambda](https://skillsmatter.com/skillscasts/9904-london-scala-march-meetup), motivate the natural progression of
abstraction from Java 1.0 to flat-mapping over a container, but
slightly differently:

-   Problem: abstract over elements of a container
    -   object on a `List`
    -   runtime exception
    -   workaround with `ListString` boilerplate
    -   paradigm: generics

-   Today's abstraction problem: the containers themselves / execution
    -   type constructors (feel free to think of as containers)
    -   procedural code with an interface defining `doThenEtc`
    -   this is just `flatMap`, the container is commonly called a `Monad`


