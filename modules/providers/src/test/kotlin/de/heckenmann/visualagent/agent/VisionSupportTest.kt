package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies provider-neutral image MIME detection and inline encoding. */
class VisionSupportTest {
    @Test
    fun `data URL preserves the detected PNG MIME type`() {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        assertEquals("data:image/png;base64,iVBORw==", VisionSupport.dataUrl(image))
    }

    @Test
    fun `unknown image formats fail explicitly`() {
        assertFailsWith<IllegalStateException> { VisionSupport.dataUrl(byteArrayOf(0x01, 0x02)) }
    }
}
