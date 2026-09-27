package de.heckenmann.visualagent.agent.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal fun ServerTlsEntry.toJson() =
    buildJsonObject {
        put("alias", alias)
        put("entryType", entryType)
        putJsonArray("certificates") { certificates.forEach { add(it.toJson()) } }
    }

internal fun ServerCertificateInfo.toJson() =
    buildJsonObject {
        put("subject", subject)
        put("issuer", issuer)
        put("sha256", sha256)
        put("notBefore", notBefore)
        put("notAfter", notAfter)
        put("isCertificateAuthority", basicConstraints >= 0)
        putJsonArray("subjectAlternativeNames") { subjectAlternativeNames.forEach { add(JsonPrimitive(it)) } }
        pem?.let { put("pem", it) }
    }
