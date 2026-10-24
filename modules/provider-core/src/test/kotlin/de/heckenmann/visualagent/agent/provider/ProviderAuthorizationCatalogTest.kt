package de.heckenmann.visualagent.agent.provider

import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Covers durable registration, stale settings edits, and authentication lifecycle conflicts. */
class ProviderAuthorizationCatalogTest {
    private val preferences = MemoryPreferences()
    private val persistence = ProviderCatalogPersistence(preferences)
    private val runtime = DefaultProviderRuntimeConfig()
    private val notifications = ProviderCatalogNotifications(persistence, runtime)
    private val authorization = ProviderAuthorizationCatalog(persistence, notifications)
    private val catalog = ProviderCatalogService(preferences, runtime, persistence, notifications)
    private val profile =
        ProviderProfile(
            "direct",
            "Direct",
            ProviderAdapter.CODEX_DIRECT,
            "https://api.openai.com",
            defaultModel = "model",
            models = listOf(ProviderModelConfig("model")),
            directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
        )

    @Test
    fun `invalid staged direct endpoints fail before an authorization target is created`() {
        assertFailsWith<IllegalArgumentException> {
            authorization.prepare(profile.copy(baseUrl = "https://custom.example.test")).block()
        }
        assertNull(catalog.getProvider(profile.id))
    }

    @Test
    fun `conversion rejects a concurrently edited previous endpoint`() {
        val previous = profile.copy(adapter = ProviderAdapter.OLLAMA, baseUrl = "https://first.example.test")
        catalog.saveProvider(previous)
        val target = requireNotNull(authorization.prepare(profile).block())
        catalog.saveProvider(previous.copy(baseUrl = "https://second.example.test"))
        assertFailsWith<IllegalStateException> { authorization.complete(target, "registration").block() }
        assertEquals(ProviderAdapter.OLLAMA, catalog.getProvider(profile.id)?.adapter)
        assertEquals("https://second.example.test", catalog.getProvider(profile.id)?.baseUrl)
        assertNull(catalog.getProvider(profile.id)?.credentialReference)
    }

    @Test
    fun `conversion retains the staged direct endpoint instead of the previous adapter endpoint`() {
        listOf(ProviderAdapter.OLLAMA, ProviderAdapter.OPENAI_COMPATIBLE).forEach { adapter ->
            catalog.saveProvider(profile.copy(adapter = adapter, baseUrl = "https://custom.example.test"))
            val target = requireNotNull(authorization.prepare(profile).block())
            catalog.saveProvider(requireNotNull(catalog.getProvider(profile.id)).copy(name = "Concurrent name"))
            authorization.complete(target, "registration").block()
            val restored = requireNotNull(catalog.getProvider(profile.id))
            assertEquals(ProviderAdapter.CODEX_DIRECT, restored.adapter)
            assertEquals(profile.baseUrl, restored.baseUrl)
            assertEquals("Concurrent name", restored.name)
            assertEquals("registration", restored.credentialReference)
        }
    }

    @Test
    fun `notification read failure after commit does not fail authorization or forget the registration`() {
        val target = requireNotNull(authorization.prepare(profile).block())
        preferences.afterWrite = { preferences.failReads = true }
        authorization.complete(target, "registration").block()
        preferences.failReads = false
        assertEquals("registration", catalog.getProvider(profile.id)?.credentialReference)
    }

    @Test
    fun `notification read failure after sign out does not turn successful persistence into failure`() {
        complete(profile, "registration")
        val registered = requireNotNull(catalog.getProvider(profile.id))
        preferences.afterWrite = { preferences.failReads = true }
        authorization.clear(registered).block()
        preferences.failReads = false
        assertNull(catalog.getProvider(profile.id)?.credentialReference)
    }

    @Test
    fun `registration survives a new catalog instance without saving the settings draft`() {
        val selection = catalog.activeProviderId() to catalog.activeModelId()
        complete(profile, "registration")

        val restored = ProviderCatalogService(preferences)
        assertEquals("registration", restored.getProvider(profile.id)?.credentialReference)
        assertEquals(selection, restored.activeProviderId() to restored.activeModelId())
    }

    @Test
    fun `registration preserves concurrently edited fields and other providers`() {
        catalog.saveProvider(profile)
        val target = requireNotNull(authorization.prepare(profile).block())
        val others = catalog.listProviders().filterNot { it.id == profile.id }
        catalog.saveProvider(profile.copy(name = "Edited", baseUrl = "https://api.openai.com/v1"))
        authorization.complete(target, "registration").block()

        assertEquals("Edited", catalog.getProvider(profile.id)?.name)
        assertEquals("https://api.openai.com/v1", catalog.getProvider(profile.id)?.baseUrl)
        assertEquals(others, catalog.listProviders().filterNot { it.id == profile.id })
    }

