package com.vrchatlegends.osccompanion.osc

/**
 * Every VRChat OSC address this app knows about, including the corners that are easy to
 * miss in the docs (avatar scaling, tracker/eye endpoints, the third chatbox argument).
 *
 * Sources: docs.vrchat.com osc-as-input-controller, osc-avatar-parameters,
 * osc-avatar-scaling, osc-trackers, osc-eye-tracking, oscquery.
 */
object VrcOsc {

    /** VRChat listens here. */
    const val DEFAULT_SEND_PORT = 9000

    /** VRChat sends here when OSCQuery is not negotiating a port. */
    const val DEFAULT_RECEIVE_PORT = 9001

    // ── Chatbox ────────────────────────────────────────────────────────────────
    /**
     * `/chatbox/input s b n`
     *  - s: text, hard limit 144 characters including newlines
     *  - b: true sends immediately, false opens the keyboard pre-filled
     *  - n: false suppresses the notification SFX (defaults true when omitted, and the
     *       third argument is the one most implementations forget to send)
     */
    const val CHATBOX_INPUT = "/chatbox/input"
    const val CHATBOX_TYPING = "/chatbox/typing"

    const val CHATBOX_MAX_CHARS = 144
    const val CHATBOX_MAX_LINES = 9

    // ── Avatar ─────────────────────────────────────────────────────────────────
    const val AVATAR_CHANGE = "/avatar/change"
    const val AVATAR_PARAMETER_PREFIX = "/avatar/parameters/"

    /**
     * Avatar scaling. `/avatar/eyeheight` is writable in metres even though the built-in
     * avatar *parameters* (ScaleFactor, EyeHeightAsMeters) are read-only.
     * Accepted range 0.01 .. 10000; VRChat only supports 0.1 .. 100 without a HUD warning.
     */
    const val AVATAR_EYE_HEIGHT = "/avatar/eyeheight"
    const val AVATAR_EYE_HEIGHT_MIN = "/avatar/eyeheightmin"
    const val AVATAR_EYE_HEIGHT_MAX = "/avatar/eyeheightmax"
    const val AVATAR_EYE_HEIGHT_SCALING_ALLOWED = "/avatar/eyeheightscalingallowed"

    const val EYE_HEIGHT_SUPPORTED_MIN = 0.1f
    const val EYE_HEIGHT_SUPPORTED_MAX = 100f

    // ── Tracking ───────────────────────────────────────────────────────────────
    fun trackerPosition(index: Int) = "/tracking/trackers/$index/position"
    fun trackerRotation(index: Int) = "/tracking/trackers/$index/rotation"
    const val TRACKER_HEAD_POSITION = "/tracking/trackers/head/position"
    const val TRACKER_HEAD_ROTATION = "/tracking/trackers/head/rotation"

    const val EYE_CLOSED_AMOUNT = "/tracking/eye/EyesClosedAmount"
    const val EYE_CENTER_PITCH_YAW = "/tracking/eye/CenterPitchYaw"
    const val EYE_CENTER_PITCH_YAW_DIST = "/tracking/eye/CenterPitchYawDist"
    const val EYE_CENTER_VEC = "/tracking/eye/CenterVec"
    const val EYE_CENTER_VEC_FULL = "/tracking/eye/CenterVecFull"
    const val EYE_LEFT_RIGHT_PITCH_YAW = "/tracking/eye/LeftRightPitchYaw"
    const val EYE_LEFT_RIGHT_VEC = "/tracking/eye/LeftRightVec"

    fun parameter(name: String) = AVATAR_PARAMETER_PREFIX + sanitizeParameterName(name)

    /** VRChat turns spaces in parameter names into underscores when building addresses. */
    fun sanitizeParameterName(name: String) = name.replace(' ', '_')

    // ── Inputs ─────────────────────────────────────────────────────────────────

    enum class InputKind { AXIS, BUTTON }

    data class InputControl(
        val address: String,
        val label: String,
        val kind: InputKind,
        val group: String,
        val vrOnly: Boolean = false,
        val note: String? = null,
    )

    /**
     * Axes take a float in -1..1 and must be reset to 0. Buttons take int 1 then int 0;
     * sending 1 twice without a 0 in between only registers once.
     */
    val AXES: List<InputControl> = listOf(
        InputControl("/input/Vertical", "Move forward / back", InputKind.AXIS, "Movement"),
        InputControl("/input/Horizontal", "Strafe right / left", InputKind.AXIS, "Movement"),
        InputControl("/input/LookHorizontal", "Look right / left", InputKind.AXIS, "Movement", note = "Snap turns in VR when Comfort Turning is on"),
        InputControl("/input/UseAxisRight", "Use held item", InputKind.AXIS, "Held object"),
        InputControl("/input/GrabAxisRight", "Grab item", InputKind.AXIS, "Held object"),
        InputControl("/input/MoveHoldFB", "Push / pull held object", InputKind.AXIS, "Held object"),
        InputControl("/input/SpinHoldCwCcw", "Spin held object CW / CCW", InputKind.AXIS, "Held object"),
        InputControl("/input/SpinHoldUD", "Spin held object up / down", InputKind.AXIS, "Held object"),
        InputControl("/input/SpinHoldLR", "Spin held object left / right", InputKind.AXIS, "Held object"),
    )

