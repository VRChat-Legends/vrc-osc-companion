package com.vrchatlegends.osccompanion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vrchatlegends.osccompanion.bridge.PcBridge
import com.vrchatlegends.osccompanion.net.NetworkUtils
import com.vrchatlegends.osccompanion.osc.VrcOsc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    /** Empty means "follow the headset's own LAN IP", resolved at connect time. */
    val oscHost: String = "",
    val oscSendPort: Int = VrcOsc.DEFAULT_SEND_PORT,
    val oscReceivePort: Int = VrcOsc.DEFAULT_RECEIVE_PORT,
    val useOscQuery: Boolean = true,
    val useBroadcast: Boolean = false,
    val autoConnect: Boolean = true,

    /** PC bridge. Off until the user supplies a desktop address. */
    val bridgeEnabled: Boolean = false,
    val bridgePcHost: String = "",
    val bridgePcPort: Int = PcBridge.DEFAULT_PC_PORT,
    val bridgeListenPort: Int = PcBridge.DEFAULT_LISTEN_PORT,
    val bridgeBlockPrefixes: String = "/tracking/",
    val bridgeRateLimitHz: Int = 0,
    val bridgeRestrictToPcHost: Boolean = true,

    /** Persisted storage access framework grant for VRChat's log folder. */
    val logFolderUri: String = "",
    val logAutoRefresh: Boolean = true,

    val chatboxSilent: Boolean = true,
    val chatboxShowTyping: Boolean = true,
    val statusEnabled: Boolean = false,
    val statusIntervalSec: Int = 6,
    val statusShowClock: Boolean = true,
    val statusShowBattery: Boolean = true,
    val statusShowHeartRate: Boolean = false,
    val statusShowVrcl: Boolean = false,
    val statusPrefix: String = "",

    val pulsoidToken: String = "",
    val heartRateToParameters: Boolean = true,
    val heartRateMax: Int = 200,

    val vrclToken: String = "",
    val vrclDisplayName: String = "",

    val lastEyeHeight: Float = 1.7f,
) {
    /** Resolves the blank "auto" host to the headset's current address. */
    fun resolvedHost(context: Context?): String =
        oscHost.ifBlank { NetworkUtils.localIpv4OrLoopback(context) }

    val isAutoHost: Boolean get() = oscHost.isBlank()

    fun bridgeConfig(): PcBridge.Config = PcBridge.Config(
        enabled = bridgeEnabled,
        pcHost = bridgePcHost,
        pcPort = bridgePcPort,
        listenPort = bridgeListenPort,
        uplinkBlockPrefixes = PcBridge.parsePrefixes(bridgeBlockPrefixes),
        uplinkRateLimitHz = bridgeRateLimitHz,
        restrictToPcHost = bridgeRestrictToPcHost,
    )
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val OSC_HOST = stringPreferencesKey("osc_host")
        val OSC_SEND_PORT = intPreferencesKey("osc_send_port")
        val OSC_RECEIVE_PORT = intPreferencesKey("osc_receive_port")
        val USE_OSCQUERY = booleanPreferencesKey("use_oscquery")
        val USE_BROADCAST = booleanPreferencesKey("use_broadcast")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")

        val BRIDGE_ENABLED = booleanPreferencesKey("bridge_enabled")
        val BRIDGE_PC_HOST = stringPreferencesKey("bridge_pc_host")
        val BRIDGE_PC_PORT = intPreferencesKey("bridge_pc_port")
        val BRIDGE_LISTEN_PORT = intPreferencesKey("bridge_listen_port")
        val BRIDGE_BLOCK = stringPreferencesKey("bridge_block")
        val BRIDGE_RATE_HZ = intPreferencesKey("bridge_rate_hz")
        val BRIDGE_RESTRICT = booleanPreferencesKey("bridge_restrict")

        val LOG_FOLDER_URI = stringPreferencesKey("log_folder_uri")
        val LOG_AUTO_REFRESH = booleanPreferencesKey("log_auto_refresh")

        val CHATBOX_SILENT = booleanPreferencesKey("chatbox_silent")
        val CHATBOX_TYPING = booleanPreferencesKey("chatbox_typing")
        val STATUS_ENABLED = booleanPreferencesKey("status_enabled")
        val STATUS_INTERVAL = intPreferencesKey("status_interval")
        val STATUS_CLOCK = booleanPreferencesKey("status_clock")
        val STATUS_BATTERY = booleanPreferencesKey("status_battery")
        val STATUS_HR = booleanPreferencesKey("status_hr")
        val STATUS_VRCL = booleanPreferencesKey("status_vrcl")
        val STATUS_PREFIX = stringPreferencesKey("status_prefix")

        val PULSOID_TOKEN = stringPreferencesKey("pulsoid_token")
        val HR_TO_PARAMS = booleanPreferencesKey("hr_to_params")
        val HR_MAX = intPreferencesKey("hr_max")

        val VRCL_TOKEN = stringPreferencesKey("vrcl_token")
        val VRCL_NAME = stringPreferencesKey("vrcl_name")

        val LAST_EYE_HEIGHT = floatPreferencesKey("last_eye_height")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            oscHost = p[Keys.OSC_HOST] ?: "",
            oscSendPort = p[Keys.OSC_SEND_PORT] ?: VrcOsc.DEFAULT_SEND_PORT,
            oscReceivePort = p[Keys.OSC_RECEIVE_PORT] ?: VrcOsc.DEFAULT_RECEIVE_PORT,
            useOscQuery = p[Keys.USE_OSCQUERY] ?: true,
            useBroadcast = p[Keys.USE_BROADCAST] ?: false,
            autoConnect = p[Keys.AUTO_CONNECT] ?: true,
            bridgeEnabled = p[Keys.BRIDGE_ENABLED] ?: false,
            bridgePcHost = p[Keys.BRIDGE_PC_HOST] ?: "",
            bridgePcPort = p[Keys.BRIDGE_PC_PORT] ?: PcBridge.DEFAULT_PC_PORT,
            bridgeListenPort = p[Keys.BRIDGE_LISTEN_PORT] ?: PcBridge.DEFAULT_LISTEN_PORT,
            bridgeBlockPrefixes = p[Keys.BRIDGE_BLOCK] ?: "/tracking/",
            bridgeRateLimitHz = p[Keys.BRIDGE_RATE_HZ] ?: 0,
            bridgeRestrictToPcHost = p[Keys.BRIDGE_RESTRICT] ?: true,
            logFolderUri = p[Keys.LOG_FOLDER_URI] ?: "",
            logAutoRefresh = p[Keys.LOG_AUTO_REFRESH] ?: true,
            chatboxSilent = p[Keys.CHATBOX_SILENT] ?: true,
            chatboxShowTyping = p[Keys.CHATBOX_TYPING] ?: true,
            statusEnabled = p[Keys.STATUS_ENABLED] ?: false,
            statusIntervalSec = p[Keys.STATUS_INTERVAL] ?: 6,
            statusShowClock = p[Keys.STATUS_CLOCK] ?: true,
            statusShowBattery = p[Keys.STATUS_BATTERY] ?: true,
            statusShowHeartRate = p[Keys.STATUS_HR] ?: false,
            statusShowVrcl = p[Keys.STATUS_VRCL] ?: false,
            statusPrefix = p[Keys.STATUS_PREFIX] ?: "",
            pulsoidToken = p[Keys.PULSOID_TOKEN] ?: "",
            heartRateToParameters = p[Keys.HR_TO_PARAMS] ?: true,
            heartRateMax = p[Keys.HR_MAX] ?: 200,
            vrclToken = p[Keys.VRCL_TOKEN] ?: "",
            vrclDisplayName = p[Keys.VRCL_NAME] ?: "",
            lastEyeHeight = p[Keys.LAST_EYE_HEIGHT] ?: 1.7f,
        )
    }

    suspend fun setOscHost(value: String) = put(Keys.OSC_HOST, value.trim())
    suspend fun setOscSendPort(value: Int) = put(Keys.OSC_SEND_PORT, value)
    suspend fun setOscReceivePort(value: Int) = put(Keys.OSC_RECEIVE_PORT, value)
    suspend fun setUseOscQuery(value: Boolean) = put(Keys.USE_OSCQUERY, value)
    suspend fun setUseBroadcast(value: Boolean) = put(Keys.USE_BROADCAST, value)
    suspend fun setAutoConnect(value: Boolean) = put(Keys.AUTO_CONNECT, value)

    suspend fun setBridgeEnabled(value: Boolean) = put(Keys.BRIDGE_ENABLED, value)
    suspend fun setBridgePcHost(value: String) = put(Keys.BRIDGE_PC_HOST, value.trim())
    suspend fun setBridgePcPort(value: Int) = put(Keys.BRIDGE_PC_PORT, value.coerceIn(1, 65535))
    suspend fun setBridgeListenPort(value: Int) = put(Keys.BRIDGE_LISTEN_PORT, value.coerceIn(1, 65535))
    suspend fun setBridgeBlockPrefixes(value: String) = put(Keys.BRIDGE_BLOCK, value)
    suspend fun setBridgeRateLimitHz(value: Int) = put(Keys.BRIDGE_RATE_HZ, value.coerceIn(0, 240))
    suspend fun setBridgeRestrictToPcHost(value: Boolean) = put(Keys.BRIDGE_RESTRICT, value)

    suspend fun setLogFolderUri(value: String) = put(Keys.LOG_FOLDER_URI, value)
    suspend fun setLogAutoRefresh(value: Boolean) = put(Keys.LOG_AUTO_REFRESH, value)

    suspend fun setChatboxSilent(value: Boolean) = put(Keys.CHATBOX_SILENT, value)
    suspend fun setChatboxTyping(value: Boolean) = put(Keys.CHATBOX_TYPING, value)
    suspend fun setStatusEnabled(value: Boolean) = put(Keys.STATUS_ENABLED, value)
    suspend fun setStatusInterval(value: Int) = put(Keys.STATUS_INTERVAL, value.coerceIn(2, 120))
    suspend fun setStatusClock(value: Boolean) = put(Keys.STATUS_CLOCK, value)
    suspend fun setStatusBattery(value: Boolean) = put(Keys.STATUS_BATTERY, value)
    suspend fun setStatusHeartRate(value: Boolean) = put(Keys.STATUS_HR, value)
    suspend fun setStatusVrcl(value: Boolean) = put(Keys.STATUS_VRCL, value)
    suspend fun setStatusPrefix(value: String) = put(Keys.STATUS_PREFIX, value)

    suspend fun setPulsoidToken(value: String) = put(Keys.PULSOID_TOKEN, value.trim())
    suspend fun setHeartRateToParameters(value: Boolean) = put(Keys.HR_TO_PARAMS, value)
    suspend fun setHeartRateMax(value: Int) = put(Keys.HR_MAX, value.coerceIn(80, 260))

    suspend fun setVrclSession(token: String, displayName: String) {
        context.dataStore.edit {
            it[Keys.VRCL_TOKEN] = token
            it[Keys.VRCL_NAME] = displayName
        }
    }

    suspend fun clearVrclSession() {
        context.dataStore.edit {
            it.remove(Keys.VRCL_TOKEN)
            it.remove(Keys.VRCL_NAME)
        }
    }

    suspend fun setLastEyeHeight(value: Float) = put(Keys.LAST_EYE_HEIGHT, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
