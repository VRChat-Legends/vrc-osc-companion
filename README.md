# VRC OSC Companion

A standalone **Meta Quest 2 / 3 / 3S** companion app for VRChat, built by
[VRChat Legends](https://vrchatlegends.com).

It runs as a 2D panel next to VRChat on the headset itself and speaks
[VRChat's OSC protocol](https://docs.vrchat.com/docs/osc-overview) over UDP, so you get a
MagicChatbox-class toolkit without needing a PC.

Native **Kotlin + Jetpack Compose**. No WebView, no game engine.

---

## What it does

| Module | Detail |
| --- | --- |
| **Chatbox** | Live typing indicator, instant send, saved presets, rotating/marquee status lines, 144-char + 9-line budget meter |
| **Avatar parameters** | Auto-discovered from VRChat over **OSCQuery**, rendered as live toggles / sliders / steppers |
| **Input controller** | Movement, look, jump, run, voice/mute, quick menu, grab & drop, emotes, safe mode |
| **Avatar scaling** | `/avatar/eyeheight` slider plus saved height presets, respects world limits |
| **Status auto-chatbox** | Clock, headset battery, network, uptime, VRCL profile, composed into one rotating line |
| **Heart rate** | Pulsoid websocket to chatbox text and to avatar parameters (`HR`, `HRPercent`, `isHRConnected`, `onesHR`/`tensHR`/`hundredsHR`) |
| **PC Link** | Two way OSC bridge between VRChat on the headset and an app on your PC. See below |
| **VRChat logs** | Reads VRChat's own log off the headset: world changes, joins and leaves, errors, raw tail |
| **Monitor** | Raw bidirectional OSC feed with address filtering, for debugging |
| **VRChat Tools** | Coming soon. VRChat account sign-in and API features |
| **Community** | Social feed, comments, follows, events, leaderboards, and sandboxed shared scripts |
| **VRChat Legends** | Optional sign-in with the same account as the website. Everything works signed out |

## Community scripts

Community scripts are deliberately not executable code. Installing one copies a strict JSON
preset into the app-private `files/scripts` folder after showing every effect for review. A
script can contain only chatbox lines, waits, and writes to parameters that the current avatar
reports as writable through OSCQuery.

Scripts never auto-run. The user must tap **Run once**, and can stop the active script at any
time. The runner reloads and revalidates the stored file before every run, permits only one run
at a time, enforces pacing and a two-minute ceiling, and stops if VRChat disconnects or the
avatar changes. Scripts cannot run code or shell commands, access files, launch Android intents,
select raw OSC paths, control movement inputs, schedule themselves, or make network requests.

## Background running

Horizon OS suspends a 2D panel the moment you drop into VRChat, which would kill the
socket mid session. Connecting therefore always starts a foreground service, and that
notification is what keeps OSC alive. This is not optional and there is no toggle for it.

## Meta Developer Mode

Most of the app works without it. These do not:

* Now playing in your chatbox
* Reading VRChat's log (see below)
* Sideloading a build that did not come from the store

Turn it on in the Meta Horizon phone app: **Menu > Devices > your headset > Headset
settings > Developer Mode**. A free verified developer organisation on your Meta account
is required first.

## VRChat logs on Quest

Verified on a Quest 3S: VRChat's package is `com.vrchat.oculus.quest` and it writes **no
log file at all**. Nothing resembling `output_log*.txt` exists anywhere under its files
directory. The Unity output goes to **logcat**.

Android only lets one app read another app's logcat with `android.permission.READ_LOGS`,
which has the `development` protection flag. That cannot be granted by tapping a dialog,
it has to come over adb, which is exactly why Developer Mode is required. Run this once
with the headset plugged into a PC:

```bash
adb shell pm grant com.vrchatlegends.osccompanion android.permission.READ_LOGS
```

The grant survives reboots but not a reinstall. For sideloaded debug builds the package is
`com.vrchatlegends.osccompanion.debug`.

Without the grant the Logs tab still opens, but logcat will only return this app's own
lines. The file reader remains as a fallback for logs copied onto the headset or pulled
from desktop VRChat.

## PC Link

VRChat on a Quest only ever sends OSC to `127.0.0.1`, so there is no supported way to make
a PC the destination. This app runs on the headset, holds that loopback socket, and relays
both directions:

```
VRChat  ->  127.0.0.1:9001  (this app)  ->  yourPc:9001
your PC ->  questIp:9100    (this app)  ->  VRChat
```

So a contact receiver poke on your avatar reaches your PC, your PC decides what to do, and
the response goes back into VRChat. The uplink is byte identical OSC, so existing desktop
tools work unmodified once pointed at the headset.

A dependency free reference client lives at
[tools/pc_bridge_demo.py](tools/pc_bridge_demo.py). Full design notes, traffic shaping and
security model: [docs/PC-BRIDGE.md](docs/PC-BRIDGE.md).

## Networking model

The app sends OSC to **the headset's own LAN IP by default** (auto-detected on launch, e.g.
`192.168.1.42:9000`) and listens on `9001`. `127.0.0.1` is offered as a fallback, and any
manual host/port is accepted for driving a PC copy of VRChat from the headset.

On top of that it runs a full **OSCQuery** peer:

* Discovers VRChat's `_oscjson._tcp` service via mDNS and pulls the live parameter tree.
* Advertises its own `_osc._udp` + `_oscjson._tcp` services so VRChat routes output to it
  without the user touching a port field.

Legacy `9000`/`9001` still works if OSCQuery is unavailable.

> VRChat side: **Action Menu → Options → OSC → Enabled**. Nothing else to configure.

## Repo layout

```
app/src/main/java/com/vrchatlegends/osccompanion/
  osc/         OSC 1.0 codec, UDP transport, VRChat address catalog, shared repository
  oscquery/    mDNS discovery + embedded OSCQuery HTTP server
  bridge/      two way PC relay for VRChat traffic that cannot leave the headset
  logs/        VRChat log discovery, tailing and parsing
  net/         Quest LAN IP / broadcast detection
  data/        DataStore settings + chatbox presets
  scripts/     strict policy, private JSON store and one-shot sandboxed runner
  vrcl/        VRChat Legends API client and Custom Tab OAuth
  pulsoid/     Heart rate websocket
  status/      MagicChatbox-style status line composer
  service/     Foreground service keeping the OSC link alive behind VRChat
  ui/          Compose screens and theme
docs/
  OSC-REFERENCE.md      every VRChat OSC address, including the undocumented corners
  PC-BRIDGE.md          how the headset relays OSC to and from a PC
  QUEST-BUILD.md        build, sideload, SideQuest and Meta Horizon Store submission
  VRCL-INTEGRATION.md   how sign-in works against the VRChat Legends API
tools/
  pc_bridge_demo.py     reference desktop client for the PC link
```

## Build

Requires JDK 17 and the Android SDK (API 34).

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew assembleRelease        # signed release, see docs/QUEST-BUILD.md
```

Sideload:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Full instructions, keystore setup and store-submission checklist: [docs/QUEST-BUILD.md](docs/QUEST-BUILD.md).

## Links

* Discord: https://discord.gg/6xPkZ7Dxp9
* Website: https://vrchatlegends.com

## Credits

Feature inspiration from [MagicChatbox](https://github.com/BoiHanny/vrcosc-magicchatbox)
(PC) and VRC-NEXUS (Quest). No code from either project is used here.
