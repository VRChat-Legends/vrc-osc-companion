package com.vrchatlegends.osccompanion.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.vrcl.VrclMedia
import com.vrchatlegends.osccompanion.vrcl.VrclPhotoMeta

private class ActiveVideoController {
    var activeToken: Any? by mutableStateOf(null)
        private set

    fun play(token: Any) {
        activeToken = token
    }

    fun stop(token: Any) {
        if (activeToken === token) activeToken = null
    }
}

private val LocalActiveVideoController = compositionLocalOf<ActiveVideoController?> { null }

/** Limits a feed to one active decoder and one audio session at a time. */
@Composable
fun PostMediaPlaybackScope(content: @Composable () -> Unit) {
    val controller = remember { ActiveVideoController() }
    CompositionLocalProvider(LocalActiveVideoController provides controller, content = content)
}

/**
 * Every attachment on a post: images, video, and the VRChat camera metadata the backend
 * pulls out of a screenshot. A post with several images becomes a swipeable pager rather
 * than a tall stack, because a Quest panel has very little vertical room.
 */
@Composable
fun PostMediaBlock(media: List<VrclMedia>, modifier: Modifier = Modifier) {
    if (media.isEmpty()) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (media.size == 1) {
            MediaFrame(media.first())
        } else {
            val pagerState = rememberPagerState(pageCount = { media.size })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                pageSpacing = 8.dp,
            ) { page ->
                MediaFrame(media[page])
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                media.indices.forEach { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) SignalCoral
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
        }

        // One metadata card per post, taken from whichever attachment actually carries it.
        media.firstNotNullOfOrNull { it.vrchat }?.let { VrchatPhotoCard(it) }
    }
}

/** One attachment, with the spoiler gate applied before anything is decoded. */
@Composable
private fun MediaFrame(item: VrclMedia) {
    var revealed by remember(item.url) { mutableStateOf(!item.spoiler) }

    Box(
        Modifier
            .fillMaxWidth()
            // Very tall portrait shots would otherwise push the whole card off screen.
            .heightIn(max = 320.dp)
            .aspectRatio(item.aspectRatio.coerceIn(0.6f, 2.2f))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.isVideo) {
            VideoFrame(item, enabled = revealed)
        } else {
            MediaPreview(
                url = item.url,
                contentDescription = if (item.url.substringBefore('?').endsWith(".gif", true)) {
                    "Animated post image"
                } else {
                    "Post image"
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (revealed) Modifier else Modifier.blur(28.dp)),
            )
        }

        if (!revealed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { revealed = true },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = Color.White)
                    Text("Tap to reveal", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Video stays a still frame until the user asks for it. Autoplaying every clip in a feed
 * would chew through the headset battery and the Wi-Fi link for content nobody asked to watch.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(item: VrclMedia, enabled: Boolean) {
    val context = LocalContext.current
    val scopedController = LocalActiveVideoController.current
    val localController = remember { ActiveVideoController() }
    val controller = scopedController ?: localController
    val playbackToken = remember(item.url) { Any() }
    val active = controller.activeToken === playbackToken
    var muted by remember(playbackToken) { mutableStateOf(true) }
    var playbackState by remember(playbackToken) { mutableIntStateOf(Player.STATE_IDLE) }
    var playbackError by remember(playbackToken) { mutableStateOf<String?>(null) }

    DisposableEffect(controller, playbackToken) {
        onDispose { controller.stop(playbackToken) }
    }

    if (!active) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MediaPreview(
                url = item.url,
                contentDescription = "Video preview",
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.clickable(enabled = enabled) { controller.play(playbackToken) },
            ) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
        return
    }

    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
    val player = remember(item.url, playbackToken) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(item.url))
            setAudioAttributes(audioAttributes, false)
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player, muted) {
        player.setAudioAttributes(audioAttributes, !muted)
        player.volume = if (muted) 0f else 1f
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) playbackError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
            }
        }
        player.addListener(listener)
        playbackState = player.playbackState
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 2_500
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(36.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        playbackError?.let { errorCode ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.78f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Could not play video", color = Color.White)
                    Text(
                        errorCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    TextButton(
                        onClick = {
                            playbackError = null
                            playbackState = Player.STATE_BUFFERING
                            player.stop()
                            player.setMediaItem(MediaItem.fromUri(item.url))
                            player.prepare()
                            player.playWhenReady = true
                        },
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null)
                        Text("Retry")
                    }
                }
            }
        }

        if (playbackState == Player.STATE_ENDED && playbackError == null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
            ) {
                IconButton(
                    onClick = {
                        player.seekTo(0)
                        player.play()
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Filled.Replay,
                        contentDescription = "Replay video",
                        tint = Color.White,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.68f),
        ) {
            IconButton(
                onClick = { muted = !muted },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (muted) "Unmute video" else "Mute video",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun MediaPreview(url: String, contentDescription: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        },
        error = {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = Color.White)
                Text(
                    "Media unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        },
    )
}

/** World, photographer and who was in the shot, straight out of the PNG's VRChat metadata. */
@Composable
private fun VrchatPhotoCard(meta: VrclPhotoMeta) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = SignalCyan.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.25f)),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = SignalCyan,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "VRChat camera",
                    style = MaterialTheme.typography.labelLarge,
                    color = SignalCyan,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            meta.worldName?.let { world ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        world,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            meta.authorName?.let {
                Text(
                    "Shot by $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (meta.players.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded },
                ) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (expanded) "Hide the ${meta.players.size} people in this shot"
                        else "${meta.players.size} people in this shot",
                        style = MaterialTheme.typography.labelMedium,
                        color = SignalCyan,
                    )
                }
                if (expanded) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(meta.players) { name ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
