package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CredentialUpdate
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingPort
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeModalHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Renders the server-owned first-run LLM provider/model setup before the workspace opens. */
@Composable
fun ComposeOnboardingWizard(
    onboarding: OnboardingPort,
    automatic: Boolean,
    onFinished: () -> Unit,
    skipRequested: Boolean = false,
    onSkipRequestHandled: () -> Unit = {},
) {
    var profiles by remember { mutableStateOf<List<OnboardingProviderProfile>>(emptyList()) }
    var loadingProfiles by remember { mutableStateOf(true) }
    var step by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<OnboardingProviderProfile?>(null) }
    var customDraft by remember { mutableStateOf<OnboardingProviderDraft?>(null) }
    var models by remember { mutableStateOf<List<ProviderModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<ProviderModel?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var savingProvider by remember { mutableStateOf(false) }
    var agents by remember { mutableStateOf<List<OnboardingAgent>>(emptyList()) }
    var agentDescription by remember { mutableStateOf(DEFAULT_RESEARCHER_REQUEST) }
    var creatingAgent by remember { mutableStateOf(false) }
    var agentResult by remember { mutableStateOf<String?>(null) }
    var agentError by remember { mutableStateOf<String?>(null) }
    var confirmSkip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(onboarding) {
        profiles = withContext(Dispatchers.IO) { onboarding.providers() }
        selected = profiles.firstOrNull { it.enabled } ?: profiles.firstOrNull()
        loadingProfiles = false
    }
    LaunchedEffect(skipRequested) {
        if (skipRequested) {
            confirmSkip = true
            onSkipRequestHandled()
        }
    }
    val draft = customDraft ?: selected?.toDraft()
    val credentialConfigured =
        when (val credential = draft?.credential) {
            CredentialUpdate.Unchanged -> selected?.credentialConfigured == true
            CredentialUpdate.Clear -> false
            is CredentialUpdate.Replace -> credential.value.isNotBlank()
            null -> false
        }

    val discoverModels: () -> Unit = {
        draft?.let { providerDraft ->
            scope.launch {
                loadingModels = true
                validationError = null
                runCatching {
                    withContext(Dispatchers.IO) { onboarding.discoverModels(providerDraft) }
                }.onSuccess { discovered ->
                    models = discovered
                    selectedModel = discovered.firstOrNull()
                    if (discovered.isEmpty()) {
                        validationError =
                            "No selectable model was found for this provider. Check the provider and refresh later."
                    }
                }.onFailure {
                    validationError = "Models could not be discovered from this Visual Agent server."
                }
                loadingModels = false
            }
        }
    }
    val createAgent: () -> Unit = {
        scope.launch {
            creatingAgent = true
            agentError = null
            runCatching { withContext(Dispatchers.IO) { onboarding.createAgent(agentDescription) } }
                .onSuccess { result ->
                    agents = result.agents
                    agentResult = result.message
                }.onFailure {
                    agentError = "The model could not create the sub-agent. Adjust the description or skip this step."
                }
            creatingAgent = false
        }
    }
    val saveProvider: () -> Unit = {
        scope.launch {
            savingProvider = true
            val result = withContext(Dispatchers.IO) { onboarding.validate(checkNotNull(draft), checkNotNull(selectedModel).id) }
            if (!result.success) {
                validationError = result.message
                step = 2
            } else {
                runCatching {
                    withContext(Dispatchers.IO) {
                        onboarding.finish(checkNotNull(draft), checkNotNull(selectedModel), checkNotNull(result.validationFingerprint))
                        onboarding.agents()
                    }
                }.onSuccess { currentAgents ->
                    agents = currentAgents
                    step = 4
                }.onFailure {
                    validationError = "The setup could not be saved. Your previous provider configuration remains active."
                }
            }
            savingProvider = false
        }
    }
    val completeOnboarding: () -> Unit = {
        scope.launch {
            withContext(Dispatchers.IO) { onboarding.complete() }
            onFinished()
        }
    }
    val stepScrollState = rememberScrollState()
    LaunchedEffect(step) { stepScrollState.scrollTo(0) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Set up Visual Agent", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Learn the workflow, choose this server's model, and optionally create your first specialized helper.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(stepScrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (step) {
                        0 -> OnboardingWelcome()
                        1 ->
                            OnboardingProviderStep(
                                profiles = profiles,
                                selected = selected,
                                customDraft = customDraft,
                                loading = loadingProfiles,
                                onSelect = {
                                    selected = it
                                    customDraft = null
                                    models = emptyList()
                                    selectedModel = null
                                    validationError = null
                                },
                                onDraftChange = {
                                    selected = null
                                    customDraft = it
                                    models = emptyList()
                                    selectedModel = null
                                    validationError = null
                                },
                            )
                        2 ->
                            OnboardingModelStep(
                                draft = draft,
                                models = models,
                                selectedModel = selectedModel,
                                loading = loadingModels,
                                error = validationError,
                                onSelect = { model -> selectedModel = model },
                                onRefresh = discoverModels,
                            )
                        3 -> OnboardingReviewStep(draft, credentialConfigured, selectedModel, validationError)
                        else ->
                            OnboardingAgentStep(
                                agents,
                                agentDescription,
                                creatingAgent,
                                agentResult,
                                agentError,
                                onDescriptionChange = { agentDescription = it },
                                onCreate = createAgent,
                            )
                    }
                }
                if (stepScrollState.maxValue > 0) {
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(stepScrollState),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
            OnboardingNavigation(
                step = step,
                automatic = automatic,
                busy = loadingModels || savingProvider || creatingAgent,
                canContinue = step != 1 || draft != null,
                canSelectModel = step != 2 || selectedModel != null,
                onBack = { step -= 1 },
                onCancel = { if (automatic) confirmSkip = true else onFinished() },
                onContinue = {
                    when (step) {
                        1 -> {
                            step += 1
                            discoverModels()
                        }
                        3 -> saveProvider()
                        4 -> completeOnboarding()
                        else -> step += 1
                    }
                },
                onSkipAgents = completeOnboarding,
            )
            if (confirmSkip) {
                ComposeModalHost(
                    modal =
                        ComposeConfirmationModal(
                            title = if (step == 4) "Skip agent setup?" else "Skip provider setup?",
                            message =
                                if (step == 4) {
                                    "Your provider and model are saved. You can create sub-agents later from the conversation."
                                } else {
                                    "Setup will not open automatically again. You can run it later from Providers and models."
                                },
                            confirmDescription = if (step == 4) "Skip agent setup" else "Skip setup",
                            onConfirm = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (step == 4) onboarding.complete() else onboarding.dismiss()
                                    }
                                    onFinished()
                                }
                            },
                        ),
                    onDismiss = { confirmSkip = false },
                )
            }
        }
    }
}
