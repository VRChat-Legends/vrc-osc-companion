# Privacy Policy

**VRC OSC Companion**
Last updated: 8 August 2026

VRC OSC Companion is published by VRChat Legends. This policy explains exactly what the app
touches. It is short because the app does very little.

## What the app does with your data

### Stored on your headset only

* Your OSC settings: target host, ports, discovery and background options.
* Your chatbox presets and rotating status lines.
* Community scripts you explicitly install. These are declarative presets, not executable code.
* Your last avatar eye-height value.
* Your VRChat Legends session token, if you choose to sign in.
* Your Pulsoid access token, if you choose to enter one.

All of this lives in the app's private storage on the device. It is excluded from Android
cloud backup and device transfer. Uninstalling the app deletes it.

Installed scripts can contain only chatbox text, waits, and values for writable parameters
reported by the current avatar through OSCQuery. The app revalidates a script before every
run. Scripts cannot access files, launch apps, run code or shell commands, choose raw OSC
paths, schedule themselves, or make network requests.

### Sent over your local network

The app sends and receives Open Sound Control (OSC) messages over UDP on your local
network, to and from VRChat. This traffic never leaves your network and is not recorded by
us. It contains whatever you choose to send: chatbox text, avatar parameter values, input
commands, avatar scale.

The app also advertises itself over mDNS on your local network so VRChat can discover it
automatically.

### Sent to VRChat Legends (only if you sign in)

Signing in is optional. Every OSC feature works without an account.

If you sign in, the app opens the VRChat Legends website in a browser tab. Your password is
entered on the website, never in this app, and the app never receives it. The website
returns a session token which the app stores locally and sends back to
`vrchatlegends.com` to read:

* your display name and roles,
* the public VRChat Legends event list.

You can sign out at any time from the Account screen, which deletes the token from the
device.

### Sent to Pulsoid (only if you provide a token)

If you enter a Pulsoid access token, the app opens a websocket to `dev.pulsoid.net` to
receive your live heart rate. The token is stored only on your headset and is sent only to
Pulsoid. Remove the token to stop this entirely.

## What the app does not do

* No analytics, telemetry, crash reporting or advertising SDKs.
* No location, camera, microphone, contacts or file access.
* No reading or writing of VRChat account credentials.
* No selling or sharing of personal data with third parties.
* No tracking across apps or websites.

## Permissions and why

| Permission | Reason |
| --- | --- |
| `INTERNET` | OSC over UDP, and the optional VRChat Legends and Pulsoid connections |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | detect the headset's own IP so OSC targets the right address |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS discovery so VRChat and the app find each other automatically |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK` | keep the OSC link alive while you are inside VRChat, with a visible ongoing notification |

## Children

The app is not directed at children under 13 and collects nothing from them.

## Changes

Material changes to this policy will be published in this file and reflected in the app's
store listing.

## Contact

Questions go to our Discord: <https://discord.gg/6xPkZ7Dxp9>
