package de.heckenmann.visualagent.server

import org.apache.tika.Tika
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Spring wiring for the server-owned conversation media services. */
@Configuration(proxyBeanMethods = false)
class ConversationMediaConfiguration {
    /** Creates the shared Apache Tika MIME detector used for image payload validation. */
    @Bean
    fun conversationMimeDetector(): Tika = Tika()
}
