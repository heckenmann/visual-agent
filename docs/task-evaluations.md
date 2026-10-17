# Task behavioral evaluations

The synthetic version-1 corpus is `application/src/test/resources/task-evals/v1.json`. Every scenario declares artifact contents, criterion, permitted write behavior, reported outcome, expected terminal state and whether acceptance is allowed. Offline trials have a 15-second deadline and isolated H2/workspace state. They inspect committed state, artifact digests and persisted attempts; they do not grade prompt substrings. Scripted reviewer approval deliberately tests that server evidence can still prevent false completion.

Run deterministic regression:

```sh
./gradlew :application:test --tests '*TaskBehavioralEvaluationTest' --tests '*TaskEvidenceTest' --tests '*TaskRoutingTest'
./gradlew :tool-standard:test --tests '*TaskToolAccessTest'
./gradlew :tool-javascript:test --tests '*JavaScriptTaskScopeTest'
./gradlew :task-core:test
```

Additional lifecycle tests cover stale contract edits, scoped nested calls and unavailable capabilities. Existing scheduler, streaming, batch and cancellation suites retain their existing responsibilities; dependency scheduling awaits #121.

Accepted attempts are preserved independently of the current todo result. Contract edits reopen completed work and clear current acceptance, but retain the historically accepted report. A late callback cannot overwrite that report. Input files are hashed before worker execution; rewritten sources cannot become original evidence. Resource-scoped calls reject symbolic-link aliases, including aliases to other folders within the workspace. Capability routing respects current explicit provider/model selections, model filtering and authoritative tool-support declarations; incomplete declarations remain unknown rather than invented negatives.

Review regressions additionally cover in-place editing of files declared as both inputs and deliverables, preserving immutable quote-source checks; directory creation against its derived destination rather than unrelated arguments; and complete supported UTF-8 inspection receipts. Binary, invalid UTF-8, truncated or version-mismatched manual evidence remains NOT_CHECKED even if a scripted reviewer approves. The reader reuses existing workspace MIME detection, the JDK charset decoder and serialization; no new parsing framework is required. UI lifecycle tests keep open overlays synchronized on completion and contract changes and reject delayed older snapshots.

Actual-model trials are disabled by default. Provision a local model manually, configure the existing provider profile, then explicitly run:

```sh
./gradlew :application:test --tests '*TaskModelEvaluationTest' \
  -Dvisualagent.taskEval.live=true \
  -Dvisualagent.taskEval.provider=local-profile-id \
  -Dvisualagent.taskEval.model=your-provisioned-model \
  -Dvisualagent.taskEval.trials=3 \
  -Dvisualagent.taskEval.label=candidate
```

An explicitly chosen remote profile can incur provider charges. Installing or running ordinary tests never starts these trials. The live harness reuses corpus artifact/criterion fixtures and reads existing synthetic evidence under a read-only task policy. It uses the actual Spring provider, worker, reviewer, tools, database and workspace. Scripted worker outcomes do not dictate live-model responses. Each live trial has a 90-second deadline and emits a separate JSON report under `build/reports/task-evaluations/`; it never stores credentials or raw tool output.

Reports record scenario/trial, mode, model/provider, prompt versions, model token limit, final state, artifact inspection, incorrect completion and elapsed time. Missing provider usage and cost are explicitly UNAVAILABLE. NOT_CHECKED means evidence was not verified; NOT_RUN identifies the other execution mode. A blocked task can be a correct outcome, not a failed runtime guarantee. Live results are descriptive; offline tests remain the correctness gate.

For comparable baseline/candidate runs, use the same corpus revision, provider profile/model, options and trial count on each prompt revision, set labels to baseline/candidate, and retain both report sets alongside the respective source commit IDs. Prompt versions identify policies; a label alone does not change a prompt. Compare observed outcomes and trial counts. A small sample does not establish a general reliability percentage. Main-agent conversation steering still has its dedicated prompt/context tests; this corpus does not claim to certify live conversational behavior.
