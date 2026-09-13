package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationCompletionEventBus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Provides the server-owned completion event source used by conversation suggestions. */
@Configuration
class ConversationSuggestionConfiguration {
    /** Creates the process-local event bus shared by completion publishers and the UI port. */
    @Bean
    fun conversationCompletionEventBus(): ConversationCompletionEventBus = ConversationCompletionEventBus()
}
