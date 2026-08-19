package com.vrchatlegends.osccompanion

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.data.AppTheme
import com.vrchatlegends.osccompanion.ui.AppBackground
import com.vrchatlegends.osccompanion.ui.AppRoot
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.theme.VrcOscTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        viewModel.handleAuthCallback(intent?.data)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val accent = settings.accentColor.takeIf { it != 0L }?.let { Color(it.toInt()) }
            val hasBackground = settings.backgroundUri.isNotBlank()
            val backgroundIsVideo = remember(settings.backgroundUri) {
                viewModel.isVideoUri(settings.backgroundUri)
            }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.appTheme) {
                AppTheme.SYSTEM -> systemDark
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            VrcOscTheme(darkTheme = darkTheme, accent = accent) {
                Box(Modifier.fillMaxSize()) {
                    AppBackground(
                        uri = settings.backgroundUri,
                        dim = settings.backgroundDim,
                        isVideo = backgroundIsVideo,
                    )
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        // Transparent so the wallpaper shows through; opaque otherwise, or the
                        // panel renders on whatever Horizon has behind it.
                        color = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background,
                    ) {
                        AppRoot(viewModel)
                    }
                }
            }
        }
    }

    /** singleTask, so the OAuth deep link arrives here rather than in a new instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleAuthCallback(intent.data)
    }

    override fun onPostResume() {
        super.onPostResume()
        viewModel.onAppResumed()
    }

    override fun onPause() {
        viewModel.onAppPaused()
        super.onPause()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
