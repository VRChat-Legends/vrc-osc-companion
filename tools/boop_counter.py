#!/usr/bin/env python3
"""
Boop counter for VRChat on Quest.

Someone boops the contact on your avatar, VRChat sends the parameter to the companion
app on the headset, the app forwards it here over Wi-Fi, this script counts it, writes the
running total to a JSON file on your PC, and sends the total straight back to your VRChat
chatbox through the same bridge.

    VRChat -> 127.0.0.1:9001 (headset app) -> this PC :9001
    this PC -> headset :9100 (app) -> VRChat /chatbox/input

Requires nothing but Python 3.8+. Start the PC Link tab in the companion app first, point
it at this machine, and leave this running.

    python boop_counter.py
    python boop_counter.py --template "Boops: {total}"
    python boop_counter.py --params OSCBoop --no-chatbox
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import socket
import sys
import tempfile
import time
from datetime import date, datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pc_bridge_demo import (  # noqa: E402
    DEFAULT_LISTEN_PORT,
    DEFAULT_QUEST_PORT,
    HELLO_ADDRESS,
    BridgeClient,
    decode,
)

PARAM_PREFIX = "/avatar/parameters/"
CHATBOX_ADDRESS = "/chatbox/input"

# VRChat throttles the chatbox. Anything faster than this is dropped, so bursts coalesce.
CHATBOX_MIN_INTERVAL = 1.6

# Hard limits from the VRChat OSC docs: 144 characters counting newlines, 9 lines, and
# word wrap counts against the line budget. Going over gets the whole message rejected.
CHATBOX_MAX_CHARS = 144
CHATBOX_MAX_LINES = 9

# A physical boop bounces the collider a few times, and an avatar may expose the same
# event under more than one parameter. Both collapse into one count.
DEFAULT_DEBOUNCE = 0.35

# Avatars found on this account. OSCBoop is the plain OSC flag, VF106_Booped is the
# VRCFury contact receiver. Watching both means it works whichever one the avatar drives.
DEFAULT_PARAMS = ["OSCBoop", "VF106_Booped"]


# ── Meme numbers ───────────────────────────────────────────────────────────────

# Landing exactly on one of these is the whole joke, so the chatbox calls it out.
# Keep the labels short: they share a 144 character budget with everything else.
MEME_NUMBERS = {
    1: "FIRST BOOP",
    7: "LUCKY SEVEN",
    13: "UNLUCKY 13",
    21: "BLACKJACK",
    23: "HIS AIRNESS",
    24: "MAMBA",
    42: "THE ANSWER",            # Hitchhiker's Guide to the Galaxy
    50: "HALF CENTURY",
    64: "NINTENDO 64",
    69: "NICE",
    73: "THE BEST NUMBER",       # Sheldon Cooper, 21st prime, 7x3
    88: "GREAT SCOTT",           # 88mph, Back to the Future
    99: "ONE OFF",
    100: "CENTURY",
    101: "BOOPS 101",
    123: "EASY AS 123",
    137: "FINE STRUCTURE",       # the physics constant, roughly 1/137
    143: "I LOVE YOU",           # pager code, letters per word
    200: "HTTP OK",
    250: "QUARTER K",
    256: "ONE BYTE OVER",        # 2^8
    300: "THIS IS SPARTA",
    314: "PI",
    360: "NO SCOPE",
    365: "A WHOLE YEAR",
    404: "BOOP NOT FOUND",
    418: "I AM A TEAPOT",        # HTTP 418, the April Fools RFC
    420: "BLAZE IT",
    451: "FAHRENHEIT",           # Bradbury, and HTTP 451
    500: "HALF A THOUSAND",
    512: "2 TO THE 9",
    555: "FAKE PHONE NUMBER",    # the reserved Hollywood prefix
    616: "THE OTHER BEAST",      # earliest manuscripts say 616, not 666
    666: "THE BEAST",
    727: "W Y S I",               # osu!, Cookiezi's 727pp Blue Zenith play
    777: "JACKPOT",
    888: "TRIPLE FORTUNE",       # lucky in Chinese numerology
    911: "EMERGENCY",
    999: "ONE OFF AGAIN",
    1000: "ONE THOUSAND",
    1024: "ONE KILOBOOP",        # 2^10
    1234: "STAIRCASE",
    1337: "L E E T",
    1701: "NCC-1701",            # USS Enterprise
    1729: "TAXICAB",             # Ramanujan's number
    2000: "Y2K",
    2048: "2048",
    3141: "PI AGAIN",
    4096: "2 TO THE 12",
    4200: "BLAZE IT x10",
    5000: "FIVE THOUSAND",
    6969: "DOUBLE NICE",
    8008: "CALCULATOR CLASSIC",  # the one everyone typed upside down at school
    9000: "IT IS OVER 9000",     # Dragon Ball Z, the line is 9000 not 9001
    9001: "ACTUALLY OVER 9000",
    10000: "TEN THOUSAND",
    12345: "LUGGAGE COMBO",      # Spaceballs
    31337: "ELEET",
    65535: "MAX UINT16",
    69420: "PEAK NUMBER",
    80085: "CALCULATOR CLASSIC",
    100000: "SIX FIGURES",
}


def meme_label(n: int) -> str | None:
    """The joke for this exact number, or a pattern that is funny on its own."""
    if n in MEME_NUMBERS:
        return MEME_NUMBERS[n]
    text = str(n)
    if len(text) >= 3:
        if len(set(text)) == 1:
            return f"ALL {text[0]}s"
        if text == text[::-1]:
            return "PALINDROME"
    if n >= 1000 and n % 1000 == 0:
        return "MILESTONE"
    return None


def next_meme(n: int) -> int | None:
    """The next exact meme number above n, for the teaser line."""
    later = [k for k in MEME_NUMBERS if k > n]
    return min(later) if later else None


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class BoopLog:
    """Running totals, persisted as JSON after every boop."""

    def __init__(self, path: str):
        self.path = os.path.abspath(path)
        self.data = self._load()

    def _load(self) -> dict:
        try:
            with open(self.path, "r", encoding="utf-8") as fh:
                loaded = json.load(fh)
        except (OSError, ValueError):
            loaded = {}
        return {
            "total": int(loaded.get("total", 0)),
            "byDay": dict(loaded.get("byDay", {})),
            "byParameter": dict(loaded.get("byParameter", {})),
            "firstBoopAt": loaded.get("firstBoopAt"),
            "lastBoopAt": loaded.get("lastBoopAt"),
            "sessionStartedAt": now_iso(),
            "sessionBoops": 0,
        }

    def _save(self) -> None:
        # Write to a sibling temp file and replace, so a crash mid-write cannot leave a
        # half-written file where the running total used to be.
        directory = os.path.dirname(self.path) or "."
        os.makedirs(directory, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=directory, prefix=".boop-", suffix=".tmp")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as fh:
                json.dump(self.data, fh, indent=2)
                fh.write("\n")
            os.replace(tmp, self.path)
        except BaseException:
            try:
                os.unlink(tmp)
            except OSError:
                pass
            raise

    def record(self, parameter: str) -> dict:
        today = date.today().isoformat()
        self.data["total"] += 1
        self.data["sessionBoops"] += 1
        self.data["byDay"][today] = self.data["byDay"].get(today, 0) + 1
        self.data["byParameter"][parameter] = self.data["byParameter"].get(parameter, 0) + 1
        stamp = now_iso()
        self.data["lastBoopAt"] = stamp
        if not self.data["firstBoopAt"]:
            self.data["firstBoopAt"] = stamp
        self._save()
        return self.stats()

    def stats(self) -> dict:
        today = date.today().isoformat()
        return {
            "total": self.data["total"],
            "today": self.data["byDay"].get(today, 0),
            "session": self.data["sessionBoops"],
            "last": self.data["lastBoopAt"] or "never",
        }


class Chatbox:
    """Rate limited chatbox sender. Coalesces bursts instead of dropping them."""

    def __init__(self, client: BridgeClient, notify: bool, hold: float,
                 template: str | None = None, ascii_only: bool = False):
        self.client = client
        self.notify = notify
        self.hold = hold
        self.template = template
        self.ascii_only = ascii_only
        self.pending: str | None = None
        self.last_sent = 0.0
        self.last_text = ""
        self.last_boop = 0.0

    def show(self, stats: dict) -> None:
        if self.template:
            try:
                text = self.template.format(**stats)
            except (KeyError, IndexError, ValueError) as exc:
                print(f"[chatbox] template error ({exc}), falling back to the built in layout",
                      flush=True)
                text = render_message(stats, self.ascii_only)
        else:
            text = render_message(stats, self.ascii_only)
        self.pending = clamp_chatbox(text)
        self.last_boop = time.monotonic()

    def pump(self) -> None:
        now = time.monotonic()

        # Re-send periodically so the counter stays on screen for a while after a boop,
        # then go quiet instead of occupying the chatbox forever.
        if self.pending is None and self.hold > 0 and self.last_text:
            if now - self.last_boop < self.hold and now - self.last_sent >= 20:
                self.pending = self.last_text

        if self.pending is None or now - self.last_sent < CHATBOX_MIN_INTERVAL:
            return
        text, self.pending = self.pending, None
        if self.client.send(CHATBOX_ADDRESS, [text, True, self.notify]):
            self.last_sent = now
            self.last_text = text
        else:
            self.pending = text  # headset not announced yet, try again next pump


def is_truthy(value) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value > 0.5
    return bool(value)


# ── Chatbox layout ─────────────────────────────────────────────────────────────


def clamp_chatbox(text: str) -> str:
    """Trim to VRChat's limits from the bottom up, so the headline always survives."""
    lines = text.split("\n")[:CHATBOX_MAX_LINES]
    while lines and len("\n".join(lines)) > CHATBOX_MAX_CHARS:
        if len(lines) == 1:
            return lines[0][:CHATBOX_MAX_CHARS]
        lines.pop()
    return "\n".join(lines)


