package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.vrcl.CommunityRepository
import com.vrchatlegends.osccompanion.vrcl.CommunityState
import com.vrchatlegends.osccompanion.vrcl.VrclComment

private const val MAX_COMMENT_LENGTH = 500

/**
 * The comment thread for one post.
 *
 * A full-width dialog rather than a bottom sheet: the panel is landscape, so a sheet would
 * leave a thread squeezed into a couple of centimetres at the bottom of the window.
 */
@Composable
fun CommentDialog(state: CommunityState, repository: CommunityRepository) {
    val post = state.thread.post ?: return
    var draft by remember(post.id) { mutableStateOf("") }

    Dialog(
        onDismissRequest = repository::closeThread,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .widthIn(max = 900.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Replies to ${post.authorName}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (post.body.isNotBlank()) {
                            Text(
                                post.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = repository::closeThread) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                if (state.thread.loading && state.thread.comments.isEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = SignalCyan)
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(Modifier.weight(1f)) {
                    if (state.thread.comments.isEmpty() && !state.thread.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No replies yet. Be the first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.thread.comments, key = { it.id }) { comment ->
                                CommentRow(
                                    comment = comment,
                                    onLike = { repository.likeComment(comment) },
                                    onReply = { repository.setReplyTo(comment) },
                                    onDelete = { repository.deleteComment(comment) },
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                state.thread.replyTo?.let { target ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Replying to ${target.authorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalCyan,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { repository.setReplyTo(null) }) { Text("Cancel") }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= MAX_COMMENT_LENGTH) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write a reply") },
                        enabled = !state.thread.sending,
                        maxLines = 3,
                    )
                    IconButton(
                        onClick = {
                            repository.sendComment(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank() && !state.thread.sending,
                    ) {
                        if (state.thread.sending) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SignalCoral)
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = "Send", tint = SignalCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: VrclComment,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Depth is already clamped by the parser, so this can never run off the edge.
            .padding(start = (comment.depth * 22).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(comment.authorAvatarUrl, comment.authorName, 32)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    comment.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                comment.createdAt?.let {
                    Text(
                        it.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLike) {
                    Icon(
                        if (comment.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (comment.likedByMe) SignalCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(" ${comment.likeCount}", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onReply) {
                    Icon(
                        Icons.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(" Reply", style = MaterialTheme.typography.labelMedium)
                }
                if (comment.canDelete) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
