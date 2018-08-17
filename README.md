# `drone-dynamic-agents`

This project is an example application complementing the book [Functional Programming for Mortals with Scalaz](https://leanpub.com/fpmortals).

The pretend goal is to dynamically manage a fleet of agents for the [`drone.io`](https://github.com/drone/drone) CI platform, ensuring that every PR has a dedicated  machine, yet having no machines sitting idle when there is no work to do. The real goal is to demonstrate how to design and lay out a working pure FP application.

That said, this application could probably save millions of dollars per year in operations costs for large corporates if it were made production ready for their choice of CI and compute platform.

Several parts of the application have been left unimplemented and may be completed as a personal exercise. For example:

- add unit tests for modules that do not have any.
- harden `AuthJsonClient` (see comments in the file)
- implement the `DroneModule` interpreter by transcribing the JSON API (use `MachinesModule` as a worked example)
- add support for other CI platforms, e.g. Jenkins
- add stub (docker) servers and integration tests using [`hmonad`](https://github.com/cakesolutions/docker-images-public/tree/master/hmonad)
- design a logging algebra and interpreter
- rewrite `main.scala` to use the `Free` monad as per [Chapter 7.5](https://leanpub.com/fpmortals/read#leanpub-auto-a-free-lunch).
- add AWS support using [`xmlformat`](https://gitlab.com/fommil/scalaz-deriving/tree/master/examples/xmlformat), as opposed to the [AWS SDK](https://aws.amazon.com/developers/getting-started/java/).

# Linting and Testing

All commands are from the `sbt` shell.

This project enforces the [scalazzi](https://github.com/scalaz/scalazzi) subset of Scala. To run the linter type `lint`. Use `fix` to apply automatic rewrites where available.

To run the unit tests, type `test`.

To run the integration tests you must have [`docker-compose`](https://github.com/docker/compose) installed. Bring up the stub services by typing `it:dockerComposeUp`, run the tests with `it:test`, and bring down the services with `it:dockerComposeDown`. If you forget to do this, the services will continue to run after `sbt` has been closed. You can manually clean up using a combination of `docker ps` and `docker kill`.

Happy hacking!
