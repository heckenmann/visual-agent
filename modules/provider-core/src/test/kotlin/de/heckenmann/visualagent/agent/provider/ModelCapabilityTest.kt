package de.heckenmann.visualagent.agent.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelCapabilityTest {
    @Test
    fun `openai inference distinguishes chat vision speech images and embeddings`() {
        assertTrue(ModelCapability.VISION in OpenAiModelCapabilities.infer("gpt-4o"))
        assertTrue(ModelCapability.EMBEDDING in OpenAiModelCapabilities.infer("text-embedding-3-small"))
        assertTrue(ModelCapability.AUDIO_INPUT in OpenAiModelCapabilities.infer("whisper-1"))
        assertTrue(ModelCapability.AUDIO_OUTPUT in OpenAiModelCapabilities.infer("tts-1"))
        assertTrue(ModelCapability.IMAGE_GENERATION in OpenAiModelCapabilities.infer("gpt-image-1"))
        assertFalse(ModelCapability.CHAT in OpenAiModelCapabilities.infer("whisper-1"))
        assertEquals(setOf(ModelCapability.CHAT), OpenAiModelCapabilities.infer("custom-model"))
    }

    @Test
    fun `unknown capabilities never grant specialized model access implicitly`() {
        val incomplete = ProviderModelConfig("unknown")
        val manual = ProviderModelConfig("manual", capabilities = setOf("vision"), capabilitiesComplete = true)

        assertTrue(incomplete.supportsCapability(ModelCapability.CHAT))
        assertFalse(incomplete.supportsCapability(ModelCapability.VISION))
        assertTrue(manual.supportsCapability(ModelCapability.VISION))
        assertFalse(manual.supportsCapability(ModelCapability.AUDIO_INPUT))
        assertEquals(ModelCapability.TOOLS, ModelCapability.fromProviderValue("tool_calling"))
    }
}
