package de.heckenmann.visualagent.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.ui.components.ActionTooltip

/** Warnings derived from the active provider model's declared capabilities and limits. */
internal data class ModelCapabilityWarnings(
    val context: ModelCapabilityWarning? = null,
    val tooling: ModelCapabilityWarning? = null,
)

/** One user-facing provider-model warning shown in the workspace header. */
internal data class ModelCapabilityWarning(
    val label: String,
    val description: String,
)

/**
 * Evaluates only declared provider-model metadata and the user's configured context limit.
 *
 * Unknown capabilities remain unknown and therefore do not create a negative capability warning.
 */
internal fun modelCapabilityWarnings(
    configuredContextLength: Int,
    activeModel: ProviderModel?,
): ModelCapabilityWarnings {
    val effectiveContextLimit =
        listOfNotNull(
            configuredContextLength.takeIf { it > 0 },
            activeModel?.contextLimit?.takeIf { it > 0 },
        ).minOrNull()
    val contextWarning =
        effectiveContextLimit
            ?.takeIf { it < MIN_RECOMMENDED_CONTEXT_TOKENS }
            ?.let { limit ->
                ModelCapabilityWarning(
                    label = "Context $limit",
                    description =
                        "The active model can use at most $limit context tokens. " +
                            "Visual Agent recommends at least $MIN_RECOMMENDED_CONTEXT_TOKENS tokens for reliable multi-turn work.",
                )
            }
    val toolingWarning =
        activeModel
            ?.takeIf { model ->
                model.capabilitiesComplete && model.capabilities.none { it.equals("tools", ignoreCase = true) }
            }?.let {
                ModelCapabilityWarning(
                    label = "Tools unavailable",
                    description = "The active model declares no tool-calling capability. Agent actions that require tools are unavailable.",
                )
            }
    return ModelCapabilityWarnings(context = contextWarning, tooling = toolingWarning)
}

/** Displays context and tooling warnings with one shared visibility transition. */
@Composable
internal fun ModelCapabilityWarningBadges(warnings: ModelCapabilityWarnings) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ModelCapabilityWarningBadge(warning = warnings.context)
        ModelCapabilityWarningBadge(warning = warnings.tooling)
    }
}

@Composable
private fun ModelCapabilityWarningBadge(warning: ModelCapabilityWarning?) {
    AnimatedVisibility(
        visible = warning != null,
        enter = fadeIn(tween(BADGE_ANIMATION_MILLIS)) + expandHorizontally(tween(BADGE_ANIMATION_MILLIS)),
        exit = fadeOut(tween(BADGE_ANIMATION_MILLIS)) + shrinkHorizontally(tween(BADGE_ANIMATION_MILLIS)),
    ) {
        warning?.let { current ->
            ActionTooltip(description = current.description) {
                Box(
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .clip(BadgeShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .border(1.dp, MaterialTheme.colorScheme.error, BadgeShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = current.label,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val MIN_RECOMMENDED_CONTEXT_TOKENS = 4096
private const val BADGE_ANIMATION_MILLIS = 180
private val BadgeShape = RoundedCornerShape(8.dp)
