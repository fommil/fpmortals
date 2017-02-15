Dynamically manage your [drone.io](https://github.com/drone/drone) cluster of agents.

It is costly to have powerful dedicated build machines. If usage is only a percentage of uptime, dynamically sizing the agent network for the work queue can have a cost saving.

For many libre software projects, requiring a few hours of compute time every day, this can make it possible to use high-powered build machines for a fraction of the cost of a dedicated machine (e.g. $20/month instead of $200/month).


# Drone Overview

Drone server is a libre continuous integration application, which farms out jobs to docker *agents* when it detects events such as pull requests or new commits.

Drone agents spawn additional (project-defined) docker containers which run the jobs they receive, and report their status back to the main drone server.

An agent can be started on any docker-enabled device with a command like:

```
docker run -d \
  -e DRONE_SERVER=wss://ci.fommil.com/ws/broker \
  -e DRONE_SECRET=<redacted> \
  -e DOCKER_MAX_PROCS=1 \
  -e DRONE_TIMEOUT=30m \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --restart=always \
  --name=drone-agent \
  drone/drone:0.5 agent
```

# Objectives

This is a daemon application that demonstrates the functional programming style in Scala (this is more important to the authors than quick fixes or features) and manages a (paid-for) resource pool of agents:

1. if the work queue is not empty, agents should be purchased until sufficient worker resource is available to deplete the queue
1. no cost should be incurred when the queue is empty for extended periods (or if the application gets into a bad state!)

# Inputs, Constraints and Thoughts

We can poll the drone server using its REST API http://readme.drone.io/api/ (a websockets API is available but is known to be unreliable) to get the work queue and infer expected timings of jobs.

A `SIGTERM` will stop an agent from accepting new jobs but will finish existing jobs (**this would be a good drone feature**).

It's possible to use the Amazon ECS service, see http://docs.datadoghq.com/integrations/ecs/ to set up an appropriate container (which is in a Task, which run in Instances, which are grouped in a Cluster).

A reasonable instance for libre projects is a [c4.xlarge](https://aws.amazon.com/ec2/instance-types/) instance (4 CPUs, 7.5GB RAM), costing ~$0.06/hour (rounded up to an hour) using the spot price. It is possible to set up budgets and alerts on Amazon, which is a good safety net in case of bugs.

There are many ways to programmatically communicate with Amazon http://docs.aws.amazon.com/AmazonECS/latest/APIReference/Welcome.html we'd ideally prefer to use a pure JVM solution without having to rely on proprietary binaries (e.g. REST).

In an ideal world we would respond to work queue events like so: when a job is submitted, spawn an agent that accepts one job and then shutdown.

However, the APIs available to us do not allow us to do this. Notably:

1. there is no way to spawn an agent that accepts one job and then exits (**this would be a good drone feature**)
1. telling an agent not to accept any more jobs cannot be done remotely
1. amazon charges are rounded up to the hour, so starting an Instance for 1 minute costs 1 hour
1. an ECS Task cannot be started or accepted unless there is a running Instance
1. worth repeating: Amazon charges per hour of Instance (not per hour of a Task)
1. if an agent is killed before it finishes, it is not rescheduled (**this would be a good drone feature**)
1. it might be possible to bid for cheaper CPU resource by block booking (to confirm)
1. it might be possible to extend a block-booked period of time

A possible algorithm may be:

1. receive input from AWS and drone that mutates a forgetful state containing our knowledge about what we have asked AWS/drone and what it has told us.
1. if the queue is non-empty and the resource list is empty and we have not requested more resources, then bid for an hour of Instance
1. when an Instance appears, start a Task within it
1. when the hour is almost up (timing TBD), make a go/no-go decision to renew for another hour (i.e. is work queue empty and no currently running jobs). If no-go, then tell the agents not to accept any more work and schedule the Task/Instance to die.

This business logic is to be fully implemented using the free monad pattern (i.e. no business logic in the implementation details) and ideally in such a way that another (non-Amazon) interpreter could be written.
