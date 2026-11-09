package com.svce.issuegram

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.svce.issuegram.ui.IssuegramViewModel
import com.svce.issuegram.ui.camera.PixelCameraScreen
import com.svce.issuegram.ui.components.IssuegramBottomBar
import com.svce.issuegram.ui.components.IssuegramTopAppBar
import com.svce.issuegram.ui.home.HomeFeedScreen
import com.svce.issuegram.ui.map.CivicMapScreen
import com.svce.issuegram.ui.messages.MessagesScreen
import com.svce.issuegram.ui.navigation.Destination
import com.svce.issuegram.ui.onboarding.WelcomeOnboardingScreen
import com.svce.issuegram.ui.profile.ProfileScreen
import com.svce.issuegram.ui.reports.ReportActions
import com.svce.issuegram.ui.reports.ReportDetailScreen
import com.svce.issuegram.data.model.CivicIssueDto
import kotlinx.coroutines.launch

@Composable
fun IssuegramApp(viewModel: IssuegramViewModel = viewModel()) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val ownIssues by viewModel.ownIssues.collectAsStateWithLifecycle()
    val selectedIssue by viewModel.selectedIssue.collectAsStateWithLifecycle()
    val openingIssue by viewModel.isOpeningIssue.collectAsStateWithLifecycle()
    val mutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val profileBusy by viewModel.isProfileBusy.collectAsStateWithLifecycle()
    val sendingMessage by viewModel.sendingMessage.collectAsStateWithLifecycle()
    val screenStates = rememberSaveableStateHolder()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val staffChannels by viewModel.staffChannels.collectAsStateWithLifecycle()
    val messagesByChannel by viewModel.messagesByChannel.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isPostingIssue by viewModel.isPostingIssue.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(Destination.Home) }
    var openedIssueId by rememberSaveable { mutableStateOf<Long?>(null) }
    var contactChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var contactText by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmingDiscard by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.setForeground(true)
            if (event == Lifecycle.Event.ON_STOP) viewModel.setForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.setForeground(true)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.setForeground(false) }
    }
    LaunchedEffect(viewModel) { viewModel.notices.collect { snackbar.showSnackbar(it) } }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (currentUser == null) {
        WelcomeOnboardingScreen(isLoading = isAuthLoading, errorMessage = error,
            onGenerateUsername = viewModel::generateRandomUsername,
            onContinue = { name, handle, uri -> viewModel.completeOnboarding(name, handle, uri) })
        return
    }
    fun navigate(destination: Destination) {
        openedIssueId = null
        contactChannel = null; contactText = ""
        selected = destination
    }
    val openIssue: (CivicIssueDto) -> Unit = { openedIssueId = it.id }
    val canResolveIssue: (CivicIssueDto) -> Boolean = { issue -> channels.any { it.category == issue.category && it.id in staffChannels } }
    val displayIssue: (CivicIssueDto) -> CivicIssueDto = { issue ->
        if (issue.authorId == currentUser?.id) issue.copy(authorAvatarUrl = currentUser?.avatarUrl) else issue
    }
    LaunchedEffect(openedIssueId) { viewModel.openIssue(openedIssueId) }
    BackHandler(openedIssueId != null || selected != Destination.Home) {
        if (openedIssueId != null) openedIssueId = null else navigate(Destination.Home)
    }
    val reportActions: @Composable (CivicIssueDto) -> Unit = { issue ->
        ReportActions(issue, currentUser?.id.orEmpty(), mutating,
            onOpen = { openIssue(issue) },
            onContact = {
                val channel = channels.firstOrNull { it.category == issue.category }
                if (channel == null) scope.launch { snackbar.showSnackbar("No department is available for this report category yet.") }
                else {
                    openedIssueId = null; selected = Destination.Messages; contactChannel = channel.id
                    contactText = "Regarding ${issue.ticketCode.ifBlank { "report #${issue.id}" }}: ${issue.title}"
                }
            },
            onEdit = viewModel::editIssue, onDelete = viewModel::deleteIssue, onFlag = viewModel::flagIssue,
            onDeleted = { if (openedIssueId == issue.id) openedIssueId = null },
            errorMessage = error, onClearError = viewModel::dismissError,
        )
    }
    if (confirmingDiscard) AlertDialog(
        onDismissRequest = { confirmingDiscard = false },
        title = { Text("Remove failed uploads?") },
        text = { Text("Remove the failed reports, messages and photos saved in this device's upload queue? This cannot be undone.") },
        confirmButton = { TextButton(onClick = { viewModel.discardFailedUploads(); confirmingDiscard = false }) { Text("Remove failed uploads") } },
        dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Cancel") } },
    )
    Scaffold(
        topBar = {
            if (selected != Destination.Create && selected != Destination.Messages) IssuegramTopAppBar(
                onMessagesClick = { navigate(Destination.Messages) }, onProfileClick = { navigate(Destination.Profile) })
        },
        bottomBar = { IssuegramBottomBar(selected = selected, onSelect = ::navigate) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (error != null) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { openedIssueId?.let(viewModel::openIssue) ?: viewModel.refreshAll() }, enabled = !isLoading) { Text("Retry") }
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                }
            }
            if (pending.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val failed = pending.count { it.failed }
                    Text(if (failed > 0) "$failed saved upload(s) need attention" else "${pending.size} saved upload(s) waiting to send",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::retryUploads) { Text("Retry") }
                    if (failed > 0) TextButton(onClick = { confirmingDiscard = true }) { Text("Remove") }
                }
                pending.firstOrNull { it.failed }?.error?.let { Text(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (openedIssueId == null && selected != Destination.Create) {
                TextButton(onClick = viewModel::refreshAll, enabled = !isLoading, modifier = Modifier.align(Alignment.End)) { Text("Refresh") }
            }
            val screenModifier = Modifier.weight(1f).fillMaxWidth()
            if (openedIssueId != null) ReportDetailScreen(
                issue = selectedIssue?.takeIf { it.id == openedIssueId }?.let(displayIssue), loading = openingIssue,
                canResolve = selectedIssue?.let(canResolveIssue) == true,
                onToggleResolve = { selectedIssue?.let(viewModel::toggleIssueResolution) },
                onBack = { openedIssueId = null }, onRefresh = { viewModel.openIssue(openedIssueId) },
                actions = reportActions, modifier = screenModifier,
            ) else screenStates.SaveableStateProvider(selected.name) {
                when (selected) {
                Destination.Home -> HomeFeedScreen(issues = issues.map(displayIssue), onToggleResolve = viewModel::toggleIssueResolution,
                    canResolve = canResolveIssue, actions = reportActions, onOpenIssue = openIssue, onCreate = { navigate(Destination.Create) },
                    hasMore = hasMore && !isLoading, onLoadMore = viewModel::loadMore, modifier = screenModifier)
                Destination.Create -> PixelCameraScreen(isPosting = isPostingIssue,
                    onPostIssue = { caption, category, location, detail, lat, lon, file, uri ->
                        viewModel.postNewIssue(caption, category, location, detail, lat, lon, file, uri) {
                            screenStates.removeState(Destination.Create.name); selected = Destination.Home
                        }
                    }, modifier = screenModifier)
                Destination.Map -> CivicMapScreen(issues = issues, modifier = screenModifier, onOpenIssue = openIssue)
                Destination.Profile -> ProfileScreen(currentUser = currentUser, allIssues = ownIssues,
                    onUpdateAvatar = viewModel::updateAvatar, onUpdateName = viewModel::updateName, onRemoveAvatar = viewModel::removeAvatar,
                    profileBusy = profileBusy, errorMessage = error, onOpenIssue = openIssue, onCreate = { navigate(Destination.Create) }, modifier = screenModifier)
                Destination.Messages -> MessagesScreen(channels = channels, messagesByChannel = messagesByChannel,
                    currentUserId = currentUser?.id.orEmpty(), onOpenChannel = viewModel::openChannel,
                    onSendMessage = viewModel::sendChannelMessage, onRetryMessages = viewModel::retryUploads,
                    initialChannelId = contactChannel, initialMessage = contactText,
                    sending = sendingMessage, onContextConsumed = { contactText = "" },
                    modifier = screenModifier, onBackToHome = { selected = Destination.Home })
                }
            }
        }
    }
}
