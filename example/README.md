# `drone-dynamic-agents`

This project is an example application complementing the book [Functional Programming for Mortals with Scalaz](https://leanpub.com/fpmortals).

The pretend goal is to dynamically manage a fleet of agents for the [`drone.io`](https://github.com/drone/drone) CI platform, ensuring that every PR has a dedicated  machine, yet having no machines sitting idle when there is no work to do. The real goal is to demonstrate how to design and lay out a working pure FP application.

That said, this application could probably save millions of dollars per year in operations costs for large corporates if it were made production ready for their choice of CI and compute platform.

Several parts of the application have been left unimplemented and may be completed as a personal exercise. For example:

- add unit tests for untested modules.
- add integration tests for client `POST` requests
- with a stub docker service that does the OAuth2 handshake, e.g. using [`hmonad`](https://github.com/cakesolutions/docker-images-public/tree/master/hmonad).
- harden `OAuth2JsonClient` and write integration tests against a server with expiring tokens.
- implement `DroneModule` and test against the provided docker drone server.
- implement `GoogleMachinesModule` and write a stub docker server to test against.
- add support for other CI platforms, e.g. Jenkins
- design a logging algebra and interpreter, scatter logs throughout the app
- can `BlazeUserInteraction` be rewritten to use `IndexedState` instead of a state machine ADT?
- rewrite `main.scala` to use the `Free` monad as per [Chapter 7.5](https://leanpub.com/fpmortals/read#leanpub-auto-a-free-lunch).
- add support for launching local agents via docker (allowing functional testing of the entire app).
- add AWS support using [`xmlformat`](https://gitlab.com/fommil/scalaz-deriving/tree/master/examples/xmlformat), as opposed to the [AWS SDK](https://aws.amazon.com/developers/getting-started/java/).
- write a compiler plugin and macro to generate the contents of the `boilerplate.scala` files for all algebras

The https://gitter.im/scalaz/scalaz chat room is recommended for anybody seeking volunteer assistance. The issue tracker is closed to encourage live engagement with the community: you will learn a lot by asking questions, although do not assume that anybody knows this codebase.

# Custom `prelude`

This project uses a custom `prelude.scala` instead of the default Scala imports. By default, new files will have nothing imported into scope, not even primitives like `Int`, `Boolean` or `String`.

All of the concepts covered in `#fpmortals` can be imported by using this import at the top of the file

```scala
import fommil.prelude._
```

Scalaz typeclass syntax is opt-in with

```scala
import fommil.prelude.Z._
```

If scala stdlib data types, compatibility or typeclass instances are required, use

```scala
import fommil.prelude.S._
```

If compatibility with Typelevel libraries is required, use

```scala
import fommil.prelude.T._
```

These imports are often written relatively, e.g.

```scala
import prelude._, S._, T._, Z._
```

See `prelude.scala` to understand exactly what is being imported. If you are starting a fresh application, consider creating such a prelude. One of the advantages of Scalaz is that it allows users to construct an *a la carte* set of imports, rather than being forced to import everything at once with

```scala
import scalaz._, Scalaz._
```

which is, however, useful for beginners.

# Linting and Testing

All commands are from the `sbt` shell.

This project enforces the [scalazzi](https://github.com/scalaz/scalazzi) subset of Scala. To run the linter type `lint`. Use `fix` to apply automatic rewrites where available.

To run the unit tests, type `test`.

To run the integration tests you must have [`docker-compose`](https://github.com/docker/compose) installed. Bring up the stub services by typing `it:dockerComposeUp`, run the tests with `it:test`, and bring down the services with `it:dockerComposeDown`. If you forget to do this, the services will continue to run after `sbt` has been closed. You can manually clean up using a combination of `docker ps` and `docker kill`.

Read the instructions under `docker/drone` to use your own github projects, user and `DRONE_TOKEN`.

The OAuth2 handshake with Google works by running `runMain fommil.dda.Main --machines` if you have set up a [Google Cloud Application](https://console.developers.google.com/apis/credentials) and defined the `MACHINES_CLIENT_SECRET` and `MACHINES_CLIENT_ID` environment variables. It will print a `RefreshToken` to the console which should be used as the `MACHINES_REFRESH_TOKEN` when running the main app.

Happy hacking!
