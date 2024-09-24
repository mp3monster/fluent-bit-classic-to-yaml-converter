The following table outlines the mappings between Fluentd and Fluent Bit - we only consider those provided as a vanilla deployment.

Inputs

| Fluentd (1.0)                                                | Fluent Bit (3.1)                                             |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [tail](https://docs.fluentd.org/input/tail)                  | [Tail](https://docs.fluentbit.io/manual/pipeline/inputs/tail), [Head](https://docs.fluentbit.io/manual/pipeline/inputs/head)? |
| [forward](https://docs.fluentd.org/input/forward)            | [Forward](https://docs.fluentbit.io/manual/pipeline/inputs/forward) |
| [udp](https://docs.fluentd.org/input/udp)                    | [UDP](https://docs.fluentbit.io/manual/pipeline/inputs/udp)  |
| [tcp](https://docs.fluentd.org/input/tcp)                    | [TCP](https://docs.fluentbit.io/manual/pipeline/inputs/tcp)  |
| [unix](https://docs.fluentd.org/input/unix)                  |                                                              |
| [http](https://docs.fluentd.org/input/http)                  | [HTTP](https://docs.fluentbit.io/manual/pipeline/inputs/http) |
| [syslog](https://docs.fluentd.org/input/syslog)              | [Syslog](https://docs.fluentbit.io/manual/pipeline/inputs/syslog) |
| [exec](https://docs.fluentd.org/input/exec)                  | [exec](https://docs.fluentbit.io/manual/pipeline/inputs/exec) |
| [sample](https://docs.fluentd.org/input/sample)              | [dummy](https://docs.fluentbit.io/manual/pipeline/inputs/dummy)? |
| monitor_agent                                                |                                                              |
| [windows_eventlog](https://docs.fluentd.org/input/windows_eventlog) | [Windows Event Log](https://docs.fluentbit.io/manual/pipeline/inputs/windows-event-log), [Windows Event Log (winevtlog)](https://docs.fluentbit.io/manual/pipeline/inputs/windows-event-log-winevtlog) |
|                                                              | [Collectd](https://docs.fluentbit.io/manual/pipeline/inputs/collectd) |
|                                                              | [CPU Log based metrics](https://docs.fluentbit.io/manual/pipeline/inputs/cpu-metrics) |
|                                                              | [Disk I/O Log based metrics](https://docs.fluentbit.io/manual/pipeline/inputs/disk-io-metrics) |
|                                                              | [Docker Log based metrics](https://docs.fluentbit.io/manual/pipeline/inputs/docker-metrics) |
|                                                              | [Docker Events](https://docs.fluentbit.io/manual/pipeline/inputs/docker-events) |
|                                                              | [Elasticsearch](https://docs.fluentbit.io/manual/pipeline/inputs/elasticsearch) |
|                                                              | [Exec WASI](https://docs.fluentbit.io/manual/pipeline/inputs/exec-wasi) |
|                                                              | [Fluent Bit metrics](https://docs.fluentbit.io/manual/pipeline/inputs/fluentbit-metrics) |
|                                                              | [Health](https://docs.fluentbit.io/manual/pipeline/inputs/health) |
|                                                              | [Kafka](https://docs.fluentbit.io/manual/pipeline/inputs/kafka) |
|                                                              | [Kernel Logs](https://docs.fluentbit.io/manual/pipeline/inputs/kernel-logs) |
|                                                              | [Kubernetes Events](https://docs.fluentbit.io/manual/pipeline/inputs/kubernetes-events) |
|                                                              | [Memory Metrics](https://docs.fluentbit.io/manual/pipeline/inputs/memory-metrics) |
|                                                              | [MQTT](https://docs.fluentbit.io/manual/pipeline/inputs/mqtt) |
|                                                              | [Network I/O Log based metrics](https://docs.fluentbit.io/manual/pipeline/inputs/network-io-metrics) |
|                                                              | [NGINX Exporter metrics](https://docs.fluentbit.io/manual/pipeline/inputs/nginx) |
|                                                              | [Node Exporter metrics](https://docs.fluentbit.io/manual/pipeline/inputs/node-exporter-metrics) |
|                                                              | [Podman metrics](https://docs.fluentbit.io/manual/pipeline/inputs/podman-metrics) |
|                                                              | [Process Log based metrics](https://docs.fluentbit.io/manual/pipeline/inputs/process) |
|                                                              | [Process Exporter Metrics](https://docs.fluentbit.io/manual/pipeline/inputs/process-exporter-metrics) |
|                                                              | [Prometheus Scape metrics](https://docs.fluentbit.io/manual/pipeline/inputs/prometheus-scrape-metrics) |
|                                                              | [Prometheus Remote Write](https://docs.fluentbit.io/manual/pipeline/inputs/prometheus-remote-write) |
|                                                              | [Random](https://docs.fluentbit.io/manual/pipeline/inputs/random) |
|                                                              | [Serial Interface](https://docs.fluentbit.io/manual/pipeline/inputs/serial-interface) |
|                                                              | [Splunk](https://docs.fluentbit.io/manual/pipeline/inputs/splunk) |
|                                                              | [stdin](https://docs.fluentbit.io/manual/pipeline/inputs/standard-input) |
|                                                              | [StatsD](https://docs.fluentbit.io/manual/pipeline/inputs/statsd) |
|                                                              | [Systemd](https://docs.fluentbit.io/manual/pipeline/inputs/systemd) |
|                                                              | [Thermal](https://docs.fluentbit.io/manual/pipeline/inputs/thermal) |
|                                                              | [OpenTelemetry](https://docs.fluentbit.io/manual/pipeline/inputs/opentelemetry) |
|                                                              | [Windows Exporter metrics](https://docs.fluentbit.io/manual/pipeline/inputs/windows-exporter-metrics) |

