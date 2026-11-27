# UC-0000132: Manage the Visual Agent Server Trust Store

## Goal

Let users manage the connected Visual Agent server's additional trusted CAs in Settings and provider onboarding. Let an explicitly authorized main agent use the same store through its dedicated tool.

## Actors

- The user administers managed CAs and endpoint-scoped server certificates in **Settings > Certificates**, or imports them while setting up a provider.
- The main agent inspects, imports, exports, or removes public certificates when requested by the user.

## Preconditions

- The Visual Agent server is running and has a writable server data root.
- For manual CA import, the certificate is supplied as bounded ASCII PEM content. The client sends only the public PEM from the selected file or pasted text.
- For the model tool flow, the user explicitly enables `security:truststore`. It is disabled by default and is never offered to sub-agents.

## Main Flow

### Settings

1. In **Settings > Certificates**, the user sees the managed CAs and server certificates. **Inspect** shows the subject, issuer, validity dates, and SHA-256 fingerprint. A server certificate shows its exact hostname and port.
2. To add a CA, the user supplies an alias and one public PEM X.509 certificate, then selects **Trust certificate**. The server validates the CA and persists it. The list is refreshed.
3. **Export** retrieves only the public certificate and lets the user save it as a PEM file on the desktop.
4. Before removing a certificate, a confirmation dialog shows its alias or endpoint and its fingerprint. The server removes the entry only if the fingerprint still matches, then the client refreshes the list.
5. Provider discovery and validation use the changed trust on the next request. Restart the server to reload clients that keep TLS connections open.

### Main agent

1. The model calls `security_truststore` with `list` or `inspect` to view aliases and public certificate metadata.
2. To add a CA, the model supplies an alias and certificate content through `importCertificate`.
3. The server validates the X.509 certificate, writes the managed PKCS#12 trust store atomically, and preserves a recovery copy of the newly saved state.
4. To remove an entry, the model supplies its exact alias through `removeCertificate`.
5. The tool reports the certificate fingerprint and whether a server restart is required before dependent clients use the change.
6. The server audit log records each mutation's operation, store, validated alias, certificate fingerprint when available, and outcome.

## Result

The managed trust store is exposed through Spring Boot's `SslBundle` API and combined with platform/JVM trust roots for Visual Agent's server-side TLS clients. Normal hostname verification remains enabled. Spring AI/OpenAI, Ollama, the shared Spring HTTP transport, and the GitHub release client use the managed trust manager. The application trust store is separate from the server identity store. Each HTTPS client must explicitly use the bundle; adding a CA does not modify client-JVM or external-process trust. Audit entries never include passwords, certificate payloads, private keys, or raw exception messages. Only explicit public certificate export returns PEM.

## Tool Calls

- `security_truststore` with `action=list` lists bounded alias, entry type, subject, issuer, validity, CA status, and SHA-256 fingerprint metadata.
- `security_truststore` with `action=inspect` returns the same public metadata for one exact alias and may include its public PEM certificate.
- `security_truststore` with `action=importCertificate` imports a bounded ASCII PEM X.509 certificate under a new alias. It rejects key entries, duplicate aliases, malformed certificates, and non-CA certificates.
- `security_truststore` with `action=removeCertificate` removes one exact managed alias; it cannot modify the platform trust store.
- The tool accepts no arbitrary paths, shell commands, passwords, or remote URLs.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ServerTrustStoreTool`
- `de.heckenmann.visualagent.agent.tools.ServerTlsMaterialPort`
- `de.heckenmann.visualagent.security.ManagedTlsMaterialService`
- `de.heckenmann.visualagent.server.SpringCertificateTrustPort`
- `de.heckenmann.visualagent.ui.settings.certificates.CertificatesPanel`
- `de.heckenmann.visualagent.ui.onboarding.OnboardingCertificateControl`

## Acceptance Criteria

- The tool is disabled by default, main-agent-only, and omitted from model schemas while disabled.
- Store paths resolve beneath the server data root and cannot traverse outside the managed security directory.
- Only CAs become global trust anchors. Reviewed server certificates apply exclusively to the HTTPS hostname and port approved during onboarding (UC-0000114). Every mutation is atomic and recoverable.
- A corrupt primary trust-store file is restored only from a validated managed backup; symlinked store or backup paths are rejected.
- Recovery must not restore a CA that was removed by a successful mutation.
- A missing or empty password file fails closed; the server never replaces credentials for an existing store.
- Tool output and logs contain no passwords, key bytes, certificate payloads, or arbitrary local paths.
- The activation result accurately describes when server-side TLS clients begin using an imported CA.
- Settings can import public CA PEM files and inspect, export, or remove managed certificates. Removal requires confirmation and rejects stale fingerprints. The saved changes survive a server restart.
- Onboarding can retry a compatible HTTPS provider after importing its CA or endpoint certificate without restarting or losing the draft. Hostname checks still reject a mismatched endpoint.

## Dependency Decision

The application reuses its existing JCA certificate policy and Spring Boot `SslBundle` integration for trust. Existing FileKit dialogs handle local file selection and export. A new TLS implementation or platform trust manager would duplicate those responsibilities and would not manage the connected server's existing store.
