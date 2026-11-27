package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CertificateTrustPort
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationAvailability
import de.heckenmann.visualagent.protocol.OnboardingPort
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.ProtocolOperationException
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.TerminalConsentPort
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeModalHost
import de.heckenmann.visualagent.ui.settings.agent.TerminalAccessPage
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
    providers: de.heckenmann.visualagent.protocol.ProviderPort? = null,
    terminalConsent: TerminalConsentPort? = null,
    certificates: CertificateTrustPort? = null,
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
    var agentCreationAvailability by remember { mutableStateOf<OnboardingAgentCreationAvailability?>(null) }
    var confirmSkip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val certificateImport = remember(certificates) { OnboardingCertificateImport(certificates) }
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
    val credentialConfigured = onboardingCredentialConfigured(draft, selected)

    val discoverModels: () -> Unit = {
        discoverOnboardingModels(
            scope = scope,
            onboarding = onboarding,
            draft = draft,
            onLoading = { loading ->
                loadingModels = loading
                if (loading) validationError = null
            },
            onDiscovered = { discovered ->
                certificateImport.clearReview()
                models = discovered
                selectedModel = discovered.firstOrNull { it.id == selectedModel?.id } ?: discovered.firstOrNull()
                if (discovered.isEmpty()) {
                    validationError = "No selectable model was found for this provider. Check the provider and refresh later."
                }
            },
            onFailure = { error ->
                val protocolError = error as? ProtocolOperationException
                validationError = protocolError?.error?.detail
                    ?: "Models could not be discovered from this Visual Agent server."
                certificateImport.clearReview()
                if (protocolError?.certificateTrustRequired == true) certificateImport.offerReview(draft?.baseUrl)
            },
        )
    }
    val importRevision = certificateImport.importRevision
    LaunchedEffect(certificateImport, importRevision) {
        if (importRevision > 0 && step == 2) discoverModels()
    }
    val createAgent: () -> Unit = {
        createOnboardingAgent(
            scope = scope,
            onboarding = onboarding,
            description = agentDescription,
            onCreating = { creatingAgent = it },
            onCreated = { result ->
                agents = result.agents
                agentResult = result.message
                agentError =
                    if (result.agentCreated) {
                        null
                    } else {
                        "The model replied without creating a sub-agent. Revise the request or skip this step."
                    }
            },
            onFailure = {
                agentError = "The model could not create the sub-agent. Adjust the description or skip this step."
            },
        )
    }
    val saveProvider: () -> Unit = {
        saveOnboardingProvider(
            scope = scope,
            onboarding = onboarding,
            draft = draft,
            model = selectedModel,
            onSaving = { savingProvider = it },
            onValidationFailed = {
                validationError = it.message
                step = 2
                certificateImport.clearReview()
                if (it.certificateTrustRequired) certificateImport.offerReview(draft?.baseUrl)
            },
            onSaved = { currentAgents, availability ->
                agents = currentAgents
                agentCreationAvailability = availability
                step = 4
            },
            onFailure = {
                validationError = "The setup could not be saved. Your previous provider configuration remains active."
            },
        )
    }
    val completeOnboarding: () -> Unit = {
        scope.launch {
            withContext(Dispatchers.IO) { onboarding.complete() }
            onFinished()
        }
    }
    val busy = loadingModels || savingProvider || creatingAgent
    val cancel = { if (automatic) confirmSkip = true else onFinished() }
    val dismissOnEscape = onboardingDismissOnEscape(!automatic && certificateImport.modal == null, busy, cancel)
    Box(modifier = Modifier.fillMaxSize().then(dismissOnEscape)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Set up Visual Agent", style = MaterialTheme.typography.headlineMedium)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (step in 1..3) OnboardingCertificateControl(certificateImport, busy, draft?.baseUrl)
                    when (step) {
                        0 -> OnboardingWelcome()
                        1 ->
                            OnboardingProviderStep(
                                profiles = profiles,
                                selected = selected,
                                customDraft = customDraft,
                                loading = loadingProfiles,
                                authorizationControls = { providerDraft ->
                                    OnboardingDirectAuthorization(providerDraft, providers) { changed ->
                                        selected = null
                                        customDraft = changed
                                        models = emptyList()
                                        selectedModel = null
                                        validationError = null
                                    }
                                },
                                onSelect = {
                                    selected = it
                                    customDraft = null
                                    models = emptyList()
                                    selectedModel = null
                                    validationError = null
                                },
                                onCancelDraft = { customDraft = null },
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
                        4 ->
                            OnboardingAgentStep(
                                agents,
                                agentDescription,
                                creatingAgent,
                                agentResult,
                                agentError,
                                availability = agentCreationAvailability,
                                onDescriptionChange = { agentDescription = it },
                                onCreate = createAgent,
                                onSelectModel = {
                                    agentError = null
                                    agentResult = null
                                    step = 2
                                },
                            )
                        else -> TerminalAccessPage(terminalConsent)
                    }
                }
            }
            OnboardingNavigation(
                step = step,
                automatic = automatic,
                busy = busy,
                canContinue = step != 1 || draft != null,
                canSelectModel = step != 2 || selectedModel != null,
                onBack = { step -= 1 },
                onCancel = cancel,
                onContinue = {
                    when (step) {
                        1 -> {
                            step += 1
                            discoverModels()
                        }
                        3 -> saveProvider()
                        TERMINAL_ACCESS_STEP -> completeOnboarding()
                        else -> step += 1
                    }
                },
                onSkipAgents = { step = TERMINAL_ACCESS_STEP },
                onSkipTerminal = completeOnboarding,
            )
            if (confirmSkip) {
                ComposeModalHost(
                    modal =
                        ComposeConfirmationModal(
                            title = if (step >= 4) "Finish setup?" else "Skip provider setup?",
                            message =
                                if (step >= 4) {
                                    "Your provider and model are saved. You can create sub-agents later from the conversation."
                                } else {
                                    "Setup will not open automatically again. You can run it later from Providers and models."
                                },
                            confirmDescription = if (step >= 4) "Finish setup" else "Skip setup",
                            onConfirm = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (step >= 4) onboarding.complete() else onboarding.dismiss()
                                    }
                                    onFinished()
                                }
                            },
                        ),
                    onDismiss = { confirmSkip = false },
                )
            }
        }
        ComposeModalHost(certificateImport.modal, onDismiss = certificateImport::dismiss)
    }
}
