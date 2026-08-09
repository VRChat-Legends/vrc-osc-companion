# PC Bridge

Two way OSC between VRChat running on a Quest and an application on a PC.

## The problem

On PC, VRChat can be told where to send its OSC output with the `--osc=` launch argument,
so a remote machine can be the destination. On a standalone Quest none of that applies:

- VRChat sends its OSC output to `127.0.0.1:9001` and nothing else.
- There is no supported way to pass launch arguments to a store app on Horizon OS.
- OSCQuery lets VRChat *find* a service, but VRChat still only emits to loopback.

So the usual advice, "point VRChat at your PC's IP", has no equivalent on Quest. A PC can
send *into* VRChat on the headset without any help (VRChat binds `0.0.0.0:9000`, so
`questIp:9000` works), but the return path does not exist.

## The way around it

Put the listener inside the headset. VRC OSC Companion already runs on the Quest and
already binds `9001`, which is exactly the address VRChat is willing to talk to. Once it
holds that socket it can re-send everything over Wi-Fi.

```
uplink     VRChat  ->  127.0.0.1:9001  (companion)  ->  pcHost:9001
downlink   PC      ->  questIp:9100    (companion)  ->  VRChat on 127.0.0.1:9000
```

The uplink is byte identical OSC, so from the PC's point of view the headset looks like a
local VRChat install. Existing desktop OSC tools work unmodified as long as they are
pointed at the headset instead of localhost.

The downlink needs its own port because VRChat already owns `9000` on the headset. `9100`
is the default.

### Worked example

Someone pokes a contact receiver on your avatar. VRChat writes
`/avatar/parameters/HeadPat true` to `127.0.0.1:9001`. The companion receives it, forwards
it to `pcHost:9001`, your PC application reacts, and sends
`/avatar/parameters/HeadPatResponse 1` back to `questIp:9100`. The companion receives that
and writes it to VRChat on `127.0.0.1:9000`. Round trip complete, entirely over standard
OSC, with no modification to VRChat.

## Setup

1. Put the PC and the Quest on the same network.
2. In the app, open **PC Link**, enter the PC address, press Apply, then enable the bridge.
3. On the PC, listen on UDP `9001`.
4. Send OSC back to `questIp:9100`.

## Auto discovery

While the bridge is running it repeats `/vrcosc/bridge/hello` to the PC every 3 seconds
with three arguments:

| index | type   | meaning                       |
|-------|--------|-------------------------------|
| 0     | string | the headset's LAN IPv4        |
| 1     | int32  | the port to send replies to   |
| 2     | string | bridge protocol version       |

A desktop tool can watch for that message and configure itself, so the user only has to
type the PC address once, on the headset.

The app also advertises `_osc._udp` and `_oscjson._tcp` over mDNS as `VRC-OSC-Companion`,
so an OSCQuery aware PC tool can find the headset without any configuration at all.

## Traffic shaping

Wi-Fi is the weakest part of this chain, and VRChat emits tracking data every frame. Two
controls keep that off the link:

- **Blocked prefixes**: addresses starting with any of these are never forwarded.
  `/tracking/` is blocked by default.
- **Per address rate limit**: caps each individual address to N sends per second. `0`
  disables it.

Both live on the PC Link screen.

## Security

The downlink port accepts OSC that gets written straight into your live VRChat session, so
it is a real inbound surface.

- The bridge is **off by default** and will not start without a PC address.
- **Only accept from the PC above** is on by default and drops datagrams whose source
  address is not the configured PC.
- Messages whose OSC address does not begin with `/` are rejected before they reach
  VRChat.

UDP source addresses can be forged, so the host filter is a convenience control rather than
a guarantee. Run the bridge on networks you trust, and turn it off when you are done.

## Reference client

`tools/pc_bridge_demo.py` is a dependency free Python script that listens for the uplink,
prints what VRChat is doing, auto discovers the headset from the hello message, and can
send messages back. Use it to confirm the link before wiring up anything larger.

```
python tools/pc_bridge_demo.py
python tools/pc_bridge_demo.py --send /chatbox/input "hello from the PC" true false
```
