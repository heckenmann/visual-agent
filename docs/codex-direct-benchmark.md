# Direct Provider Local Benchmarks

Run `./gradlew :provider-codex-direct:benchmarkDirectProvider --no-daemon`.
The opt-in benchmark uses synthetic credentials and a loopback-only HTTP/SSE server.
It never contacts OpenAI, performs OAuth login, or consumes paid inference.
Measurements are observations, not correctness-test deadlines or CI thresholds.

## Scope

- Ten warm-up batches and fifty measured batches per scenario, in a fixed order.
- Real official SDK requests and Spring AI response mapping. Text, tool, concurrency,
  cancellation, and credential scenarios use the production Responses tool loop.
  Token counting and prepared attachment mapping use the production lease/transport.
- One retained tool-loop instance, like the Spring singleton. Recreating it per
  sample would incorrectly include tokenizer initialization in every request.
- Cold HTTP batches construct and close an SDK transport; warm batches reuse it
  through the production generation pool. Credential rotation changes request-scoped
  SDK views. Trust rotation changes the pool generation, not the fixture's certificates.
- The media scenarios use opaque synthetic 64-KiB PNG/PDF payloads; the fixture
  returns a constant count and never decodes or analyzes their contents. They measure
  encoding, serialization, counting round-trip, and streaming overhead, not image/PDF
  interpretation or correctness of an OpenAI token estimate.
- A separate count-only case reuses a pre-encoded synthetic PDF to isolate count
  request serialization and HTTP exchange from encoding/inference. Every sample
  still sends a real local count request; no count result is cached. Its first-text
  result is unavailable by definition. The 1-MiB input-item serialization microbenchmark
  uses the official SDK JSON mapper and excludes prior encoding and network time.
- Tool results come from a synthetic registry, without database/filesystem work.
  Two registry tool rounds require three HTTP requests. Concurrent batches contain
  four requests; their first-text measurement is the first visible text in the batch,
  not a per-request percentile. Cancellation stops after its first visible delta.
- Allocation is caller-thread JVM allocation only. It excludes SDK/server worker
  allocations, native memory, and retained heap. Payload sizes count application-body
  UTF-8 bytes, excluding HTTP/TCP headers and framing. Connections count unique accepted
  channel IDs, not repeated per-request callbacks on a reused channel.
- Connection and payload counters exclude warm-up. Thus zero new connections means
  measured requests reused already-warm channels, not that no connection existed.
- Loopback HTTP excludes TLS handshake, managed-store access, OAuth refresh, hosted
  inference, and cache-hit behavior. Separate functional tests verify TLS policy,
  credential persistence/refresh, permissions, isolation, and cancellation.

## Recorded Results

Two serial runs on October 23, 2026, macOS ARM64, Java 24, same machine/configuration.
All values below measure the current implementation. They do not establish a
historical before/after hosted-inference result or an end-user service-level promise.
Order/JIT/GC effects remain possible; compare repeated runs rather than unlike rows.

| Scenario | Total median ms, run 1 / 2 | Total p95 ms, run 1 / 2 | First-text median ms, run 1 / 2 | First-text p95 ms, run 1 / 2 |
| --- | --- | --- | --- | --- |
| Cold text | 2.129 / 2.052 | 3.295 / 2.559 | 1.624 / 1.561 | 2.626 / 1.913 |
| Warm text | 1.233 / 1.226 | 1.422 / 1.390 | 0.912 / 0.911 | 1.088 / 1.027 |
| PNG count + inference | 2.155 / 2.119 | 5.042 / 3.646 | 1.944 / 1.916 | 4.783 / 3.435 |
| PDF count + inference | 1.723 / 1.854 | 4.782 / 2.027 | 1.578 / 1.673 | 4.272 / 1.858 |
| Two tool rounds | 3.192 / 2.712 | 5.001 / 4.050 | 3.009 / 2.515 | 4.770 / 3.247 |
| Four concurrent requests | 1.253 / 1.087 | 2.096 / 1.263 | 0.838 / 0.656 | 1.344 / 0.769 |
| Cancel after first text | 112.251 / 112.935 | 116.049 / 116.830 | 4.613 / 5.441 | 5.715 / 7.131 |
| Rotated credential | 0.622 / 0.638 | 0.739 / 0.909 | 0.483 / 0.498 | 0.564 / 0.712 |
| Replaced trust generation | 0.847 / 0.853 | 0.973 / 0.995 | 0.726 / 0.726 | 0.857 / 0.857 |

Run 2 counters across fifty measured batches:

| Scenario | Caller allocation bytes/batch | Requests | New connections | Request-body bytes | Response-body bytes |
| --- | --- | --- | --- | --- | --- |
| Cold text | 95,564 | 50 | 50 | 7,150 | 35,750 |
| Warm text | 41,684 | 50 | 0 | 7,150 | 35,750 |
| PNG count + inference | 734,227 | 100 | 0 | 8,757,900 | 38,400 |
| PDF count + inference | 733,570 | 100 | 0 | 8,759,100 | 38,400 |
| Two tool rounds | 52,794 | 150 | 0 | 86,550 | 101,950 |
| Four concurrent requests | 163,422 | 200 | 0 | 28,600 | 143,000 |
| Cancel after first text | 41,080 | 50 | 50 | 7,050 | 8,500 |
| Rotated credential | 40,876 | 50 | 0 | 7,150 | 35,750 |
| Replaced trust generation | 94,563 | 50 | 50 | 7,150 | 35,750 |

The fixture demonstrates actual channel reuse for warm requests and credential
views, and fresh channels after generation replacement. Cancellation of an open
SSE body requires a replacement connection on the next batch. The measured
cancellation batch includes stream disposal and is substantially slower than
time-to-first-text; this observation alone does not attribute the delay to a
particular library or justify a custom cancellation/HTTP implementation.

The separate setup/1-MiB encoding microbenchmark and its earlier two-run results
remain in [the provider design](codex-direct-provider.md#httpsse-runtime-and-measurement).

Two subsequent serial runs with the same settings additionally isolate serialization
and counting. These extend the scenario matrix; they are not a before/after comparison.

| Scenario | Median ms, run 1 / 2 | p95 ms, run 1 / 2 | Caller allocation bytes/batch, run 2 |
| --- | --- | --- | --- |
| 1-MiB prepared input JSON serialization | 1.363 / 1.356 | 1.641 / 2.326 | 2,743,729 |
| Prepared 64-KiB PDF count only | 0.785 / 0.800 | 1.132 / 0.943 | 25,777 |

The fifty measured count-only batches sent fifty HTTP requests over already-warm
connections, with 4,376,500 request-body bytes and 2,650 response-body bytes.
