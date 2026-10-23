package de.heckenmann.visualagent.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProviderMetricsConfigurationTest {
    @Test
    fun `server supplies an in memory registry and closes it at shutdown`() {
        val context = AnnotationConfigApplicationContext(ProviderMetricsConfiguration::class.java)
        val registry = context.getBean(MeterRegistry::class.java)
        assertTrue(registry is SimpleMeterRegistry)
        context.close()
        assertTrue(registry.isClosed)
    }

    @Test
    fun `existing registry is preserved instead of duplicated`() {
        val supplied = SimpleMeterRegistry()
        val context = AnnotationConfigApplicationContext()
        try {
            context.registerBean("suppliedRegistry", MeterRegistry::class.java, java.util.function.Supplier { supplied })
            context.register(ProviderMetricsConfiguration::class.java)
            context.refresh()
            assertEquals(1, context.getBeansOfType(MeterRegistry::class.java).size)
            assertSame(supplied, context.getBean(MeterRegistry::class.java))
        } finally {
            context.close()
            supplied.close()
        }
    }
}
