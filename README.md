# Push
"Push" is a Real Time Reactive Push Messaging API highly inspired by [Netflix](https://github.com/Netflix) 's [Zuul Push](https://github.com/Netflix/zuul/wiki/Push-Messaging), but with some differences. It was written in [Kotlin](https://kotlinlang.org/) with the [Spring](https://spring.io/) framework and it uses [Redis](https://redis.io/) as a message broker. "Push" provides [WebSocket](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API) and [Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events) endpoints that can be used to subscribe to real-time messages in a web environment. It also provides an HTTP endpoint to publish messages.

## Architecture
"Push" was designed with the goal of being scalable and possibly used in a distributed environment, it uses [Redis](https://redis.io/) as message broker.

The following diagram demonstrates the architecture behind "Push" in a [Kubernetes](https://kubernetes.io/) environment, where we can create a cluster for Redis with master and slave nodes in order to have horizontal scalability and resiliency and also multiple instances of the application itself for the same purpose.

### Push Architecture
![](./docs/arch.png)

### Deployment Architecture
This section describes how a kubernetes deployment works under the hood.

![](./docs/deployment-arch.png)

### StatefulSet Architecture
This section describes how a kubernetes stateful set works under the hood.

![](./docs/statefulset-arch.png)

## Getting Started
// TODO

## To Do List
* [ ] Add unit and integration tests
* [ ] Write documentation
* [X] Dockerize application
* [X] Add observability and monitoring
* [X] Add heartbeats, timeouts and reconnection events to the SSE endpoint
* [X] Add ping-pongs, timeouts and reconnection events to the WS endpoint
* [X] Create [Kubernetes](https://kubernetes.io/) cluster with redundancy

## Improvements
* [ ] Automatically configure cluster for Redis
  * [ ] Configure horizontal pod autoscaler for Redis
* [ ] Configure horizontal pod autoscaler for Push based on custom metrics (e.g. throughput, number of concurrent connections, etc)
* [ ] Add [Sharded Pub/Sub](https://redis.io/docs/manual/pubsub/#sharded-pubsub) capacity
* [ ] Use [Terraform](https://www.terraform.io/) to deploy to Kubernetes
* [ ] Add "acks" to websockets messages
* [ ] Configure native compilation

## References
- https://github.com/rustudorcalin/deploying-redis-cluster
