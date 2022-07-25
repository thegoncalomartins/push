# Push
**_Push_** is a Real Time Reactive Push Messaging API highly inspired by [Netflix](https://github.com/Netflix) 's [Zuul Push](https://github.com/Netflix/zuul/wiki/Push-Messaging), but with some differences. It was written in [Kotlin](https://kotlinlang.org/) with the [Spring](https://spring.io/) framework and it uses [Redis](https://redis.io/) as a message broker. **_Push_** provides [WebSocket](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API) and [Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events) endpoints that can be used to subscribe to real-time messages in a web environment. It also provides an HTTP endpoint to publish messages.

## Architecture
**_Push_** was designed with the goal of being scalable and possibly used in a distributed environment, it uses [Redis](https://redis.io/) as message broker.

The following diagram demonstrates the architecture behind **_Push_** in a [Kubernetes](https://kubernetes.io/) environment, where we can create a cluster for [Redis](https://redis.io/) with master and slave nodes in order to have horizontal scalability and resiliency and also multiple instances of the application itself for the same purpose.

### Push Architecture
This section describes the architecture of **_Push_** through a component diagram.

![](./docs/arch.png)

### Deployment Architecture
**_Push_** takes advantage of the "Deployment" workload API from [Kubernetes](https://kubernetes.io/).
This section describes how it works under the hood.
A Deployment provides declarative updates for Pods and ReplicaSets and is used to tell Kubernetes how to create or modify instances of the pods that hold a containerized application. Deployments can scale the number of replica pods, enable rollout of updated code in a controlled manner, or roll back to an earlier deployment version if necessary.

![](./docs/deployment-arch.png)

### StatefulSet Architecture
**_Push_** makes use of the "StatefulSet" workload API to manage the [Redis](https://redis.io/) cluster.
This section describes how a kubernetes StatefulSet works under the hood.
StatefulSet is the workload API object used to manage stateful applications. It manages the deployment and scaling of a set of Pods, and provides guarantees about the ordering and uniqueness of these Pods. Similar to a Deployment, a StatefulSet manages Pods that are based on an identical container spec.

![](./docs/statefulset-arch.png)

## Getting Started

Required software:
1. [`docker`](https://www.docker.com/products/docker-desktop/)
2. [`minikube`](https://minikube.sigs.k8s.io/docs/start/)
3. [`kubectl`](https://kubernetes.io/docs/tasks/tools/)

If you also want to compile the code directly on your machine you'll also need:
1. [`java`](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
2. [`gradle`](https://gradle.org/install/)

### Building from Source

To compile the code simply run
```bash
$ gradle clean build -x test -x compileAotMainJava
```

### Running Unit Tests

#### Locally

```bash
$ gradle clean build -x compileAotMainJava
```

#### With Docker

```bash
$ docker-compose -f docker-compose.test.yml build unit-tests && docker-compose -f docker-compose.test.yml run unit-tests
```

### Running Integration Tests

1. Starting the needed dependencies:
```bash
$ docker-compose -f docker-compose.test.yml up -d redis
```

#### Locally

```bash
$ gradle clean build integrationTest -x compileAotMainJava
```

#### With Docker

```bash
$ docker-compose -f docker-compose.test.yml build integration-tests && docker-compose -f docker-compose.test.yml run integration-tests
```

### Running the Application

#### Locally

1. Starting the needed dependencies:
```bash
$ docker-compose up -d redis
```

2. Booting the application
```bash
$ gradle clean build bootRun -x compileAotMainJava
```

#### With Docker

1. Booting
```bash
$ docker-compose up --build -d push
```
2. Terminating
```bash
$ docker-compose down
```
#### With Kubernetes

1. Booting
```bash
$ ./k8s/init-cluster.sh && kubectl port-forward svc/push 8080:8080 8000:8000
```
2. Terminating
```bash
$ kubectl delete -f ./k8s/cluster.yml
```

#### Testing

1. Publishing messages to channel `qwerty`
```bash
$ while true; do curl -H 'Content-Type: application/json' --request POST --data '{"channel":"qwerty","message":"a message to channel 'qwerty'"}' http://localhost:8080/messages ; done
# {"subscribers":0}
```
2. Subscribing to channel `qwerty` and channel `xyz` by SSE (Server-Sent Events)
```bash
$ curl 'http://localhost:8080/sse/messages?channels=xyz,qwerty'
# event:heartbeat
# data:{}

# event:message
# data:{"channel":"qwerty","message":"a message to channel qwerty"}
```
3. Subscribing to channel `qwerty` and channel `xyz` by WS (WebSockets)
```bash
$ websocat -v 'ws://localhost:8080/ws/messages?channels=qwerty,xyz'
# [INFO  websocat::lints] Auto-inserting the line mode
# [INFO  websocat::stdio_threaded_peer] get_stdio_peer (threaded)
# [INFO  websocat::ws_client_peer] get_ws_client_peer
# [INFO  websocat::ws_client_peer] Connected to ws
# {"event":"ping","data":{}}
# {"event":"message","data":{"channel":"qwerty","message":"a message to channel 'qwerty'"}}
```

## Environment Variables

| Name | Description | Default value |
| ---- | ----------- | ------------- |
| `push.reconnect.dither.min.duration` | Minimum value for a randomization window for each client's max connection lifetime. Helps in spreading subsequent client reconnects across time. | `120s` (120 seconds / 2 minutes) |
| `push.reconnect.dither.max.duration` | Maximum value for a randomization window for each client's max connection lifetime. Helps in spreading subsequent client reconnects across time. | `180s` (180 seconds / 3 minutes) |
| `push.client.close.grace.period.duration` | Time the server will wait for the client to close the connection or respond to a "ping" before it closes it forcefully from its side | `4s` (4 seconds) |
| `push.heartbeat.interval.duration` | Interval for when the server will emit heartbeats or pings to the client | `30s` (Every 30 seconds) |

## How **_Push_** Works

Both Server-Sent Events and WebSockets require persistent connections, so there are additional challenges that need to be addressed in order to provide scalability to the application.

// TODO complete

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

## Built with
<a href="https://www.docker.com/" target="_blank"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/docker/docker-original-wordmark.svg" alt="docker" width="40" height="40"/> </a>
<a href="https://gradle.org/" target="_blank"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/gradle/gradle-plain.svg" alt="gradle" width="40" height="40"/> </a>
<a href="https://kotlinlang.org" target="_blank"> <img src="https://www.vectorlogo.zone/logos/kotlinlang/kotlinlang-icon.svg" alt="kotlin" width="40" height="40"/> </a>
<a href="https://kubernetes.io" target="_blank"> <img src="https://www.vectorlogo.zone/logos/kubernetes/kubernetes-icon.svg" alt="kubernetes" width="40" height="40"/> </a>
<a href="https://redis.io" target="_blank"> <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/redis/redis-original-wordmark.svg" alt="redis" width="40" height="40"/> </a>
<a href="https://spring.io/" target="_blank"> <img src="https://www.vectorlogo.zone/logos/springio/springio-icon.svg" alt="spring" width="40" height="40"/> </a>

## References
- https://github.com/Netflix/zuul/wiki/Push-Messaging
- https://www.youtube.com/watch?v=IdR6N9B-S1E
- https://github.com/rustudorcalin/deploying-redis-cluster
- https://www.vmware.com/topics/glossary/content/kubernetes-deployment.html
- https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
- https://congdonglinux.com/rolling-updates-and-rollbacks-in-kubernetes/
- https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/
- https://jayendrapatil.com/kubernetes-components/
