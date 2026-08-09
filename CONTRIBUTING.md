# Contributing

## Getting set up

See [docs/QUEST-BUILD.md](docs/QUEST-BUILD.md) for the toolchain. Short version: JDK 17+,
Android SDK 34, then `.\gradlew.bat assembleDebug`.

## Layout

Package `com.vrchatlegends.osccompanion`:

| Package | Responsibility |
| --- | --- |
| `osc` | OSC 1.0 codec, UDP transport, VRChat address catalog, the single shared repository |
| `oscquery` | mDNS discovery of VRChat plus our own advertised OSCQuery HTTP server |
| `net` | headset IP and broadcast address detection |
| `data` | DataStore-backed settings and presets |
| `vrcl` | VRChat Legends API client and Custom Tab OAuth |
| `pulsoid` | heart rate websocket |
| `status` | status line composition |
| `service` | foreground service that keeps the socket alive |
| `ui` | Compose screens, theme, view model |

`OscRepository` is a process singleton. There must never be a second UDP socket bound to
the receive port: on Android the second bind succeeds and then silently receives nothing.

## House rules

* No em dashes or en dashes in code comments, docs or UI copy.
* Comments explain why, not what. One line is usually enough.
* Anything user-facing has to be readable at Quest panel distance, so nothing below 15sp.
* New OSC addresses go in `VrcOsc.kt` with a short note about any non-obvious behaviour,
  and get mirrored into [docs/OSC-REFERENCE.md](docs/OSC-REFERENCE.md).
* Never assume a VRChat OSC address works because it looks reasonable. Check it against the
  live docs and against the OSC Debug view in the headset.

## Testing OSC changes without a headset

The codec is plain Kotlin with no Android dependencies, so `OscCodec` round-trips can be
unit tested on the JVM. Anything touching sockets, NSD or Compose needs a device.
