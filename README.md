# Monitor Tutorial

Swim application tutorial to monitor a cluster of machines.

## Getting Started

[Install JDK 11+](https://www.oracle.com/technetwork/java/javase/downloads/index.html)

- Ensure that your `JAVA_HOME` environment variable points to the Java installation.
- Ensure that your `PATH` includes `$JAVA_HOME`.

## Running the Tutorial

### Running the Server

```bash
$ ./gradlew run
```

### Running a Client

```bash
$ ./gradlew -Dhost=<warp-address-of-server> runClient
```

Example:

```bash
$ ./gradlew -Dhost=warp://localhost:9001 runClient
```

## Streaming APIs

### Introspection APIs

#### Stream High level stats
```
swim-cli sync -h warp://localhost:9001 -n swim:meta:mesh -l pulse
```

### Application APIs

#### Streaming APIs for top level Monitor
```
swim-cli sync -h warp://localhost:9001 -n /monitor -l machines
swim-cli sync -h warp://localhost:9001 -n /monitor -l clusters
```

#### Streaming APIs for a given Machine
```
swim-cli sync -h warp://localhost:9001 -n /machine/my-machine -l status
swim-cli sync -h warp://localhost:9001 -n /machine/my-machine -l statusHistory
swim-cli sync -h warp://localhost:9001 -n /machine/my-machine -l systemInfo
swim-cli sync -h warp://localhost:9001 -n /machine/my-machine -l usage
swim-cli sync -h warp://localhost:9001 -n /machine/my-machine -l processes
swim-cli sync -h warp://localhost:9001 -n /machine/my-machine -l sessions
```

#### Streaming APIs for a Cluster
```
swim-cli sync -h warp://localhost:9001 -n /cluster/abc -l machines
swim-cli sync -h warp://localhost:9001 -n /cluster/abc -l status
swim-cli sync -h warp://localhost:9001 -n /cluster/abc -l statusHistory
```

## Running the UI


Now, under the `/ui` folder under project root, open `index.html` as a local file in your web browser to see results from monitoring your locate machine populate a chart.
