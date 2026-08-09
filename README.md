# VRC OSC Companion

A standalone **Meta Quest 2 / 3 / 3S** companion app for VRChat, built by
[VRChat Legends](https://vrchatlegends.com).

It runs as a 2D panel next to VRChat on the headset itself and speaks
[VRChat's OSC protocol](https://docs.vrchat.com/docs/osc-overview) over UDP, so you get a
MagicChatbox-class toolkit without needing a PC.

Native **Kotlin + Jetpack Compose**. No WebView, no game engine, no PC bridge.

---

## What it does

| Module | Detail |
| --- | --- |
| **Chatbox** | Live typing indicator, instant send, saved presets, rotating/marquee status lines, 144-char + 9-line budget meter |
| **Avatar parameters** | Auto-discovered from VRChat over **OSCQuery**, rendered as live toggles / sliders / steppers |
| **Input controller** | Movement, look, jump, run, voice/mute, quick menu, grab & drop, emotes, safe mode |
| **Avatar scaling** | `/avatar/eyeheight` slider plus saved height presets, respects world limits |
| **Status auto-chatbox** | Clock, headset battery, controller battery, network, uptime, VRCL profile, composed into one rotating line |
| **Heart rate** | Pulsoid websocket to chatbox text and to avatar parameters (`HR`, `HRPercent`, `isHRConnected`, `onesHR`/`tensHR`/`hundredsHR`) |
| **Monitor** | Raw bidirectional OSC feed with address filtering, for debugging |
| **VRChat Legends** | Optional sign-in with the same account as the website. Everything works signed out |

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
  net/         Quest LAN IP / broadcast detection
  data/        DataStore settings + chatbox presets
  vrcl/        VRChat Legends API client and Custom Tab OAuth
  pulsoid/     Heart rate websocket
  status/      MagicChatbox-style status line composer
  service/     Foreground service keeping the OSC link alive behind VRChat
  ui/          Compose screens and theme
docs/
  OSC-REFERENCE.md      every VRChat OSC address, including the undocumented corners
  QUEST-BUILD.md        build, sideload, SideQuest and Meta Horizon Store submission
  VRCL-INTEGRATION.md   how sign-in works against the VRChat Legends API
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
