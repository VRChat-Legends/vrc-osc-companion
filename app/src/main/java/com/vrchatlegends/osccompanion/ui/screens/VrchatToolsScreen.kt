package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenHeader
import com.vrchatlegends.osccompanion.ui.ScreenMaxWidth
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.vrchat.VrchatAvatar
import com.vrchatlegends.osccompanion.vrchat.VrchatFriend
import com.vrchatlegends.osccompanion.vrchat.VrchatNotification
import com.vrchatlegends.osccompanion.vrchat.VrchatToolsRepository
import com.vrchatlegends.osccompanion.vrchat.VrchatToolsState
import com.vrchatlegends.osccompanion.vrchat.VrchatWorld
import kotlinx.coroutines.launch

private enum class VrchatTab(val label: String, val icon: ImageVector) {
    FRIENDS("Friends", Icons.Filled.Groups),
    INBOX("Inbox", Icons.Filled.Notifications),
    AVATARS("Avatars", Icons.Filled.SmartToy),
    WORLDS("Worlds", Icons.Filled.TravelExplore),
}

@Composable
fun VrchatToolsScreen(viewModel: AppViewModel) {
    val state by viewModel.vrchatTools.state.collectAsStateWithLifecycle()
    val repository = viewModel.vrchatTools
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    // All tabs share one list, so a tab switch would otherwise inherit the old scroll offset.
    LaunchedEffect(selectedTab) {
        query = ""
        listState.scrollToItem(0)
    }

    val showBackToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = ScreenMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "VRChat Tools",
                    subtitle = if (state.user == null) "Direct account tools, stored only on this headset"
                    else "Friends, invites, avatars, and worlds without leaving VRChat",
                )
            }

            when {
                !state.sessionChecked -> item(key = "session-loading") { SessionLoading() }
                state.user != null -> signedInItems(
                    state = state,
                    repository = repository,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    query = query,
                    onQueryChange = { query = it },
                    sendChatbox = viewModel::sendChatbox,
                )
                state.twoFactorMethods.isNotEmpty() ->
                    item(key = "two-factor") { TwoFactorPanel(state, repository) }
                else -> item(key = "sign-in") { VrchatSignIn(state, repository) }
            }
        }

        AnimatedVisibility(
            visible = showBackToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                icon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                text = { Text("Back to top") },
                containerColor = SignalCoral,
            )
        }
    }
}

@Composable
private fun SessionLoading() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Text("Checking your encrypted VRChat session")
        }
    }
}

@Composable
private fun VrchatSignIn(state: VrchatToolsState, repository: VrchatToolsRepository) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (username.isBlank() || password.isBlank() || state.busy) return
        val submittedPassword = password
        password = ""
        focusManager.clearFocus()
        repository.signIn(username.trim(), submittedPassword)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = SignalCyan.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.28f)),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolIcon(Icons.Filled.Security, SignalCyan)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Private by design", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Only encrypted session cookies are saved. Your password and codes are never stored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Sign in to VRChat", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username or email") },
                    leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                MessageBanner(state)
                Button(
                    onClick = ::submit,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = username.isNotBlank() && password.isNotBlank() && !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalCoral),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.Login, contentDescription = null)
                    }
                    Text(if (state.busy) "Signing in" else "Sign in")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TwoFactorPanel(state: VrchatToolsState, repository: VrchatToolsRepository) {
    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val method = when {
        state.twoFactorMethods.any { it.equals("totp", true) } -> "Authenticator code"
        state.twoFactorMethods.any { it.equals("emailOtp", true) } -> "Email code"
        else -> "Recovery code"
    }

    fun submit() {
        if (code.isBlank() || state.busy) return
        val submittedCode = code
        code = ""
        focusManager.clearFocus()
        repository.verifyTwoFactor(submittedCode)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolIcon(Icons.Filled.Shield, SignalCyan)
                Column {
                    Text("Two-factor check", style = MaterialTheme.typography.titleMedium)
                    Text(
                        method,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(method) },
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (method == "Recovery code") KeyboardType.Text else KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            MessageBanner(state)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = ::submit, enabled = code.isNotBlank() && !state.busy) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                    Text("Verify")
                }
                OutlinedButton(onClick = repository::cancelSignIn, enabled = !state.busy) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Text("Cancel")
                }
            }
        }
    }
}

