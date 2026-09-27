# UC-0000133: Manage the Visual Agent Server Key Store

## Goal

Let an explicitly authorized main agent inspect public metadata in Visual Agent's application-owned key store and generate server or CA key pairs and certificates that Visual Agent can use without exposing private key material.

## Actors

- The user enables the server key-store tool for the main agent.
- The main agent generates or inspects server-owned credentials at the user's request.

## Preconditions

- The Visual Agent server is running and has a writable server data root.
- The `security:keystore` tool has been explicitly enabled by the user. It is disabled by default and is never offered to sub-agents.
- Key and certificate algorithms, SAN values, validity intervals, and aliases satisfy the server's constrained policy.

## Main Flow

1. The model calls `security_keystore` with `list` or `inspect` to view aliases, certificate chains, and public metadata.
2. To create a CA, it supplies an alias, subject, and bounded validity period through `generateCertificate` with `certificateAuthority=true`.
3. To generate a server credential, it supplies a CA signing alias, subject, DNS/IP SANs, and bounded validity period through `generateCertificate` with `certificateAuthority=false`.
4. The server generates the key pair and signed certificate with the JDK `keytool` utility and stores them in the managed PKCS#12 key store.
5. The private key remains encrypted at rest and is available to the server TLS configuration without being returned through tools.
6. Configure `visualagent.server.tls.key-store-alias` to select the generated alias for the optional loopback-network gRPC endpoint. If PEM certificate properties are configured, they continue to take precedence for compatibility.
7. Restart the server so the loopback-network gRPC listener loads the selected key and certificate.
8. The server audit log records each mutation's operation, store, validated alias, certificate fingerprint when available, and outcome.

## Result

Generated material is persisted only inside the server-managed security directory and exposed to the server through Spring Boot's `SslBundle` API. The agent can inspect and export public certificates, but cannot read or export private keys or store passwords. The optional loopback-network gRPC endpoint can select a generated server certificate by alias; the default managed alias is `server`. Audit entries never include passwords, private-key bytes, raw certificate material, or raw exception messages.

This use case covers server identity material and its use by the optional gRPC listener. A complete remote desktop connection, including client trust configuration and mutual TLS, belongs to issue #345 rather than this server-store feature.

## Tool Calls

- `security_keystore` with `action=list` lists bounded alias, entry type, and certificate metadata.
- `security_keystore` with `action=inspect` returns one exact alias's certificate chain, fingerprints, validity, key usage, and SANs.
- `security_keystore` with `action=generateCertificate` creates a constrained self-signed CA or a TLS server certificate/key pair signed by an existing managed CA. Server certificates require a managed signing alias and at least one validated DNS or IP SAN.
- `security_keystore` with `action=exportCertificate` exports only the public certificate in PEM form.
- No action returns, imports, or exports private key bytes or accepts an arbitrary store path, shell command, or password.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.ServerKeyStoreTool`
- `de.heckenmann.visualagent.agent.tools.ServerTlsMaterialPort`
- `de.heckenmann.visualagent.security.ManagedTlsMaterialService`
- `de.heckenmann.visualagent.server.VisualAgentGrpcServer`

## Acceptance Criteria

- The tool is disabled by default, main-agent-only, and omitted from model schemas while disabled.
- Private material is kept in the server-managed PKCS#12 file with restrictive filesystem permissions where supported.
- A corrupt primary key-store file is restored only from a validated managed backup; symlinked TLS directories, store files, and password files are rejected.
- Recovery must not restore a private key that was removed by a successful mutation.
- A missing or empty password file fails closed; the server leaves existing key material untouched.
- Generated certificates use allowed key/signature algorithms, appropriate CA/server X.509 extensions, and bounded validity.
- Server certificates include subject alternative names and are usable by the configured gRPC server after the documented activation step.
- A local TLS handshake verifies the selected gRPC certificate chain and hostname against the managed trust store.
- No result, error, or log contains a password, private key, raw certificate payload, or arbitrary local path.
