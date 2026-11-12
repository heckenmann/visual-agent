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

The current local macOS package inventory contains no `js-isolate` artifact.
That local result does not establish the contents of a future cross-platform
release candidate; regenerate and inspect all four platform inventories before
deciding whether these terms apply to a release. Retain this file as historical
evidence for the earlier candidate, not as proof that the current package
contains these components.

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
