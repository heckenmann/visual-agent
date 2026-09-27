# UC-0000132: Manage the Visual Agent Server Trust Store

## Goal

Let an explicitly authorized main agent inspect and manage certificates in Visual Agent's application-owned server trust store, without exposing private key material or modifying the JDK's global `cacerts` store.

## Actors

- The user enables the server trust-store tool for the main agent.
- The main agent inspects, imports, exports, or removes public certificates when requested by the user.

## Preconditions

- The Visual Agent server is running and has a writable server data root.
- The `security:truststore` tool has been explicitly enabled by the user. It is disabled by default and is never offered to sub-agents.
- The certificate is supplied as bounded ASCII PEM content; the tool does not accept arbitrary file paths or download URLs.

## Main Flow

1. The model calls `security_truststore` with `list` or `inspect` to view aliases and public certificate metadata.
2. To add a CA, the model supplies an alias and certificate content through `importCertificate`.
3. The server validates the X.509 certificate, writes the managed PKCS#12 trust store atomically, and preserves a recoverable prior version.
4. To remove an entry, the model supplies its exact alias through `removeCertificate`.
5. The tool reports the certificate fingerprint and whether a server restart is required before dependent clients use the change.
6. The server audit log records each mutation's operation, store, validated alias, certificate fingerprint when available, and outcome.

## Result

The managed trust store is exposed through Spring Boot's `SslBundle` API and combined with platform/JVM trust roots for Visual Agent's server-side TLS clients; normal hostname verification remains enabled. Spring AI/OpenAI, Ollama, the shared Spring HTTP transport, and the GitHub release client use the managed trust manager. Local HTTPS integration tests verify managed-CA acceptance and hostname rejection for these consumers. Each HTTPS client must explicitly use the bundle; adding a CA does not modify client-JVM or external-process trust. Restart the server after changing trusted roots so long-lived clients reload them. Audit entries never include passwords, certificate payloads, private keys, or raw exception messages. Results expose public certificate metadata only; they never include passwords, raw store bytes, or private keys.

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

## Acceptance Criteria

- The tool is disabled by default, main-agent-only, and omitted from model schemas while disabled.
- Store paths resolve beneath the server data root and cannot traverse outside the managed security directory.
- Only CA certificates can be trusted; every mutation is atomic and recoverable.
- A corrupt primary trust-store file is restored only from a validated managed backup; symlinked store or backup paths are rejected.
- A missing or empty password file fails closed; the server never replaces credentials for an existing store.
- Tool output and logs contain no passwords, key bytes, certificate payloads, or arbitrary local paths.
- The activation result accurately describes when server-side TLS clients begin using an imported CA.
