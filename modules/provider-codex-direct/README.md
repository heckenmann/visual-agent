# Direct Codex Provider

Server-side, CLI-independent provider for the documented public OpenAI Responses
endpoint. Included by `:providers`; presentation modules use protocol ports only.

## Ownership

- `CodexDirectProvider`: existing profile-aware provider contract, model discovery,
  prompt mapping, images/files, and reactive response aggregation.
- `DirectResponsesModels`: Spring AI Responses models and managed TLS trust.
- `DirectResponsesTransport` / `DirectFileTransport`: supported native-part extensions
  for OAuth tool declarations and exact input filenames; no independent HTTP runtime.
- `DirectOAuthProtocol`: Spring Security authorization-code exchange, JWT validation,
  token refresh, and discovered revocation.
- `DirectProviderAuthorizationService`: bounded browser/loopback login sessions.
- `DirectOAuthCredentialStore`: owner-protected files, atomic rotation, and profile
  isolation under the authoritative server data directory.

## Authentication

Select either ChatGPT OAuth or a user-supplied Platform API key per profile. API-key
requests incur Platform usage charges; OAuth requires explicit eligible plan usage
authorization. Neither mode falls back to the other. No inference runs simply
because the user selects the provider.

OAuth records are protected by operating-system permissions, not encrypted at rest.
Only opaque references cross protocol boundaries. Do not log or export tokens.
Remote-server browser authorization requires explicit loopback forwarding.

## Verification

```bash
./gradlew :provider-codex-direct:ktlintFormat
./gradlew :provider-codex-direct:test --no-daemon
```

Automated tests use synthetic credentials and local protocol fixtures. Real account
login or billable inference requires explicit user authorization. The framework
upgrade currently uses prerelease Spring AI 2.1.0-M1 and Spring Boot 4.2.0-M2;
complete application and package quality gates are required before publication.

See [design, route restrictions, and verification scope](../../docs/codex-direct-provider.md)
and [use case 152](../../docs/usecases/uc_0000152_use_direct_codex_with_explicit_authentication.md).
