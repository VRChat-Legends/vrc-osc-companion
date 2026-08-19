package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.vrcl.VrclAuth
import com.vrchatlegends.osccompanion.vrcl.VrclEvent
import com.vrchatlegends.osccompanion.vrcl.VrclProfile
import com.vrchatlegends.osccompanion.vrcl.VrclSocialIdentity

private const val WEBSITE = "https://vrchatlegends.com"
private const val DISCORD = "https://discord.gg/6xPkZ7Dxp9"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val legend by viewModel.legend.collectAsStateWithLifecycle()
    val events by viewModel.vrclEvents.collectAsStateWithLifecycle()
    val error by viewModel.authError.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Account",
        subtitle = profile?.let { "Signed in as ${legend?.displayName ?: it.displayName}" }
            ?: "VRChat Legends",
    ) {
        val activeProfile = profile
        if (activeProfile != null) {
            SignedInDashboard(
                profile = activeProfile,
                legend = legend,
                events = events,
                onRefresh = viewModel::refreshProfile,
                onSignOut = viewModel::signOut,
                onSendEvent = viewModel::sendEventToChatbox,
            )
        } else {
            SignedOutDashboard(
                authError = error,
                savedSessionFailed = settings.vrclToken.isNotBlank(),
                onSignIn = viewModel::signIn,
            )
            AccountLinks(profilePath = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignedOutDashboard(
    authError: String?,
    savedSessionFailed: Boolean,
    onSignIn: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(34.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Connect your Legends account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Your browser handles sign-in and returns a session to this headset.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        SectionCard(
            title = "Sign in",
            subtitle = "Use an account already linked to VRChat Legends",
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VrclAuth.providers.forEachIndexed { index, provider ->
                    if (index == 0) {
                        Button(onClick = { onSignIn(provider.id) }) {
                            Icon(Icons.Filled.Login, contentDescription = null)
                            Text(" ${provider.label}")
                        }
                    } else {
                        OutlinedButton(onClick = { onSignIn(provider.id) }) {
                            Icon(Icons.Filled.Login, contentDescription = null)
                            Text(" ${provider.label}")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = authError != null || savedSessionFailed,
                enter = fadeIn() + slideInVertically { -it / 5 },
                exit = fadeOut(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(7.dp),
                    color = Bad.copy(alpha = 0.11f),
                    border = BorderStroke(1.dp, Bad.copy(alpha = 0.25f)),
                ) {
                    Text(
                        authError ?: "The saved session could not be validated. Sign in again.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Bad,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedInDashboard(
    profile: VrclProfile,
    legend: VrclSocialIdentity?,
    events: List<VrclEvent>,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onSendEvent: (VrclEvent) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val displayName = legend?.displayName ?: profile.displayName
    val profileUrl = legend?.profilePath?.let { WEBSITE + it }
    val openProfile: (() -> Unit)? = profileUrl?.let { url -> { uriHandler.openUri(url) } }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints {
            val wide = maxWidth >= 900.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AccountIdentity(
                        profile = profile,
                        legend = legend,
                        displayName = displayName,
                        eventCount = events.size,
                        onOpenProfile = openProfile,
                        onRefresh = onRefresh,
                        onSignOut = onSignOut,
                        onOpenWebsite = { uriHandler.openUri(WEBSITE) },
                        onOpenDiscord = { uriHandler.openUri(DISCORD) },
                        modifier = Modifier.width(340.dp),
                    )
                    AccountEvents(
                        events = events,
                        onSendEvent = onSendEvent,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    AccountIdentity(
                        profile = profile,
                        legend = legend,
                        displayName = displayName,
                        eventCount = events.size,
                        onOpenProfile = openProfile,
                        onRefresh = onRefresh,
                        onSignOut = onSignOut,
                        onOpenWebsite = { uriHandler.openUri(WEBSITE) },
                        onOpenDiscord = { uriHandler.openUri(DISCORD) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AccountEvents(
                        events = events,
                        onSendEvent = onSendEvent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountIdentity(
    profile: VrclProfile,
    legend: VrclSocialIdentity?,
    displayName: String,
    eventCount: Int,
    onOpenProfile: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenDiscord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(legend?.avatarUrl, displayName, 72)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!legend?.playerId.isNullOrBlank()) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = "Legend profile linked",
                            tint = Good,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Text(
                    legend?.playerId ?: "Legends account",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (profile.roles.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                profile.roles.take(5).forEach { role -> RoleChip(role) }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccountMetric(
                Icons.Filled.AccountCircle,
                "Profile",
                if (!legend?.playerId.isNullOrBlank()) "Linked" else "Not linked",
                Modifier.weight(1f),
            )
            MetricDivider()
            AccountMetric(Icons.Filled.Badge, "Roles", "${profile.roles.size}", Modifier.weight(1f))
            MetricDivider()
            AccountMetric(Icons.Filled.Event, "Events", "$eventCount", Modifier.weight(1f))
        }

        onOpenProfile?.let { open ->
            Button(
                onClick = open,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null)
                Text("  Open profile")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("  Refresh")
            }
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Text("  Sign out")
            }
        }

        Row(Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onOpenWebsite,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text("Website")
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
            }
            TextButton(
                onClick = onOpenDiscord,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text("Discord")
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun AccountMetric(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(38.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun AccountEvents(
    events: List<VrclEvent>,
    onSendEvent: (VrclEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Upcoming events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (events.isEmpty()) "Nothing scheduled" else "${events.size} on your calendar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (events.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "No upcoming events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            events.forEachIndexed { index, event ->
                EventRow(event = event, onSend = onSendEvent)
                if (index != events.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: VrclEvent, onSend: (VrclEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(7.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(event.startsAt, event.location).joinToString(" | ").ifBlank { "Details pending" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onSend(event) }) {
            Icon(Icons.Filled.Send, contentDescription = "Send event to chatbox")
        }
    }
}

@Composable
private fun RoleChip(role: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            role,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountLinks(profilePath: String?) {
    val uriHandler = LocalUriHandler.current
    SectionCard(title = "VRChat Legends", subtitle = "Your community links") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profilePath?.let { path ->
                Button(onClick = { uriHandler.openUri(WEBSITE + path) }) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                    Text(" Profile")
                }
            }
            OutlinedButton(onClick = { uriHandler.openUri(WEBSITE) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Text(" Website")
            }
            OutlinedButton(onClick = { uriHandler.openUri(DISCORD) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Text(" Discord")
            }
        }
    }
}