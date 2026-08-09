# VRChat OSC reference

Everything VRC OSC Companion talks to, with the parts that are easy to get wrong called
out. Compiled from the official docs (`osc-overview`, `osc-as-input-controller`,
`osc-avatar-parameters`, `osc-avatar-scaling`, `osc-trackers`, `osc-eye-tracking`,
`oscquery`), the `vrchat-community/osc` wiki, and `vrchat/osccore`.

---

## Transport

| | |
| --- | --- |
| Protocol | OSC 1.0 over UDP, big-endian, 4-byte aligned |
| VRChat receives on | **9000** |
| VRChat sends on | **9001** |
| Override | launch arg `--osc=inPort:senderIP:outPort`, e.g. `--osc=9000:192.168.1.42:9001` |
| Enable | Action Menu → Options → **OSC → Enabled** |
| Debug view | Action Menu → OSC → **OSC Debug** (also force-enables OSC) |

VRChat's internal OSC library is the
[all-in-one branch of OscCore](https://github.com/vrchat/osccore/tree/all-in-one). It
trades OSC's "any combination of any type" flexibility for speed, so in practice only
`int`, `float`, `bool` and `string` round-trip.

### On Quest

VRChat on a standalone Quest binds the same ports. Because this app runs on the same
headset, either `127.0.0.1` or the headset's LAN IP reaches it. We default to the LAN IP:
it matches what the user sees in every other tool, it survives any future loopback
sandboxing, and the same field then also points at a PC without changing anything else.

Quest IPs move with DHCP, so the address is re-resolved on every connect rather than
being stored. A stale hardcoded IP fails completely silently with UDP.

---

## OSCQuery (the part most apps skip)

VRChat implements the [OSCQuery proposal](https://github.com/Vidvox/OSCQueryProposal) as
of **2023.3.1**. This removes all port guessing.

* VRChat advertises mDNS `_oscjson._tcp` under a name like `VRChat-Client-A1B2C3`.
* `GET http://host:port/` returns the whole node tree, including every avatar parameter
  on the currently worn avatar, with `TYPE`, `VALUE` and `ACCESS`.
* `GET http://host:port/?HOST_INFO` returns `{"NAME","OSC_IP","OSC_PORT","OSC_TRANSPORT"}`
  which is the UDP endpoint to send to.
* Your app advertises **both** `_oscjson._tcp` (your HTTP port) and `_osc._udp` (your UDP
  receive port). VRChat finds them and starts streaming to you.

`ACCESS` is a bitmask: `1` read, `2` write, `3` read/write. A parameter with only read
access will ignore anything you send it.

Reference implementation: [vrc-oscquery-lib](https://github.com/vrchat-community/vrc-oscquery-lib).

---

## Chatbox

```
/chatbox/input   s b n
/chatbox/typing  b
```

* `s` text. **144 characters maximum, 9 lines maximum**, and word wrap counts toward the
  line budget.
* `b` `true` sends immediately, `false` opens the in-game keyboard pre-filled.
* `n` **optional third argument**, defaults to `true`. Setting it `false` suppresses the
  notification SFX. Most implementations omit it and then spam everyone nearby.

Practical notes:

* There is no documented rate limit, but the client throttles. Roughly **1.5 s** between
  sends is the safe floor; this app coalesces bursts down to that.
* Braille (U+2800 plus a dot bitmask) gives 2x4 pixels per character and never triggers
  word wrap, which is why it is the standard trick for chatbox text art. With `R` rows the
  usable column count is `(144 - R + 1) / R`.
* `/chatbox/typing true` stays on until you explicitly send `false`.

---

## Avatar

```
/avatar/change                     s   (avatar id, sent by VRChat on load)
/avatar/parameters/<Name>          i|f|T|F
```

* Spaces in a parameter name become **underscores** in the address.
* Auto-generated per-avatar configs live at
  `%LOCALAPPDATA%Low\VRChat\VRChat\OSC\usr_*\Avatars\avtr_*.json` on PC. A parameter is
  writable only if it has an `input` block; output-only entries are read-only.
* VRCFury-built avatars often carry a `VF###_` build number in face-tracking parameter
  names, and that number **changes on every rebuild**, so those addresses go stale
  silently. Prefer stable names.
* Configs are not written for Build & Test avatars, only published ones.

Stock parameters worth knowing: `VRCEmote`, `VRCFaceBlendH/V`, `Viseme`, `Voice`,
`GestureLeft/Right(+Weight)`, `Velocity X/Y/Z`, `VelocityMagnitude`, `AngularY`,
`Upright`, `Grounded`, `Seated`, `AFK`, `MuteSelf`, `InStation`, `Earmuffs`,
`TrackingType`, `VRMode`, `IsLocal`, `ScaleFactor`, `ScaleFactorInverse`,
`EyeHeightAsMeters`, `EyeHeightAsPercent`.

---

## Avatar scaling

```
/avatar/eyeheight                  f   read + WRITE, metres
/avatar/eyeheightmin               f   read
/avatar/eyeheightmax               f   read
/avatar/eyeheightscalingallowed    T|F read
```

This is the single most commonly missed endpoint. The built-in avatar **parameters**
(`ScaleFactor`, `EyeHeightAsMeters`) are read-only, and creators.vrchat.com says so, which
leads people to conclude scaling over OSC is impossible. It is not: the top-level
`/avatar/eyeheight` **endpoint** is separate and writable.

* Accepted range 0.01 m to 10,000 m.
* VRChat officially supports 0.1 m to 100 m and shows a HUD warning outside that.
* `eyeheightmin`/`max` describe the *menu* range set by Udon, not the OSC range.
* When `eyeheightscalingallowed` is false, writes are silently ignored.
* If Udon enforces a scale, you receive your requested value first, then the enforced one.

---

## Input controller

Addresses are `/input/<Name>`.

**Axes** take a float in `-1..1` and **must be reset to 0**, otherwise a `MoveForward` left
at `1` walks you forever.

`Vertical` `Horizontal` `LookHorizontal` `UseAxisRight` `GrabAxisRight` `MoveHoldFB`
`SpinHoldCwCcw` `SpinHoldUD` `SpinHoldLR`

**Buttons** take int `1` pressed and `0` released. Sending `1` twice without a `0` between
only registers once.

`MoveForward` `MoveBackward` `MoveLeft` `MoveRight` `LookLeft` `LookRight` `Jump` `Run`
`ComfortLeft` `ComfortRight` `DropRight` `UseRight` `GrabRight` `DropLeft` `UseLeft`
`GrabLeft` `PanicButton` `QuickMenuToggleLeft` `QuickMenuToggleRight` `Voice`

`/input/Voice` behaviour depends on the client setting:

* **Toggle Voice on**: 0 → 1 toggles mute, then set it back to 0. While it is held at 1 you
  cannot use your controller or keyboard to (un)mute.
* **Toggle Voice off**: acts as push-to-mute, 0 muted and 1 unmuted.

`ComfortLeft/Right`, `Grab*`, `Use*`, `Drop*` are VR only.

---

## Trackers

```
/tracking/trackers/{1..8}/position   f f f
/tracking/trackers/{1..8}/rotation   f f f
/tracking/trackers/head/position     f f f
/tracking/trackers/head/rotation     f f f
```

* Unity coordinates, **left-handed**, +y up, `1.0 = 1 metre`.
* Euler degrees applied in **Z, X, Y** order, so the quaternion is `Ry * Rx * Rz`.
* Up to 8 trackers: hip, chest, 2 feet, 2 knees, 2 elbows. Sending fewer is often better;
  VRChat's IK compensates for tracking error better with less data.
* `head/position` realigns VRChat's **entire tracking space every frame with no smoothing**,
  so any jitter in your source shakes everything.
* `head/rotation` lerps yaw when streamed. A **single** message with more than a 300 ms gap
  performs a one-time instant yaw snap instead. That threshold is the useful trick.
* 10 second data timeout.
* Set the tracker display model to "Tracker: System" in Tracking & IK to keep the models
  visible after calibration while debugging.

---

## Eye tracking

```
/tracking/eye/EyesClosedAmount     f    0..1, drives both eyes
/tracking/eye/CenterPitchYaw       f f
/tracking/eye/CenterPitchYawDist   f f f
/tracking/eye/CenterVec            f f f    normalised, HMD local
/tracking/eye/CenterVecFull        f f f    length sets convergence distance
/tracking/eye/LeftRightPitchYaw    f f f f
/tracking/eye/LeftRightVec         f f f f f f
```

* **Addresses are case sensitive.**
* Send **one** eye-look address only, plus `EyesClosedAmount`.
* Positive pitch looks down, positive yaw looks right. Vectors are Unity style: +x right,
  +y up, +z forward.
* 10 second timeout, tracked separately for eyelids and eye-look, after which VRChat
  reverts to auto look and auto blink.

---

## Gotchas collected the hard way

* UDP has no handshake. A wrong IP, a wrong port, or OSC disabled in VRChat all look
  identical: nothing happens, no error. **Inbound traffic is the only real proof of a
  working link**, which is why the Home screen keys "connected" off received packets.
* Two sockets cannot both bind `9001`. On Android the second one receives nothing rather
  than failing loudly, so never let a service and an activity both open a receiver.
* Bundles (`#bundle`) can nest. A decoder that only handles flat messages will silently
  drop everything VRChat batches.
* `/avatar/change` invalidates every cached parameter value. The schema survives, the
  values do not.
* A `0.0` visibility or a `false` bool is a legitimate value. Guard with `!= null`, never
  with a truthiness check.
