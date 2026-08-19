package com.vrchatlegends.osccompanion.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vrchatlegends.osccompanion.scripts.InstalledCompanionScript
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenMaxWidth
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.vrcl.CommunityRepository
import com.vrchatlegends.osccompanion.vrcl.CommunitySection
import com.vrchatlegends.osccompanion.vrcl.CommunityState
import com.vrchatlegends.osccompanion.vrcl.FeedMode
import com.vrchatlegends.osccompanion.vrcl.ScriptSort
import com.vrchatlegends.osccompanion.vrcl.VrclPost
import com.vrchatlegends.osccompanion.vrcl.VrclScript
import java.time.Duration
import java.time.Instant

private const val SCRIPTS_URL = "https://vrchatlegends.com/scripts"

@Composable
fun CommunityScreen(viewModel: AppViewModel) {
    val state by viewModel.community.state.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.state.collectAsStateWithLifecycle()
    val repository = viewModel.community
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { repository.loadOnce() }
    LaunchedEffect(state.section) {
        if (state.section == CommunitySection.LEADERBOARD) viewModel.leaderboard.loadOnce()
    }
    LaunchedEffect(state.section, state.feedMode) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        CommunityTopBar(
            state = state,
            onSection = repository::selectSection,
            onRefresh = {
                when (state.section) {
                    CommunitySection.SCRIPTS -> repository.refreshScripts()
                    CommunitySection.LEADERBOARD -> viewModel.leaderboard.refresh()
                    CommunitySection.SOCIAL -> Unit
                }
            },
        )

        AnimatedVisibility(
            visible = state.error != null || state.notice != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                FeedBanner(state, repository::clearMessages)
            }
        }

        when (state.section) {
            CommunitySection.SOCIAL -> PostMediaPlaybackScope {
                SocialWorkspace(
                    state = state,
                    repository = repository,
                    draft = draft,
                    onDraftChange = { draft = it },
                    listState = listState,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = ScreenMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.section) {
                    CommunitySection.SCRIPTS -> scriptItems(state, repository)
                    CommunitySection.LEADERBOARD -> leaderboardItems(leaderboard, viewModel.leaderboard)
                    CommunitySection.SOCIAL -> Unit
                }
            }
        }

    }

    if (state.thread.post != null) {
        CommentDialog(state, repository)
    }
}

@Composable
private fun SocialWorkspace(
    state: CommunityState,
    repository: CommunityRepository,
    draft: String,
    onDraftChange: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 960.dp
        Row(
            modifier = Modifier
                .widthIn(max = 1010.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                modifier = (if (wide) Modifier.width(680.dp) else Modifier.weight(1f))
                    .fillMaxHeight(),
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item(key = "feed-modes") {
                        FeedModeTabs(state, repository::selectFeedMode)
                    }
                    item(key = "composer") {
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Composer(state, repository, draft, onDraftChange) {
                            repository.post(draft) { onDraftChange("") }
                        }
                    }
                    item(key = "pending") {
                        AnimatedVisibility(
                            visible = state.pending.isNotEmpty(),
                            enter = fadeIn() + slideInVertically { -it / 2 },
                            exit = fadeOut() + slideOutVertically { -it / 2 },
                        ) {
                            TextButton(
                                onClick = repository::showPending,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                                Text(" ${state.pending.size} new post${if (state.pending.size == 1) "" else "s"}")
                            }
                        }
                    }

                    if (state.posts.isEmpty() && state.loaded) {
                        item(key = "empty") { FeedEmpty(state.error != null) }
                    }

                    itemsIndexed(state.posts, key = { _, post -> "post-${post.key}" }) { _, post ->
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        )
                        PostRow(post, state, repository)
                    }
                }
            }

            if (wide) {
                Column(
                    modifier = Modifier
                        .width(270.dp)
                        .fillMaxHeight()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SocialIdentityCard(state)
                    SocialFeedStatus(state)
                }
            }
        }
    }
}

