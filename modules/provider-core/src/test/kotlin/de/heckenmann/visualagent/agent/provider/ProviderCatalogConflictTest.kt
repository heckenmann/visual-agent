package de.heckenmann.visualagent.agent.provider

import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies bounded retries and absence of publications after unsuccessful persistence. */
class ProviderCatalogConflictTest {
    @Test
    fun `persistent conflicts fail after sixteen attempts without publishing a change`() {
        val conflicts = AtomicBoolean(false)
        val attempts = AtomicInteger()
        val preferences =
            object : ProviderPreferenceStore {
                private val values = mutableMapOf<String, String>()

                override fun getPreference(key: String): String? = values[key]

                override fun setPreference(
                    key: String,
                    value: String,
                ) {
                    values[key] = value
                }

                override fun compareAndSetPreferenceReactive(
                    key: String,
                    expected: String?,
                    value: String,
                ): Mono<Boolean> =
                    if (conflicts.get()) {
                        Mono.fromCallable {
                            attempts.incrementAndGet()
                            false
                        }
                    } else {
                        super.compareAndSetPreferenceReactive(key, expected, value)
                    }
            }
        val runtime = DefaultProviderRuntimeConfig()
        val catalog = ProviderCatalogService(preferences, runtime)
        val before = catalog.listProviders()
        var publications = 0
        catalog.addChangeListener { publications++ }.use {
            conflicts.set(true)
            assertFailsWith<IllegalStateException> { catalog.setActiveProvider("openai") }
            assertEquals(16, attempts.get())
            assertEquals(before, catalog.listProviders())
            assertEquals("ollama", runtime.llmProvider)
            assertEquals(0, publications)
        }
    }
}
