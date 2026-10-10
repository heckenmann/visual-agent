package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.provider.DefaultProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderConfiguration
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises conflicting catalog mutations through real Spring-managed H2/R2DBC persistence. */
class ProviderCatalogConcurrencyTest {
    @Test
    fun `two catalog instances retain concurrent edits to distinct providers`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:provider-concurrent").use { db ->
            val barrier = CyclicBarrier(2)
            val reads = AtomicInteger()
            val armed = AtomicBoolean(false)
            val preferences =
                object : ProviderPreferenceStore by db.preferenceStore {
                    override fun getPreferenceReactive(key: String): Mono<String> =
                        db.preferenceStore.getPreferenceReactive(key).publishOn(Schedulers.boundedElastic()).doOnNext {
                            if (armed.get() && key == CATALOG && reads.getAndIncrement() < 2) barrier.await(10, TimeUnit.SECONDS)
                        }
                }
            val first = ProviderCatalogService(preferences)
            val second = ProviderCatalogService(preferences)
            first.saveProvider(profile("one"))
            first.saveProvider(profile("two"))
            armed.set(true)
            Executors.newFixedThreadPool(2).use { executor ->
                val one = executor.submit { first.saveProvider(profile("one").copy(name = "Updated one")) }
                val two = executor.submit { second.saveProvider(profile("two").copy(name = "Updated two")) }
                one.get(15, TimeUnit.SECONDS)
                two.get(15, TimeUnit.SECONDS)
            }
            assertEquals("Updated one", first.getProvider("one")?.name)
            assertEquals("Updated two", second.getProvider("two")?.name)
        }
    }

    @Test
    fun `discovery rebases on a committed edit and selection without restoring stale fields`() {
        for (mode in 0..2) {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:provider-refresh").use { db ->
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val armed = AtomicBoolean(false)
                val preferences =
                    object : ProviderPreferenceStore by db.preferenceStore {
                        override fun compareAndSetPreferenceReactive(
                            key: String,
                            expected: String?,
                            value: String,
                        ): Mono<Boolean> =
                            Mono
                                .defer {
                                    if (armed.compareAndSet(true, false)) {
                                        entered.countDown()
                                        check(release.await(10, TimeUnit.SECONDS))
                                    }
                                    db.preferenceStore.compareAndSetPreferenceReactive(key, expected, value)
                                }.subscribeOn(Schedulers.boundedElastic())
                    }
                val refreshing = ProviderCatalogService(preferences)
                val editing = ProviderCatalogService(db.preferenceStore)
                refreshing.saveProvider(profile("one").copy(models = listOf(ProviderModelConfig("model"), ProviderModelConfig("obsolete"))))
                refreshing.saveProvider(profile("two"))
                armed.set(true)
                Executors.newSingleThreadExecutor().use { executor ->
                    val refresh =
                        executor.submit {
                            when (mode) {
                                0 ->
                                    refreshing.updateDiscoveredModelConfigs(
                                        "one",
                                        listOf(ProviderModelConfig("model", name = "Discovered")),
                                    )
                                1 -> refreshing.updateDiscoveredModels("one", listOf("model"))
                                else -> refreshing.updateModelCapabilities("one", mapOf("model" to setOf("tools")))
                            }
                        }
                    try {
                        assertTrue(entered.await(10, TimeUnit.SECONDS))
                        editing.saveProvider(
                            profile("one").copy(
                                name = "Edited",
                                options = mapOf("temperature" to "0.3"),
                                models = listOf(ProviderModelConfig("model", options = mapOf("topP" to "0.7"))),
                            ),
                        )
                        editing.setActiveSelection("two", "model")
                    } finally {
                        release.countDown()
                    }
                    refresh.get(15, TimeUnit.SECONDS)
                }
                assertEquals("Edited", editing.getProvider("one")?.name)
                assertEquals(mapOf("temperature" to "0.3"), editing.getProvider("one")?.options)
                assertEquals(
                    if (mode == 0) "Discovered" else "model",
                    editing
                        .getProvider("one")
                        ?.models
                        ?.single()
                        ?.name,
                )
                assertEquals(
                    mapOf("topP" to "0.7"),
                    editing
                        .getProvider("one")
                        ?.models
                        ?.single()
                        ?.options,
                )
                if (mode == 2) {
                    assertEquals(
                        setOf("tools"),
                        editing
                            .getProvider("one")
                            ?.models
                            ?.single()
                            ?.capabilities,
                    )
                }
                assertEquals("two", editing.activeProviderId())
                assertEquals("model", editing.activeModelId())
            }
        }
    }

    @Test
    fun `reactive catalog publication follows commit and is absent after rollback`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:provider-notification").use { db ->
            val runtime = DefaultProviderRuntimeConfig()
            val catalog = ProviderCatalogService(db.preferenceStore, runtime)
            val notifications = AtomicInteger()
            catalog
                .addChangeListener {
                    assertEquals("one", catalog.activeProviderId())
                    notifications.incrementAndGet()
                }.use {
                    val configuration = ProviderConfiguration(listOf(profile("one")), "one", "model")
                    assertFailsWith<IllegalStateException> {
                        db.transactionalOperator
                            .execute {
                                catalog
                                    .replaceConfigurationReactive(configuration)
                                    .then(
                                        Mono.fromRunnable<Void> {
                                            assertEquals("ollama", runtime.llmProvider)
                                            assertEquals(0, notifications.get())
                                        },
                                    ).then(Mono.error<Void>(IllegalStateException("rollback")))
                            }.then()
                            .block()
                    }
                    assertEquals("ollama", catalog.activeProviderId())
                    assertEquals(0, notifications.get())
                    db.transactionalOperator
                        .execute {
                            catalog.replaceConfigurationReactive(configuration).then(
                                Mono.fromRunnable<Void> {
                                    assertEquals("ollama", runtime.llmProvider)
                                    assertEquals(0, notifications.get())
                                },
                            )
                        }.then()
                        .block()
                    assertEquals("one", runtime.llmProvider)
                    assertEquals(1, notifications.get())
                }
        }
    }

    @Test
    fun `database compare and set rejects obsolete values and handles absent keys`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:preference-cas").use { db ->
            val preferences = db.preferenceStore
            assertTrue(preferences.compareAndSetPreferenceReactive("key", null, "first").block()!!)
            assertFalse(preferences.compareAndSetPreferenceReactive("key", null, "other").block()!!)
            assertFalse(preferences.compareAndSetPreferenceReactive("key", "obsolete", "other").block()!!)
            assertTrue(preferences.compareAndSetPreferenceReactive("key", "first", "second").block()!!)
            assertEquals("second", preferences.getPreference("key"))
        }
    }

    @Test
    fun `concurrent creation of a missing preference has exactly one winner`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:preference-insert-conflict").use { db ->
            val barrier = CyclicBarrier(2)
            Executors.newFixedThreadPool(2).use { executor ->
                val results =
                    listOf("first", "second")
                        .map { value ->
                            executor.submit<Boolean> {
                                barrier.await(10, TimeUnit.SECONDS)
                                db.preferenceStore.compareAndSetPreferenceReactive("new", null, value).block()!!
                            }
                        }.map { it.get(15, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it })
                assertEquals(if (results.first()) "first" else "second", db.preferenceStore.getPreference("new"))
            }
        }
    }

    private fun profile(id: String) =
        ProviderProfile(
            id = id,
            name = id,
            adapter = ProviderAdapter.OLLAMA,
            baseUrl = "http://localhost:11434",
            defaultModel = "model",
            models = listOf(ProviderModelConfig("model")),
        )

    private companion object {
        const val CATALOG = "llm.provider.catalog.v1"
    }
}
