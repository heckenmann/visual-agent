package de.heckenmann.visualagent.agent.codexdirect

import com.openai.errors.OpenAIInvalidDataException
import com.openai.errors.OpenAIIoException
import com.openai.errors.OpenAIServiceException
import com.openai.errors.SseException
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingError
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import org.springframework.ai.openai.responses.OpenAiResponsesException
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

/** Classifies typed failures without retaining raw response bodies or credential-bearing causes. */
internal object DirectProviderErrors {
    /** Returns a safe classified failure while preserving explicit request cancellation. */
    fun safe(error: Throwable): Throwable {
        if (error is CancellationException || error is ProviderUserFacingException) return error
        val chain = generateSequence(error) { it.cause }.take(16).toList()
        val service = chain.filterIsInstance<OpenAIServiceException>().firstOrNull()
        val status =
            service?.statusCode() ?: chain
                .filterIsInstance<WebClientResponseException>()
                .firstOrNull()
                ?.statusCode
                ?.value()
        val oauth =
            chain
                .filterIsInstance<OAuth2AuthorizationException>()
                .firstOrNull()
                ?.error
                ?.errorCode
        val responseCode = chain.filterIsInstance<OpenAiResponsesException>().firstOrNull()?.code
        val description =
            when {
                responseCode == "response_size_limit" ->
                    problem("Provider response too large", "Request a smaller response or split the task into smaller parts.")
                responseCode == "subscription_sharing_usage_limit_exceeded" ->
                    problem("ChatGPT plan usage limit", "Wait for plan usage to become available or review your ChatGPT plan.")
                responseCode == "subscription_sharing_usage_unavailable" ->
                    problem("ChatGPT plan usage unavailable", "Authorize eligible ChatGPT plan usage before retrying.")
                responseCode in setOf("missing_response_completed", "response_incomplete") ->
                    problem("Incomplete provider response", "The provider did not finish this response. Retry the request.", true)
                responseCode != null ->
                    problem(
                        "Provider stream failed",
                        "The provider reported a failed response. Review the selected account.",
                    )
                oauth == "invalid_grant" -> problem("Session expired", "Sign in with ChatGPT again to renew this profile's session.")
                oauth == "access_denied" ->
                    problem(
                        "Authorization denied",
                        "Authorize the required ChatGPT plan access before trying again.",
                    )
                oauth != null || chain.any { it is JwtException } ->
                    problem("Authorization failed", "ChatGPT authorization could not be verified. Sign in again.")
                status == 401 ->
                    problem(
                        "Authentication failed",
                        "Renew the selected profile's credentials. No other billing mode was used.",
                    )
                status == 403 -> problem("Access not authorized", "The selected account or API project does not authorize this request.")
                status == 404 -> problem("Model not available", "Refresh the model list and select a model available to this profile.")
                status == 429 && service?.code()?.orElse(null) == "insufficient_quota" ->
                    problem("Provider quota exhausted", "Check the selected account or API project's quota and billing.")
                status == 429 -> problem("Provider rate limit", "The provider temporarily limited requests. Retry later.", true)
                status == 400 || status == 422 ->
                    problem(
                        "Request not supported",
                        "Check the selected model and its supported request options.",
                    )
                status != null && status >= 500 ->
                    problem(
                        "Provider unavailable",
                        "The provider could not complete this request. Retry later.",
                        true,
                    )
                chain.any { it is TimeoutException || it is java.net.SocketTimeoutException } ->
                    problem("Provider timeout", "The provider did not complete the request in time. Retry later.", true)
                chain.any { it is SseException || it is OpenAIInvalidDataException } ->
                    problem("Invalid provider stream", "The provider stream was invalid or ended with an error. Retry the request.", true)
                chain.any { it is OpenAIIoException || it is WebClientRequestException || it is IOException } ->
                    problem("Provider connection failed", "Check the network connection and trusted certificates.", true)
                chain.any { it is IllegalArgumentException } ->
                    problem("Provider configuration invalid", "Check the selected model, authentication mode, and supported options.")
                else -> problem("Direct provider request failed", "Check the selected profile and try again.", true)
            }
        return ProviderUserFacingException(description)
    }

    private fun problem(
        summary: String,
        detail: String,
        retryable: Boolean = false,
    ) = ProviderUserFacingError(summary, detail, retryable)
}