private fun LazyListScope.signedInItems(
    state: VrchatToolsState,
    repository: VrchatToolsRepository,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sendChatbox: (String) -> Unit,
) {
    val tabs = VrchatTab.entries
    val tab = tabs[selectedTab.coerceIn(tabs.indices)]

    item(key = "account") { AccountBar(state, repository) }

    item(key = "banner") {
        AnimatedVisibility(
            visible = state.error != null || state.notice != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            MessageBanner(state, onDismiss = repository::clearNotice)
        }
    }

    item(key = "tabs") { ToolsTabRow(state, selectedTab, onSelectTab) }

    if (tab != VrchatTab.INBOX) {
        item(key = "search") { ToolsSearchField(tab.label, query, onQueryChange) }
    }

    when (tab) {
        VrchatTab.FRIENDS -> friendItems(state, query, repository, sendChatbox)
        VrchatTab.INBOX -> notificationItems(state, repository, sendChatbox)
        VrchatTab.AVATARS -> avatarItems(state, query, repository, sendChatbox)
        VrchatTab.WORLDS -> worldItems(state, query, sendChatbox)
    }
}

@Composable
private fun ToolsTabRow(state: VrchatToolsState, selectedTab: Int, onSelectTab: (Int) -> Unit) {
    Column {
        if (state.refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            VrchatTab.entries.forEachIndexed { index, tab ->
                val count = when (tab) {
                    VrchatTab.FRIENDS -> state.friends.count(VrchatFriend::isOnline)
                    VrchatTab.INBOX -> state.notifications.size
                    VrchatTab.AVATARS -> state.favoriteAvatars.size
                    VrchatTab.WORLDS -> state.favoriteWorlds.size
                }
                Tab(
                    selected = selectedTab == index,
                    onClick = { onSelectTab(index) },
                    text = { Text("${tab.label}  $count", maxLines = 1) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun ToolsSearchField(label: String, query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else null,
        placeholder = { Text("Search ${label.lowercase()}") },
        singleLine = true,
    )
}

@Composable
private fun AccountBar(state: VrchatToolsState, repository: VrchatToolsRepository) {
    val user = state.user ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Good.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Good.copy(alpha = 0.26f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(user.imageUrl, user.displayName, 52)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(user.platform, user.statusDescription.ifBlank { user.status })
                        .filter(String::isNotBlank)
                        .joinToString(" | ")
                        .ifBlank { "VRChat account connected" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = repository::refresh,
                enabled = !state.refreshing && state.activeAction == null,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh VRChat data")
            }
            IconButton(
                onClick = repository::signOut,
                enabled = !state.busy && !state.refreshing && state.activeAction == null,
            ) {
                Icon(Icons.Filled.Logout, contentDescription = "Sign out of VRChat")
            }
        }
    }
}

private fun LazyListScope.friendItems(
    state: VrchatToolsState,
    query: String,
    repository: VrchatToolsRepository,
    sendChatbox: (String) -> Unit,
) {
    val friends = state.friends.filter {
        query.isBlank() || it.displayName.contains(query, true) || it.locationLabel.contains(query, true)
    }
    if (friends.isEmpty()) {
        item(key = "friends-empty") { EmptyState(Icons.Filled.Groups, "No friends match this view") }
        return
    }
    val online = friends.filter(VrchatFriend::isOnline)
    val offline = friends.filterNot(VrchatFriend::isOnline)

    items(online, key = { "friend-online-${it.id}" }) { friend ->
        FriendRow(friend, state.activeAction, repository, sendChatbox)
    }
    if (offline.isNotEmpty()) {
        item(key = "friends-offline-header") {
            Text(
                "OFFLINE  ${offline.size}",
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        items(offline, key = { "friend-offline-${it.id}" }) { friend ->
            FriendRow(friend, state.activeAction, repository, sendChatbox)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendRow(
    friend: VrchatFriend,
    activeAction: String?,
    repository: VrchatToolsRepository,
    sendChatbox: (String) -> Unit,
) {
    ItemSurface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RemoteImage(friend.imageUrl, friend.displayName, 48)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(friend.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            friend.statusDescription.ifBlank { friend.status },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusPill(friend.locationLabel, if (friend.isOnline) Good else MaterialTheme.colorScheme.outline)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (friend.platform.isNotBlank()) AssistChip(onClick = {}, label = { Text(friend.platform) })
                    if (friend.canJoin) {
                        ActionButton(
                            label = "Join",
                            icon = Icons.Filled.RocketLaunch,
                            loading = activeAction == "join:${friend.id}",
                            enabled = activeAction == null,
                            onClick = { repository.joinFriend(friend) },
                        )
                    }
                    if (friend.canRequestInvite) {
                        OutlinedButton(
                            onClick = { repository.requestInvite(friend) },
                            enabled = activeAction == null,
                        ) {
                            ActionIcon(activeAction == "request:${friend.id}", Icons.Filled.Mail)
                            Text("Request")
                        }
                    }
                    if (friend.isOnline) {
                        OutlinedButton(
                            onClick = { repository.inviteFriend(friend) },
                            enabled = activeAction == null,
                        ) {
                            ActionIcon(activeAction == "invite:${friend.id}", Icons.Filled.Send)
                            Text("Invite")
                        }
                    }
                    TextButton(
                        onClick = {
                            sendChatbox(
                                "${friend.displayName} | ${friend.locationLabel}" +
                                    friend.platform.takeIf(String::isNotBlank)?.let { " | $it" }.orEmpty(),
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Chat, contentDescription = null)
                        Text("Chatbox")
                    }
                }
            }
        }
    }
}

private fun LazyListScope.notificationItems(
    state: VrchatToolsState,
    repository: VrchatToolsRepository,
    sendChatbox: (String) -> Unit,
) {
    if (state.notifications.isEmpty()) {
        item(key = "inbox-empty") { EmptyState(Icons.Filled.Notifications, "Inbox clear") }
        return
    }
    items(state.notifications, key = { "notification-${it.id}" }) { notification ->
        NotificationRow(notification, state.activeAction, repository, sendChatbox)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationRow(
    notification: VrchatNotification,
    activeAction: String?,
    repository: VrchatToolsRepository,
    sendChatbox: (String) -> Unit,
) {
    ItemSurface {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                ToolIcon(
                    when {
                        notification.isFriendRequest -> Icons.Filled.Person
                        notification.isInvite -> Icons.Filled.RocketLaunch
                        else -> Icons.Filled.Notifications
                    },
                    if (notification.isInvite) SignalCoral else SignalCyan,
                    size = 42,
                )
                Column(Modifier.weight(1f)) {
                    Text(notification.senderDisplayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        notification.type.replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(notification.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (notification.isFriendRequest) {
                    ActionButton(
                        label = "Accept",
                        icon = Icons.Filled.Check,
                        loading = activeAction == "accept:${notification.id}",
                        enabled = activeAction == null,
                        onClick = { repository.acceptFriendRequest(notification) },
                    )
                }
                if (notification.isInvite && notification.worldId != null && notification.instanceId != null) {
                    ActionButton(
                        label = "Join",
                        icon = Icons.Filled.RocketLaunch,
                        loading = activeAction == "join-notification:${notification.id}",
                        enabled = activeAction == null,
                        onClick = { repository.joinInvite(notification) },
                    )
                }
                if (notification.isRequestInvite) {
                    ActionButton(
                        label = "Invite",
                        icon = Icons.Filled.Send,
                        loading = activeAction == "invite-request:${notification.id}",
                        enabled = activeAction == null,
                        onClick = { repository.respondToInviteRequest(notification) },
                    )
                }
                OutlinedButton(
                    onClick = { repository.hideNotification(notification) },
                    enabled = activeAction == null,
                ) {
                    ActionIcon(activeAction == "hide:${notification.id}", Icons.Filled.Close)
                    Text("Dismiss")
                }
                TextButton(onClick = { sendChatbox("${notification.senderDisplayName}: ${notification.message}") }) {
                    Icon(Icons.Filled.Chat, contentDescription = null)
                    Text("Chatbox")
                }
            }
        }
    }
}

private fun LazyListScope.avatarItems(
    state: VrchatToolsState,
    query: String,
    repository: VrchatToolsRepository,
    sendChatbox: (String) -> Unit,
) {
    val avatars = state.favoriteAvatars.filter {
        query.isBlank() || it.name.contains(query, true) || it.authorName.contains(query, true)
    }
    if (avatars.isEmpty()) {
        item(key = "avatars-empty") { EmptyState(Icons.Filled.SmartToy, "No favorite avatars match") }
        return
    }
    items(avatars, key = { "avatar-${it.id}" }) { avatar ->
        AvatarRow(avatar, state.activeAction, repository, sendChatbox)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarRow(
    avatar: VrchatAvatar,
    activeAction: String?,
    repository: VrchatToolsRepository,
    sendChatbox: (String) -> Unit,
) {
    ItemSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
            RemoteImage(avatar.imageUrl, avatar.name, 64)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(avatar.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "by ${avatar.authorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (avatar.description.isNotBlank()) {
                    Text(
                        avatar.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    avatar.platforms.forEach { platform ->
                        AssistChip(onClick = {}, label = { Text(platform) })
                    }
                    ActionButton(
                        label = "Equip",
                        icon = Icons.Filled.Check,
                        loading = activeAction == "avatar:${avatar.id}",
                        enabled = activeAction == null,
                        onClick = { repository.selectAvatar(avatar) },
                    )
                    TextButton(onClick = { sendChatbox("Avatar: ${avatar.name} by ${avatar.authorName}") }) {
                        Icon(Icons.Filled.Chat, contentDescription = null)
                        Text("Chatbox")
                    }
                }
            }
        }
    }
}

private fun LazyListScope.worldItems(
    state: VrchatToolsState,
    query: String,
    sendChatbox: (String) -> Unit,
) {
    val worlds = state.favoriteWorlds.filter {
        query.isBlank() || it.name.contains(query, true) || it.authorName.contains(query, true)
    }
    if (worlds.isEmpty()) {
        item(key = "worlds-empty") { EmptyState(Icons.Filled.TravelExplore, "No favorite worlds match") }
        return
    }
    items(worlds, key = { "world-${it.id}" }) { world ->
        WorldRow(world, sendChatbox)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldRow(world: VrchatWorld, sendChatbox: (String) -> Unit) {
    ItemSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
            RemoteImage(world.imageUrl, world.name, 72)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(world.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "by ${world.authorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (world.capacity > 0) AssistChip(onClick = {}, label = { Text("${world.capacity} capacity") })
                    world.platforms.forEach { platform ->
                        AssistChip(onClick = {}, label = { Text(platform) })
                    }
                    TextButton(onClick = { sendChatbox("World: ${world.name} by ${world.authorName}") }) {
                        Icon(Icons.Filled.Chat, contentDescription = null)
                        Text("Chatbox")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun RemoteImage(url: String?, label: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SignalCyan.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.trim().firstOrNull()?.uppercase() ?: "V",
            style = MaterialTheme.typography.titleMedium,
            color = SignalCyan,
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
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, size: Int = 48) {
    Surface(modifier = Modifier.size(size.dp), shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled) {
        ActionIcon(loading, icon)
        Text(label)
    }
}

@Composable
private fun ActionIcon(loading: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(17.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBanner(state: VrchatToolsState, onDismiss: (() -> Unit)? = null) {
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
            Icon(
                if (state.error != null) Icons.Filled.Close else Icons.Filled.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = color)
            onDismiss?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss message", tint = color)
                }
            }
        }
    }
}