package com.vrchatlegends.osccompanion.vrcl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.vrchatlegends.osccompanion.BuildConfig

/**
 * VRChat Legends sign-in.
 *
 * The API's OAuth start route already understands app deep links: pass
 * `returnTo=vrcoscc://auth` and the callback redirects to
 * `vrcoscc://auth?session_token=<jwt>` instead of back into the web frontend. That means
 * no password is ever typed into this app and no backend change is required.
 *
 * This must talk to the API host directly. vrchatlegends.com only proxies /api via a
 * Next.js rewrite, and Cloudflare caches that path, so the OAuth redirect came back as a
 * cached HTML shell instead of a 302 to Discord.
 *
 * If the account has 2FA the browser lands on the website's 2FA page first and only then
 * follows the deep link, so the Custom Tab has to stay open until we get the callback.
 */
object VrclAuth {

    const val REDIRECT_URI = "${BuildConfig.AUTH_REDIRECT_SCHEME}://auth"
    private const val TOKEN_PARAM = "session_token"

    val providers = listOf(
        AuthProvider("discord", "Discord"),
    )

    data class AuthProvider(val id: String, val label: String)

    fun startUrl(providerId: String): String =
        Uri.parse(BuildConfig.VRCL_API_BASE_URL)
            .buildUpon()
            .appendEncodedPath("api/oauth/$providerId/start")
            .appendQueryParameter("returnTo", REDIRECT_URI)
            .build()
            .toString()

    fun launch(context: Context, providerId: String) {
        val uri = Uri.parse(startUrl(providerId))
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
                .launchUrl(context, uri)
        }.onFailure {
            // Horizon OS may not expose a Custom Tabs provider; the system browser works too.
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Pulls the session token out of the `vrcoscc://auth?session_token=...` callback. */
    fun extractToken(uri: Uri?): String? {
        if (uri == null) return null
        if (!uri.scheme.equals(BuildConfig.AUTH_REDIRECT_SCHEME, ignoreCase = true)) return null
        return uri.getQueryParameter(TOKEN_PARAM)?.takeIf { it.isNotBlank() }
    }

    fun needsPasswordSetup(uri: Uri?): Boolean =
        uri?.getQueryParameter("prompt") == "create-password"
}
