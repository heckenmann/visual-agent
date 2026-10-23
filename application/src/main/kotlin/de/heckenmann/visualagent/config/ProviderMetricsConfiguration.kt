package de.heckenmann.visualagent.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Supplies local provider measurements when no external metrics registry is configured. */
@Configuration(proxyBeanMethods = false)
class ProviderMetricsConfiguration {
    /** Keeps metrics available without enabling a network exporter or overriding a supplied registry. */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MeterRegistry::class)
    fun providerMeterRegistry(): MeterRegistry = SimpleMeterRegistry()
}
