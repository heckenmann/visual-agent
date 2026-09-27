package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory

/** Reads bounded garbage-collector and memory-pool snapshots through standard JVM management APIs. */
@Component
class JvmGcDiagnosticsProbe : GcDiagnosticsProbe {
    override fun snapshot(): GcDiagnosticsSnapshot {
        val collectors = ManagementFactory.getGarbageCollectorMXBeans()
        val pools = ManagementFactory.getMemoryPoolMXBeans()
        return GcDiagnosticsSnapshot(
            collectors =
                collectors
                    .take(MAX_COLLECTORS + 1)
                    .take(MAX_COLLECTORS)
                    .map { collector ->
                        GarbageCollectorMetric(
                            name = collector.name.take(MAX_NAME_LENGTH),
                            collectionCount = collector.collectionCount.takeWhenAvailable(),
                            collectionTimeMillis = collector.collectionTime.takeWhenAvailable(),
                        )
                    },
            memoryPools =
                pools
                    .take(MAX_MEMORY_POOLS + 1)
                    .take(MAX_MEMORY_POOLS)
                    .map { pool ->
                        val usage = runCatching { pool.usage }.getOrNull()
                        MemoryPoolMetric(
                            name = pool.name.take(MAX_NAME_LENGTH),
                            type = pool.type.name,
                            usedBytes = usage?.used?.takeIf { it >= 0 },
                            committedBytes = usage?.committed?.takeIf { it >= 0 },
                            maxBytes = usage?.max?.takeIf { it >= 0 },
                        )
                    },
            truncated = collectors.size > MAX_COLLECTORS || pools.size > MAX_MEMORY_POOLS,
        )
    }

    private fun Long.takeWhenAvailable(): Long? = takeIf { it >= 0 }

    private companion object {
        const val MAX_COLLECTORS = 16
        const val MAX_MEMORY_POOLS = 32
        const val MAX_NAME_LENGTH = 120
    }
}
