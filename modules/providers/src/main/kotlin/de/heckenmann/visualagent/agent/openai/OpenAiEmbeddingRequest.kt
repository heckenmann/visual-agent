package de.heckenmann.visualagent.agent.openai

import com.openai.client.OpenAIClient
import com.openai.models.embeddings.EmbeddingCreateParams
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Embeds text with the profile's SDK client and releases it when the request finishes. */
internal fun requestOpenAiEmbedding(
    text: String,
    modelId: String,
    clientFactory: () -> OpenAIClient,
): Mono<List<Double>> =
    Mono
        .fromCallable {
            val request =
                EmbeddingCreateParams
                    .builder()
                    .model(modelId)
                    .input(text)
                    .encodingFormat(EmbeddingCreateParams.EncodingFormat.FLOAT)
                    .build()
            val client = clientFactory()
            try {
                client
                    .embeddings()
                    .create(request)
                    .data()
                    .single()
                    .embedding()
                    .map(Float::toDouble)
            } finally {
                client.close()
            }
        }.subscribeOn(Schedulers.boundedElastic())
