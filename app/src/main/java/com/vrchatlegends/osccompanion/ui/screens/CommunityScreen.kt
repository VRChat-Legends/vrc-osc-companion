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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
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
import com.vrchatlegends.osccompanion.vrcl.CommunityState
import com.vrchatlegends.osccompanion.vrcl.VrclPost
import kotlinx.coroutines.launch

private const val SOCIAL_URL = "https://vrchatlegends.com/social"
private const val MAX_POST_LENGTH = 500

@Composable
fun CommunityScreen(viewModel: AppViewModel) {
    val state by viewModel.community.state.collectAsStateWithLifecycle()
    val repository = viewModel.community
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { repository.loadOnce() }

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
                    title = "Community",
                    subtitle = "The VRChat Legends feed, in the headset",
                )
            }

            item(key = "toolbar") { FeedToolbar(state, repository::refresh) }

            item(key = "messages") {
                AnimatedVisibility(
                    visible = state.error != null || state.notice != null,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                ) {
                    FeedBanner(state, repository::clearMessages)
                }
            }

            item(key = "composer") {
                Composer(
                    state = state,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onPost = { repository.post(draft) { draft = "" } },
                )
            }

            if (state.posts.isEmpty() && state.loaded) {
                item(key = "empty") { FeedEmpty(state.error != null) }
            }

            items(state.posts, key = { it.key }) { post ->
                PostRow(post, onLike = { repository.like(post) })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedToolbar(state: CommunityState, onRefresh: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("Refresh")
            }
            OutlinedButton(onClick = { uriHandler.openUri(SOCIAL_URL) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Text("Open on the website")
            }
            OutlinedButton(onClick = { uriHandler.openUri("https://discord.gg/6xPkZ7Dxp9") }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Text("Discord")
            }
        }
    }
}

@Composable
private fun Composer(
    state: CommunityState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
) {
    val identity = state.identity
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (identity?.canPost != true) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = SignalCyan.copy(alpha = 0.14f)) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Forum, contentDescription = null, tint = SignalCyan)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Reading as a guest", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (identity == null) "Sign in on the Account tab to post to the feed."
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(identity.avatarUrl, identity.displayName, 40)
                Text("Post as ${identity.displayName}", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= MAX_POST_LENGTH) onDraftChange(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What is happening in VRChat?") },
                minLines = 3,
                enabled = !state.posting,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${draft.length} / $MAX_POST_LENGTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (draft.length >= MAX_POST_LENGTH) Bad
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onPost,
                    enabled = draft.isNotBlank() && !state.posting,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalCoral),
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

@Composable
private fun PostRow(post: VrclPost, onLike: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            post.repostedBy?.let {
                Text(
                    "$it reposted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Avatar(post.authorAvatarUrl, post.authorName, 44)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            post.authorName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (post.authorVerified) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Verified",
                                tint = SignalCyan,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        post.createdAt?.let {
                            Text(
                                it.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (post.body.isNotBlank()) {
                        Text(post.body, style = MaterialTheme.typography.bodyMedium)
                    }
                    post.imageUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(7.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onLike) {
                            Icon(
                                if (post.viewerLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (post.viewerLiked) SignalCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("${post.likeCount}")
                        }
                        Text(
                            "${post.commentCount} comments  |  ${post.repostCount} reposts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(url: String?, name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(SignalCoral.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "L",
            style = MaterialTheme.typography.titleMedium,
            color = SignalCoral,
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