private fun LazyListScope.scriptItems(state: CommunityState, repository: CommunityRepository) {
    item(key = "script-safety") {
        ScriptSafetyCard(state)
    }

    if (state.scriptRunner.running ||
        state.scriptRunner.message != null ||
        state.scriptRunner.error != null
    ) {
        item(key = "script-runner") {
            ScriptRunnerCard(
                state = state,
                onStop = repository::stopScript,
                onDismiss = repository::clearMessages,
            )
        }
    }

    item(key = "installed-heading") {
        ScriptSectionHeading(
            title = "Installed",
            detail = "${state.installedScripts.size} in private storage",
            icon = Icons.Filled.Folder,
        )
    }

    if (!state.scriptLibraryLoaded) {
        item(key = "installed-loading") {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = SignalCyan)
        }
    } else if (state.installedScripts.isEmpty()) {
        item(key = "installed-empty") {
            ScriptLibraryEmpty()
        }
    }

    items(state.installedScripts, key = { "installed-${it.sourceId}" }) { script ->
        InstalledScriptRow(
            script = script,
            running = state.scriptRunner.runningScriptId == script.sourceId,
            anotherRunning = state.scriptRunner.running,
            busy = script.sourceId in state.scriptBusy,
            onRun = { repository.runScript(script) },
            onStop = repository::stopScript,
            onRemove = { repository.removeScript(script) },
        )
    }

    item(key = "community-heading") {
        ScriptSectionHeading(
            title = "Community library",
            detail = "Review every effect before installing",
            icon = Icons.Filled.Code,
        )
    }

    item(key = "script-toolbar") {
        ScriptToolbar(
            state = state,
            onSort = repository::setScriptSort,
            onQuery = repository::setScriptQuery,
            onSearch = repository::refreshScripts,
        )
    }

    if (state.scriptsLoading && state.scripts.isEmpty()) {
        item(key = "scripts-loading") {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = SignalCyan)
        }
    }

    if (state.scripts.isEmpty() && state.scriptsLoaded && !state.scriptsLoading) {
        item(key = "scripts-empty") { ScriptsEmpty(state.scriptQuery.isNotBlank()) }
    }

    items(state.scripts, key = { "script-${it.id}" }) { script ->
        ScriptRow(
            script = script,
            installed = state.installedScripts.any { it.sourceId == script.id },
            busy = script.id in state.scriptBusy,
            onLike = { repository.likeScript(script) },
            onInstall = { repository.installScript(script) },
        )
    }
}

