package com.vrchatlegends.osccompanion.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vrchatlegends.osccompanion.data.MAX_BACKGROUND_DIM
import com.vrchatlegends.osccompanion.data.MIN_BACKGROUND_DIM

/**
 * The user's own image or video sitting behind the whole app.
 *
 * A scrim goes on top of it, because an arbitrary photo behind body text is unreadable and
 * the panel is already being read from about two virtual metres away.
 */
@Composable
fun AppBackground(uri: String, dim: Float, isVideo: Boolean) {
    if (uri.isBlank()) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(6.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        ) {
            if (isVideo) {
                LoopingVideo(uri)
            } else {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background.copy(
                        alpha = dim.coerceIn(MIN_BACKGROUND_DIM, MAX_BACKGROUND_DIM),
                    ),
                ),
        )
    }
}

/** Muted, looping, no controls. It is wallpaper, not content. */
@OptIn(UnstableApi::class)
@Composable
private fun LoopingVideo(uri: String) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