    @Test
    fun `stale whole-settings saves cannot forget an account switch or resurrect sign-out`() {
        complete(profile, "first")
        val draft = ProviderConfiguration(catalog.listProviders(), profile.id, "model")
        complete(requireNotNull(catalog.getProvider(profile.id)), "second")
        catalog.replaceConfigurationReactive(draft).block()
        assertEquals("second", catalog.getProvider(profile.id)?.credentialReference)

        authorization.clear(requireNotNull(catalog.getProvider(profile.id))).block()
        catalog.replaceConfiguration(draft)
        assertNull(catalog.getProvider(profile.id)?.credentialReference)
    }

    @Test
    fun `stale single-profile save preserves the server registration`() {
        complete(profile, "registration")
        catalog.saveProvider(profile.copy(name = "Changed"))
        assertEquals("registration", catalog.getProvider(profile.id)?.credentialReference)
        assertEquals("Changed", catalog.getProvider(profile.id)?.name)
    }

    @Test
    fun `deleted provider is not recreated by a delayed authorization`() {
        catalog.saveProvider(profile)
        val target = requireNotNull(authorization.prepare(profile).block())
        catalog.deleteProvider(profile.id)

        assertFailsWith<IllegalStateException> { authorization.complete(target, "late").block() }
        assertNull(catalog.getProvider(profile.id))
    }

    @Test
    fun `concurrent account switch cannot be overwritten by an older authorization attempt`() {
        complete(profile, "first")
        val current = requireNotNull(catalog.getProvider(profile.id))
        val older = requireNotNull(authorization.prepare(current).block())
        complete(current, "second")

        assertFailsWith<IllegalStateException> { authorization.complete(older, "late").block() }
        authorization.clear(current).block()
        assertEquals("second", catalog.getProvider(profile.id)?.credentialReference)
    }

    @Test
    fun `concurrent API key edit prevents a pending login from discarding that credential`() {
        val apiProfile = profile.copy(directAuthMode = DirectProviderAuthMode.API_KEY, apiKey = "synthetic-first")
        catalog.saveProvider(apiProfile)
        val target = requireNotNull(authorization.prepare(profile).block())
        catalog.saveProvider(apiProfile.copy(apiKey = "synthetic-second"))

        assertFailsWith<IllegalStateException> { authorization.complete(target, "late").block() }
        assertEquals("synthetic-second", catalog.getProvider(profile.id)?.apiKey)
        assertEquals("ProviderAuthorizationTarget(redacted)", target.toString())
    }

    @Test
    fun `explicit API-key selection clears the OAuth reference and stale drafts cannot restore it`() {
        complete(profile, "registration")
        val stale = requireNotNull(catalog.getProvider(profile.id))
        catalog.saveProvider(stale.copy(directAuthMode = DirectProviderAuthMode.API_KEY, apiKey = "synthetic"))
        assertNull(catalog.getProvider(profile.id)?.credentialReference)
        catalog.saveProvider(stale)
        assertNull(catalog.getProvider(profile.id)?.credentialReference)
    }

    @Test
    fun `registration mutation retries against fresh database state after a conflict`() {
        catalog.saveProvider(profile)
        val target = requireNotNull(authorization.prepare(profile).block())
        preferences.beforeWrite = {
            catalog.saveProvider(profile.copy(name = "Concurrent edit"))
        }
        authorization.complete(target, "registration").block()
        assertEquals("registration", catalog.getProvider(profile.id)?.credentialReference)
        assertEquals("Concurrent edit", catalog.getProvider(profile.id)?.name)
    }

    private fun complete(
        profile: ProviderProfile,
        reference: String,
    ) {
        authorization.prepare(profile).flatMap { authorization.complete(it, reference) }.block()
    }

    private class MemoryPreferences : ProviderPreferenceStore {
        private val values = mutableMapOf<String, String>()
        var beforeWrite: (() -> Unit)? = null
        var afterWrite: (() -> Unit)? = null
        var failReads = false

        override fun getPreference(key: String): String? {
            check(!failReads) { "Synthetic notification read failure" }
            return values[key]
        }

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
            Mono.fromCallable {
                val action = beforeWrite
                beforeWrite = null
                action?.invoke()
                if (values[key] == expected) {
                    values[key] = value
                    val committed = afterWrite
                    afterWrite = null
                    committed?.invoke()
                    true
                } else {
                    false
                }
            }
    }
}