@Composable
private fun CommunityTopBar(
    state: CommunityState,
    onSection: (CommunitySection) -> Unit,
    onRefresh: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 700.dp
            Column {
                if (compact) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Community",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        CommunityTopActions(
                            state = state,
                            onRefresh = onRefresh,
                            onWebsite = { uriHandler.openUri(SCRIPTS_URL) },
                        )
                    }
                    CommunitySectionTabs(
                        selected = state.section,
                        expanded = true,
                        onSection = onSection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Community",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(24.dp))
                        CommunitySectionTabs(
                            selected = state.section,
                            expanded = false,
                            onSection = onSection,
                            modifier = Modifier.height(64.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        CommunityTopActions(
                            state = state,
                            onRefresh = onRefresh,
                            onWebsite = { uriHandler.openUri(SCRIPTS_URL) },
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                )
                if (state.scriptsLoading && state.section == CommunitySection.SCRIPTS) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CommunitySectionTabs(
    selected: CommunitySection,
    expanded: Boolean,
    onSection: (CommunitySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CommunitySection.entries.filterNot { it == CommunitySection.SOCIAL }.forEach { section ->
            val active = section == selected
            val tabModifier = if (expanded) Modifier.weight(1f) else Modifier.padding(horizontal = 14.dp)
            Column(
                modifier = tabModifier
                    .fillMaxHeight()
                    .clickable { onSection(section) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(if (active) 36.dp else 0.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun CommunityTopActions(
    state: CommunityState,
    onRefresh: () -> Unit,
    onWebsite: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRefresh, enabled = !state.scriptsLoading) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh community")
        }
        IconButton(onClick = onWebsite) {
            Icon(Icons.Filled.Language, contentDescription = "Open on the website")
        }
    }
}

@Composable
private fun SocialIdentityCard(state: CommunityState) {
    val identity = state.identity
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (identity == null) {
                Text("Browsing as guest", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sign in from Account to follow people, comment, and publish.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(identity.avatarUrl, identity.displayName, 42)
                    Column(Modifier.weight(1f)) {
                        Text(
                            identity.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.limits.tier ?: "VRChat Legends member",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    if (state.limits.canUploadVideo) "Photos and video enabled"
                    else "Photo posts enabled",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${state.limits.maxBodyLen} characters | up to ${CommunityState.MAX_ATTACHMENTS} attachments",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeedModeTabs(state: CommunityState, onSelect: (FeedMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        FeedMode.entries.forEach { mode ->
            val active = mode == state.feedMode
            val enabled = mode != FeedMode.FOLLOWING || state.canPost
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { onSelect(mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    mode.label,
                    modifier = Modifier.padding(vertical = 13.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        active -> MaterialTheme.colorScheme.primary
                        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent,
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptToolbar(
    state: CommunityState,
    onSort: (ScriptSort) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.scriptQuery,
            onValueChange = onQuery,
            label = { Text("Search scripts") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScriptSort.entries.forEach { sort ->
                FilterChip(
                    selected = sort == state.scriptSort,
                    onClick = { onSort(sort) },
                    label = { Text(sort.label) },
                )
            }
        }
    }
}

@Composable
private fun ScriptRow(
    script: VrclScript,
    installed: Boolean,
    busy: Boolean,
    onLike: () -> Unit,
    onInstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmingInstall by remember(script.id) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(script.authorAvatarUrl, script.authorName, 36)
                Column(Modifier.weight(1f)) {
                    Text(script.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "by ${script.authorName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onLike) {
                    Icon(
                        if (script.viewerLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (script.viewerLiked) SignalCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(" ${script.likeCount}")
                }
            }

            script.summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (script.isLua) {
                        "Lua script | ${script.installs} installs"
                    } else {
                        "${script.steps.size} step${if (script.steps.size == 1) "" else "s"} | ${script.installs} installs"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        when {
                            expanded && script.isLua -> "Hide code"
                            script.isLua -> "Show code"
                            expanded -> "Hide steps"
                            else -> "Show steps"
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { confirmingInstall = true },
                    enabled = !installed && !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = null)
                    }
                    Text(if (installed) "Installed" else "Install")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    script.steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. ${step.describe}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmingInstall) {
        ScriptInstallDialog(
            script = script,
            onDismiss = { confirmingInstall = false },
            onConfirm = {
                confirmingInstall = false
                onInstall()
            },
        )
    }
}

@Composable
private fun ScriptSafetyCard(state: CommunityState) {
    val rejected = state.rejectedScriptFiles + state.rejectedRemoteScripts
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = SignalCyan.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = SignalCyan)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Sandboxed scripts", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Stored in this app's private Scripts folder and never auto-run. Step presets and Lua scripts can only send chatbox lines, current-avatar writable parameters, and waits. No files, shell, intents, raw OSC paths, or network access, ever.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rejected > 0) {
                    Text(
                        "$rejected unsafe or unreadable script ${if (rejected == 1) "entry was" else "entries were"} blocked.",
                        style = MaterialTheme.typography.labelMedium,
                        color = Bad,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptRunnerCard(
    state: CommunityState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val runner = state.scriptRunner
    val color = when {
        runner.error != null -> Bad
        runner.running -> SignalCyan
        else -> Good
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (runner.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = color,
                )
            } else {
                Icon(Icons.Filled.Security, contentDescription = null, tint = color)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    runner.runningTitle ?: if (runner.error != null) "Script stopped safely" else "Script complete",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        runner.error != null -> runner.error
                        runner.running && runner.totalSteps > 0 -> "Step ${runner.currentStep} of ${runner.totalSteps}"
                        runner.running -> runner.message ?: "Running Lua script"
                        else -> runner.message.orEmpty()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (runner.running) {
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Filled.StopCircle, contentDescription = null)
                    Text("Stop")
                }
            } else {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss script status")
                }
            }
        }
    }
}

@Composable
private fun ScriptSectionHeading(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScriptLibraryEmpty() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Text(
            "No scripts installed. Community presets remain inert until you review and install one.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstalledScriptRow(
    script: InstalledCompanionScript,
    running: Boolean,
    anotherRunning: Boolean,
    busy: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember(script.sourceId) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (running) SignalCyan else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(script.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "by ${script.authorName} | ${script.steps.size} step${if (script.steps.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide steps" else "Review steps")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    script.steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. ${step.describe}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRemove, enabled = !running && !busy) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Text("Remove")
                }
                if (running) {
                    Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Bad)) {
                        Icon(Icons.Filled.StopCircle, contentDescription = null)
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onRun, enabled = !anotherRunning && !busy) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Run once")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptInstallDialog(
    script: VrclScript,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Security, contentDescription = null) },
        title = { Text("Install ${script.title}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This exact preset will be copied to private app storage. It cannot run automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    if (script.isLua) {
                        LazyColumn(contentPadding = PaddingValues(12.dp)) {
                            item {
                                Text(
                                    script.luaSource.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(script.steps) { index, step ->
                                Text(
                                    "${index + 1}. ${step.describe}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                Text(
                    if (script.isLua) {
                        "Lua runs in a sealed sandbox. It can only send chatbox lines, writable avatar parameters, and waits. It cannot touch files, the network, or anything else on this headset."
                    } else {
                        "Only these chatbox, writable avatar parameter, and wait actions are permitted."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("Install")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScriptsEmpty(filtered: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (filtered) "Nothing matches that search" else "No community scripts yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (filtered) {
                    "Try a shorter search, or clear it to see everything."
                } else {
                    "Scripts are shareable chatbox and avatar parameter presets. Publish one from the website."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Composer(
    state: CommunityState,
    repository: CommunityRepository,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
) {
    val identity = state.identity
    val maxLength = state.limits.maxBodyLen.coerceAtLeast(1)
    // OpenDocument rather than the photo picker: Horizon OS ships the SAF document UI but
    // has no Google photo picker, and this app already uses SAF for the log folder.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { repository.attach(it.toString()) } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (identity?.canPost != true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = SignalCyan.copy(alpha = 0.14f)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Forum, contentDescription = null, tint = SignalCyan)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Join the conversation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (identity == null) "Sign in from Account to post and follow people."
                        else "Link a Legend profile on the website before posting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(identity.avatarUrl, identity.displayName, 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= maxLength) onDraftChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What is happening in VRChat?") },
                    minLines = 2,
                    maxLines = 6,
                    enabled = !state.posting,
                    shape = RoundedCornerShape(0.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                )

                if (state.attachments.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.attachments.forEach { attachment ->
                            AttachmentChip(
                                attachment = attachment,
                                onRemove = { repository.removeAttachment(attachment.localId) },
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val types = if (state.limits.canUploadVideo) {
                                arrayOf("image/*", "video/*")
                            } else {
                                arrayOf("image/*")
                            }
                            picker.launch(types)
                        },
                        enabled = state.canAttachMore && !state.posting,
                    ) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = if (state.limits.canUploadVideo) {
                                "Add photo, GIF, or video"
                            } else {
                                "Add photo or GIF"
                            },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "${draft.length} / $maxLength",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draft.length >= maxLength) Bad
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onPost,
                        enabled = (draft.isNotBlank() || state.attachments.any { it.done }) &&
                            !state.posting && !state.attachmentsBusy,
                        shape = CircleShape,
                    ) {
                        if (state.posting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = null)
                        }
                        Text("Post")
                    }
                }
            }
        }
    }
}

/** A pending attachment: live upload progress, or the reason it failed. */
@Composable
private fun AttachmentChip(
    attachment: com.vrchatlegends.osccompanion.vrcl.ComposerAttachment,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (attachment.error != null) Bad else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = attachment.previewUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                if (attachment.isVideo) {
                    Icon(
                        Icons.Filled.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column {
                Text(
                    when {
                        attachment.error != null -> "Failed"
                        attachment.done -> "Ready"
                        else -> "${(attachment.progress * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        attachment.error != null -> Bad
                        attachment.done -> Good
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!attachment.done && attachment.error == null) {
                    LinearProgressIndicator(
                        progress = attachment.progress,
                        modifier = Modifier.width(64.dp),
                        color = SignalCyan,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PostRow(
    post: VrclPost,
    state: CommunityState,
    repository: CommunityRepository,
) {
    val authorId = post.authorPlayerId
    // Ask once per author so the follow button is never showing a stale state.
    LaunchedEffect(authorId) { authorId?.let(repository::ensureFollowState) }

    val isSelf = authorId != null && authorId == state.identity?.playerId
    val following = authorId != null && authorId in state.following

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.50f))
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        post.repostedBy?.let {
            Row(
                modifier = Modifier.padding(start = 55.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$it reposted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(post.authorAvatarUrl, post.authorName, 44)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            post.authorName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (post.authorVerified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Verified",
                                tint = SignalCyan,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        post.createdAt?.let {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "| ${relativePostTime(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (authorId != null && !isSelf && state.identity != null) {
                        FollowButton(
                            following = following,
                            busy = authorId in state.followBusy,
                            onClick = { repository.toggleFollow(authorId) },
                        )
                    }
                }

                if (post.body.isNotBlank()) {
                    Text(post.body, style = MaterialTheme.typography.bodyLarge)
                }

                if (post.media.isNotEmpty()) {
                    PostMediaBlock(post.media)
                }

                post.quote?.let { QuoteCard(it) }

                if (post.hashtags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        post.hashtags.take(6).forEach { tag ->
                            Text(
                                "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimelineAction(
                        modifier = Modifier.weight(1f),
                        icon = if (post.viewerLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = post.likeCount.toString(),
                        contentDescription = "Like",
                        active = post.viewerLiked,
                        onClick = { repository.like(post) },
                    )
                    TimelineAction(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.ChatBubbleOutline,
                        label = post.commentCount.toString(),
                        contentDescription = "Comments",
                        onClick = { repository.openThread(post) },
                    )
                    TimelineAction(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Repeat,
                        label = post.repostCount.toString(),
                        contentDescription = "Reposts",
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineAction(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.then(if (onClick != null) {
            Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
        } else {
            Modifier.heightIn(min = 48.dp)
        }),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun FollowButton(following: Boolean, busy: Boolean, onClick: () -> Unit) {
    if (following) {
        OutlinedButton(
            onClick = onClick,
            enabled = !busy,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(48.dp),
        ) {
            Text("Following", style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = !busy,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.height(48.dp),
        ) {
            Text("Follow", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SocialFeedStatus(state: CommunityState) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Live timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (state.live) Good else MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(
                if (state.live) "Connected" else "Reconnecting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "New posts wait above the timeline so the feed never jumps while you are reading.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun relativePostTime(value: String): String = runCatching {
    val seconds = Duration.between(Instant.parse(value), Instant.now()).seconds.coerceAtLeast(0)
    when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> value.take(10)
    }
}.getOrDefault(value.take(10))

@Composable
private fun QuoteCard(quote: com.vrchatlegends.osccompanion.vrcl.VrclQuote) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                quote.authorName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (quote.body.isNotBlank()) {
                Text(
                    quote.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PostMediaBlock(quote.media)
        }
    }
}

@Composable
internal fun Avatar(url: String?, name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "L",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun FeedEmpty(failed: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (failed) "The feed could not be loaded. Check the OSC link and try Refresh."
                else "No posts yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedBanner(state: CommunityState, onDismiss: () -> Unit) {
    val text = state.error ?: state.notice ?: return
    val color = if (state.error != null) Bad else Good
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 5.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = color)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = color)
            }
        }
    }
}
