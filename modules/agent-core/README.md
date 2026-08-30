# Agent Core

`:agent-core` contains cross-cutting agent runtime primitives shared by provider and tool
modules, currently including request cancellation.

It has no dependency on application, provider, tool, protocol, or UI implementations. This
keeps common execution contracts reusable and prevents lower-level modules from importing the
server composition root.

## Commands

Run from the repository root:

```bash
./gradlew :agent-core:build
./gradlew :agent-core:test
```
