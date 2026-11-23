# H2 indexes for chat preparation

[Issue #581](https://github.com/heckenmann/visual-agent/issues/581) covers the database reads before the main agent calls its provider.

H2 2.5.252 was tested with the V1–V7 schema, 200 or 100,000 synthetic history rows, and 2 or 1,000 todos respectively. Every seventeenth message was assigned to a second session. Each group of ten rows included one user message, one assistant message, three summary rows, and five audit rows. The context queries used a 20-turn and 20-record limit. Tests used the full dialogue, summary and todo SQL from the stores.

| Dataset | User boundary scan count | Dialogue query median | Summary query median |
| --- | ---: | ---: | ---: |
| 200 history rows | 190 → 20 | 0.096 → 0.074 ms | 0.080 → 0.063 ms |
| 100,000 history rows | 94,119 → 9,413 | 0.068 → 0.037 ms | 0.049 → 0.036 ms |

Scan counts are from H2 `EXPLAIN ANALYZE`. The baseline used the session/creation index for the boundary with a residual role filter. With V8, H2 used both session and role as index predicates. The full query still scans context-policy rows and the boundary can still scan matching users. Timings are nine in-memory JDBC runs per query after three warmups, in baseline-then-candidate order. At this scale the timings are sensitive to JVM warmup and do not establish desktop latency.

The wider context-policy index left outer scan counts unchanged in both datasets. The position index let H2 avoid sorting todos but still visited all 1,001 entries in the large fixture, counting the end of the scan. Its large-fixture median was 0.084 ms versus 0.101 ms immediately before adding it, with ordered runs and no clear separation from warmup effects. The migration retains both existing query paths. Each extra secondary index stores keys for every indexed row and adds maintenance to inserts, deletes and updates to its keys. For todos that includes reordering. The observed reduction in boundary scans justifies the role index. The other candidates lack comparable evidence.

A separate file-backed test seeded 100,000 history rows and 1,000 todos with each candidate in place, then performed ten full todo reorders and compacted the database. Two runs per configuration in forward and reverse order put the compacted baseline at 8.68–8.69 MB, the role index at 10.36–10.37 MB, the wider context index at 9.46–9.49 MB, and the todo index at 8.68–8.69 MB. The role index added about 1.68 MB in this fixture. In the reverse-order runs, seed times were 1,306 ms for baseline and 1,472 ms for the role index. The todo index took 47–67 ms for ten reorders versus 22–105 ms without a candidate. The variation between runs prevents a reliable write-latency estimate.

## Additional application queries

V8 also indexes the session and chronological sort keys used by visible conversation history, the import order of workspace files (replacing the shorter import-time index), and the status and creation order of filtered sub-agent lists. These indexes target those query shapes. The chat benchmark above measured the role index in isolation and does not quantify the impact of these additional indexes.

History page selection includes the constant `session_id` as its first sort key so H2 can read the index in order. In the full grouped history query with 10,000 messages in one session, H2 reported 20 index entries visited by the first-page selection for a limit of 20. The separate total count and outer query still showed scans of 10,001 entries.