    val BUTTONS: List<InputControl> = listOf(
        InputControl("/input/MoveForward", "Forward", InputKind.BUTTON, "Movement"),
        InputControl("/input/MoveBackward", "Back", InputKind.BUTTON, "Movement"),
        InputControl("/input/MoveLeft", "Left", InputKind.BUTTON, "Movement"),
        InputControl("/input/MoveRight", "Right", InputKind.BUTTON, "Movement"),
        InputControl("/input/LookLeft", "Look left", InputKind.BUTTON, "Movement"),
        InputControl("/input/LookRight", "Look right", InputKind.BUTTON, "Movement"),
        InputControl("/input/Jump", "Jump", InputKind.BUTTON, "Movement", note = "World must allow jumping"),
        InputControl("/input/Run", "Run", InputKind.BUTTON, "Movement"),
        InputControl("/input/ComfortLeft", "Snap turn left", InputKind.BUTTON, "Movement", vrOnly = true),
        InputControl("/input/ComfortRight", "Snap turn right", InputKind.BUTTON, "Movement", vrOnly = true),

        InputControl("/input/GrabLeft", "Grab (left)", InputKind.BUTTON, "Hands", vrOnly = true),
        InputControl("/input/GrabRight", "Grab (right)", InputKind.BUTTON, "Hands", vrOnly = true),
        InputControl("/input/UseLeft", "Use (left)", InputKind.BUTTON, "Hands", vrOnly = true),
        InputControl("/input/UseRight", "Use (right)", InputKind.BUTTON, "Hands", vrOnly = true),
        InputControl("/input/DropLeft", "Drop (left)", InputKind.BUTTON, "Hands", vrOnly = true),
        InputControl("/input/DropRight", "Drop (right)", InputKind.BUTTON, "Hands", vrOnly = true),

        InputControl("/input/Voice", "Voice / mute", InputKind.BUTTON, "System", note = "Toggle if 'Toggle Voice' is on, otherwise push-to-mute"),
        InputControl("/input/QuickMenuToggleLeft", "Quick menu (left)", InputKind.BUTTON, "System"),
        InputControl("/input/QuickMenuToggleRight", "Quick menu (right)", InputKind.BUTTON, "System"),
        InputControl("/input/PanicButton", "Safe mode", InputKind.BUTTON, "System", note = "Turns on Safe Mode"),
    )

    /** Default VRChat emote wheel values for the stock `VRCEmote` parameter. */
    val EMOTES: List<Pair<Int, String>> = listOf(
        0 to "Stop",
        1 to "Wave",
        2 to "Clap",
        3 to "Point",
        4 to "Cheer",
        5 to "Dance",
        6 to "Backflip",
        7 to "Sad kick",
        8 to "Die",
    )

    /** VRChat's stock locomotion / emote parameters, always worth showing first. */
    val COMMON_PARAMETERS: List<String> = listOf(
        "VRCEmote",
        "VRCFaceBlendH",
        "VRCFaceBlendV",
        "IsLocal",
        "Viseme",
        "Voice",
        "GestureLeft",
        "GestureRight",
        "GestureLeftWeight",
        "GestureRightWeight",
        "AngularY",
        "VelocityX",
        "VelocityY",
        "VelocityZ",
        "VelocityMagnitude",
        "Upright",
        "Grounded",
        "Seated",
        "AFK",
        "TrackingType",
        "VRMode",
        "MuteSelf",
        "InStation",
        "Earmuffs",
        "IsOnFriendsList",
        "AvatarVersion",
        "ScaleModified",
        "ScaleFactor",
        "ScaleFactorInverse",
        "EyeHeightAsMeters",
        "EyeHeightAsPercent",
    )

    /** Heart-rate parameter names the community has standardised on. */
    object HeartRateParams {
        const val CONNECTED = "isHRConnected"
        const val ACTIVE = "isHRActive"
        const val BEAT = "isHRBeat"
        const val PERCENT = "HRPercent"
        const val RAW = "HR"
        const val ONES = "onesHR"
        const val TENS = "tensHR"
        const val HUNDREDS = "hundredsHR"
    }
}
