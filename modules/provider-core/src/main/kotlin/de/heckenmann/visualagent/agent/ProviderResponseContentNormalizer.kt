package de.heckenmann.visualagent.agent

/**
 * Normalizes provider-neutral assistant text before it crosses into application code.
 *
 * Some chat transports serialize a standard assistant role marker as part of the content instead
 * of carrying it solely in the structured message role. The normalizer removes that framing while
 * preserving ordinary user-facing text. It deliberately has no provider, model, or template branch.
 */
object ProviderResponseContentNormalizer {
    /**
     * Removes one leading standard assistant role marker from provider response text.
     *
     * @param content Raw provider response text
     * @return Response text without a leading protocol role marker
     */
    fun normalize(content: String): String = assistantRolePrefix.replaceFirst(content, "")

    // A bare "assistant" is valid response text. Only remove unambiguous role framing.
    private val assistantRolePrefix = Regex("^\\s*(?:(?i:<\\|assistant\\|>)|(?i:assistant:))\\s*")
}