def render_message(stats: dict, ascii_only: bool = False) -> str:
    """
    Multi line chatbox card.

        + - -  B O O P S  - - +
                1,337
             ~ L E E T ~
        today 42  .  session 12
        next: 1701 in 364

    Everything past the headline is optional and gets dropped by clamp_chatbox if the
    numbers grow long enough to threaten the 144 character budget.
    """
    star, dot, wing = ("*", ".", "~") if ascii_only else ("\u2726", "\u00b7", "\u2727")
    total = stats["total"]
    lines = [f"{star} B O O P S {star}", f"{total:,}"]

    label = meme_label(total)
    if label:
        lines.append(f"{wing} {label} {wing}")

    lines.append(f"today {stats['today']:,} {dot} session {stats['session']:,}")

    upcoming = next_meme(total)
    if upcoming:
        lines.append(f"next: {upcoming:,} in {upcoming - total:,}")

    return "\n".join(lines)


def main() -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description="Count avatar contact boops relayed from a Quest.")
    parser.add_argument("--listen-port", type=int, default=DEFAULT_LISTEN_PORT,
                        help=f"UDP port the headset uplinks to (default {DEFAULT_LISTEN_PORT})")
    parser.add_argument("--quest", default="",
                        help="headset IP, only needed if the app's announce is not reaching this PC")
    parser.add_argument("--quest-port", type=int, default=DEFAULT_QUEST_PORT,
                        help=f"app downlink port (default {DEFAULT_QUEST_PORT})")
    parser.add_argument("--params", nargs="+", default=DEFAULT_PARAMS,
                        help=f"avatar parameters that mean 'booped' (default: {' '.join(DEFAULT_PARAMS)})")
    parser.add_argument("--file", default=os.path.join(here, "boop_counts.json"),
                        help="where to keep the running total")
    parser.add_argument("--template", default=None,
                        help="override the built in layout with one line, supports "
                             "{total} {today} {session} {last}")
    parser.add_argument("--ascii", action="store_true",
                        help="plain ASCII decoration instead of unicode glyphs")
    parser.add_argument("--hold", type=float, default=90.0,
                        help="seconds to keep refreshing the chatbox after a boop, 0 to send once")
    parser.add_argument("--debounce", type=float, default=DEFAULT_DEBOUNCE,
                        help="seconds to treat repeat triggers as the same boop")
    parser.add_argument("--notify", action="store_true", help="play the chatbox notification sound")
    parser.add_argument("--no-chatbox", action="store_true", help="only count, never send to VRChat")
    args = parser.parse_args()

    watched = {p if p.startswith("/") else PARAM_PREFIX + p for p in args.params}
    log = BoopLog(args.file)
    client = BridgeClient(args.listen_port, args.quest, args.quest_port)
    chatbox = None if args.no_chatbox else Chatbox(
        client, args.notify, args.hold, template=args.template, ascii_only=args.ascii
    )

    stats = log.stats()
    print(f"boop counter listening on 0.0.0.0:{args.listen_port}")
    print(f"  watching   {', '.join(sorted(watched))}")
    print(f"  log file   {log.path}")
    print(f"  loaded     {stats['total']} total, {stats['today']} today")
    if not args.no_chatbox:
        preview = clamp_chatbox(render_message(stats, args.ascii) if not args.template
                                else args.template.format(**stats))
        print("  chatbox    " + preview.replace("\n", "\n             "))
    if args.quest:
        print(f"  chatbox to {args.quest}:{args.quest_port}")
    else:
        print("  chatbox    waiting for the headset to announce itself")
    print("  Ctrl+C to stop\n", flush=True)

    state: dict[str, bool] = {}
    last_counted = 0.0
    packets = 0
    last_status = time.monotonic()

    def on_message(address, values, _peer):
        nonlocal last_counted
        if address not in watched:
            return
        high = is_truthy(values[0]) if values else False
        was = state.get(address, False)
        state[address] = high
        if not high or was:
            return  # only the rising edge counts

        now = time.monotonic()
        if now - last_counted < args.debounce:
            return  # collider chatter, or a second parameter for the same boop
        last_counted = now

        fresh = log.record(address[len(PARAM_PREFIX):] if address.startswith(PARAM_PREFIX) else address)
        print(f"[{datetime.now().strftime('%H:%M:%S')}] boop #{fresh['total']} "
              f"({fresh['today']} today, {fresh['session']} this session) via {address}", flush=True)
        if chatbox:
            chatbox.show(fresh)

    def stop(*_):
        client.close()

    signal.signal(signal.SIGINT, stop)

    # listen() blocks until a packet arrives, which would stall the rate limited chatbox
    # sender, so run the socket with a short timeout and pump on every tick.
    client.sock.settimeout(0.25)
    while client.running:
        try:
            data, peer = client.sock.recvfrom(65507)
        except socket.timeout:
            if chatbox:
                chatbox.pump()
            now = time.monotonic()
            if now - last_status >= 15:
                last_status = now
                if packets:
                    print(f"[status] {packets} messages from the headset, {log.stats()['total']} boops. "
                          f"Waiting for the next one.", flush=True)
                else:
                    print("[status] nothing from the headset yet. Check PC Link is on and pointed "
                          "at this PC, and that VRChat is in a world.", flush=True)
            continue
        except OSError:
            break
        for address, values in decode(data):
            if address == HELLO_ADDRESS:
                host = values[0] if values and isinstance(values[0], str) else peer[0]
                port = values[1] if len(values) > 1 and isinstance(values[1], int) else args.quest_port
                if (host, port) != (client.quest_host, client.quest_port):
                    client.quest_host, client.quest_port = host, port
                    print(f"[bridge] headset at {host}, chatbox replies go to {host}:{port}", flush=True)
                continue
            packets += 1
            on_message(address, values, peer)
        if chatbox:
            chatbox.pump()

    client.close()
    final = log.stats()
    print(f"\nstopped. {final['total']} boops total, {final['session']} this session.")
    print(f"saved to {log.path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
