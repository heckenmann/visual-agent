# Historical GraalVM Free Terms and Conditions Reference

The complete Oracle terms are available at:

<https://www.oracle.com/downloads/licenses/graal-free-license.html>

The [GraalJS licensing documentation](https://github.com/oracle/graaljs/tree/dd265d542fad72d0e7e1ae4032ef7c040911e700#license)
distinguishes the non-community isolate artifacts from UPL community artifacts.
These exact selected components appeared in the retained package inventory for
workflow `43187339406`:

- `org.graalvm.js:js-isolate-linux-amd64:25.5.5.0.3`
- `org.graalvm.js:js-isolate-darwin-aarch64:25.5.5.0.3`
- `org.graalvm.js:js-isolate-windows-amd64:25.5.5.0.3`

The versioned, non-publishing candidate in workflow
[`43368865856`](https://github.com/heckenmann/visual-agent/actions/runs/43368865856)
used source commit `30232f4318c5fd594c1166822a2f832b78120821`; all six platform
package manifests contain no `js-isolate` artifact. Retain this file and the
artifact hashes as historical evidence for the earlier candidate, not as proof
that a final release contains these components. Regenerate and inspect all
platform inventories from the final release source before deciding whether
these terms apply to the release.

Oracle's current GFTC permits redistribution of the unmodified Program without
charging recipients fees associated with the Program, bundled products, or
services involving it. Proprietary-rights markings must be preserved. The
project's stated distribution plan is a no-fee public GitHub release; reassess
the terms before introducing any paid distribution or service involving this
Program. This statement does not determine whether another license applies to
any separately licensed technology.

This file identifies the terms for the historical artifacts above; it is not
Oracle's agreement text, a grant under UPL, or a release approval. If a future
release contains a GFTC artifact, verify its exact binary, any transformation,
recipient access to the complete terms, proprietary-rights markings, and
separately licensed technology before recording release clearance.
