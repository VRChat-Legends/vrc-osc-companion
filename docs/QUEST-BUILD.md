# Build, sideload and publish

## Prerequisites

| | |
| --- | --- |
| JDK | 17 or newer (JDK 21 is fine, the project targets bytecode 17) |
| Android SDK | API 34 platform + build tools 34.x + platform-tools |
| Gradle | provided by the wrapper, 8.10.2 |

The machine currently has **no Android SDK installed**. Easiest fix is Android Studio
(bundles the SDK, the emulator and a working `adb`):

<https://developer.android.com/studio>

Command-line only alternative:

1. Download the command line tools from the same page.
2. Extract to `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`.
3. ```
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
4. Create `local.properties` in the repo root:
   ```
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```

## Build

```powershell
.\gradlew.bat assembleDebug
```

Output: `app\build\outputs\apk\debug\app-debug.apk`

```powershell
.\gradlew.bat assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk`

## Release signing

Android will only *update* an app when the new APK is signed with the same key. Create one
keystore before the first public build and never lose it: a different key means no updates
and no store listing.

```powershell
keytool -genkeypair -v `
  -keystore vrc-osc-companion.jks `
  -alias vrcoscc `
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the repo root (already gitignored):

```
storeFile=../vrc-osc-companion.jks
storePassword=...
keyAlias=vrcoscc
keyPassword=...
```

`app/build.gradle.kts` picks it up automatically. If the file is absent, release builds are
simply unsigned rather than failing.

> Back the `.jks` and its passwords up somewhere that is not this machine.

## Install on a Quest

1. Enable Developer Mode for the headset in the Meta Horizon phone app
   (Devices → your headset → Developer Mode).
2. Plug in over USB-C and accept the "Allow USB debugging" prompt in the headset.
3. ```powershell
   adb devices
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

Or drag the APK onto SideQuest with the headset connected.

The app appears in the Quest library under **Unknown Sources** (Apps → dropdown → Unknown
Sources) until it is published.

## Running it next to VRChat

It is a 2D panel app, so it multitasks with VRChat:

1. Launch VRChat.
2. Press the Meta button, open the app from Unknown Sources. It docks as a floating panel.
3. In VRChat: Action Menu → Options → **OSC → Enabled**.
4. The companion's Home screen flips to "VRChat is talking to us" as soon as packets arrive.

Keep **Stay alive in the background** on in Settings. Horizon OS freezes backgrounded 2D
apps, which kills the socket without any warning.

## Debugging

```powershell
adb logcat -s VrcOscCompanion:V AndroidRuntime:E
```

Watch the actual UDP traffic from a PC on the same network:

```powershell
# Protokol (hexler.net/protokol) is the easiest OSC receiver
```

Inside VRChat, Action Menu → OSC → **OSC Debug** shows every message VRChat receives, with
addresses and values. If a message is not there, it never arrived.

## Meta Horizon Store submission

Checklist for a 2D app listing:

* **Developer account** at <https://developers.meta.com/horizon/> with an organisation
  created and payout/tax details filled in.
* **Signed APK or AAB**, `versionCode` incremented on every upload.
* **App ID** from the developer dashboard; a 2D app does not need entitlement checks but
  the listing still requires the ID.
* **Data Use Checkup (DUC)**: declare exactly what the app touches. For this app that is
  *User ID* and *Usage data* only when a user chooses to sign in, plus *Storage* for local
  settings. Nothing else.
* **Privacy policy URL** that is publicly reachable: point it at the raw `PRIVACY.md` in
  this repo or a page on vrchatlegends.com.
* **Store art**:
  * icon 512x512 PNG, no transparency
  * cover 2560x1440 PNG
  * hero 3000x900 PNG (10:3)
  * at least 5 screenshots, 2560x1440 or matching the panel aspect
* **Age rating** questionnaire (IARC).
* **Release channel**: start in **Early Access / App Lab** to iterate without full store
  review, then promote.

The vector launcher icon in `res/drawable/ic_launcher_foreground.xml` covers the on-device
icon. The store still needs the flat PNGs above, generated separately.

## SideQuest

SideQuest accepts any signed APK. Submitting there is much lighter than the Meta store:
create an app entry, upload the APK, add screenshots and a description. It is the right
first distribution channel while the app is still moving fast.

## In-app updates

`update.json` in the repo root is the manifest format. Host it somewhere public (a GitHub
raw URL works) and point `UPDATE_MANIFEST_URL` in `app/build.gradle.kts` at it. Bump
`versionCode` in both the manifest and the Gradle file for every release. Android never
silently installs a sideloaded APK, so the user always gets one confirmation tap.
