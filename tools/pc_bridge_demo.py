#!/usr/bin/env python3
"""
Reference desktop client for the VRC OSC Companion PC bridge.

VRChat on a Quest only ever sends OSC to 127.0.0.1, so a PC can never be the direct
destination. The companion app runs on the headset, holds that loopback socket, and relays
in both directions. This script is the other half of that link.

    listen   UDP 9001 on this PC   <- everything VRChat on the Quest emits
    send     UDP 9100 on the Quest -> goes into VRChat

The headset address does not need to be configured: the bridge repeats
/vrcosc/bridge/hello carrying its IP and reply port, and this script picks that up.

No third party packages required.

    python pc_bridge_demo.py
    python pc_bridge_demo.py --send /chatbox/input "hello from the PC" true false
    python pc_bridge_demo.py --send /avatar/parameters/MyToggle true
"""

import argparse
import socket
import struct
import sys
import threading
import time

HELLO_ADDRESS = "/vrcosc/bridge/hello"
DEFAULT_LISTEN_PORT = 9001
DEFAULT_QUEST_PORT = 9100


# ── OSC 1.0 ────────────────────────────────────────────────────────────────────


def _pad(data: bytes) -> bytes:
    """OSC pads every field to a 4 byte boundary, including the null terminator."""
    return data + b"\x00" * (4 - (len(data) % 4) if len(data) % 4 else 4)


def encode(address: str, args) -> bytes:
    tags = ","
    body = b""
    for arg in args:
        if isinstance(arg, bool):
            tags += "T" if arg else "F"
        elif isinstance(arg, int):
            tags += "i"
            body += struct.pack(">i", arg)
        elif isinstance(arg, float):
            tags += "f"
            body += struct.pack(">f", arg)
        elif isinstance(arg, str):
            tags += "s"
            body += _pad(arg.encode("utf-8"))
        else:
            raise TypeError(f"unsupported OSC argument type: {type(arg)!r}")
    return _pad(address.encode("utf-8")) + _pad(tags.encode("utf-8")) + body


def _read_string(data: bytes, offset: int):
    end = data.index(b"\x00", offset)
    value = data[offset:end].decode("utf-8", "replace")
    return value, offset + (end - offset) + (4 - ((end - offset) % 4) if (end - offset) % 4 else 4)


def decode(data: bytes):
    """Returns a list of (address, args). Bundles are flattened."""
    if data.startswith(b"#bundle\x00"):
        messages = []
        offset = 16  # marker plus timetag
        while offset + 4 <= len(data):
            size = struct.unpack_from(">i", data, offset)[0]
            offset += 4
            if size <= 0 or offset + size > len(data):
                break
            messages.extend(decode(data[offset:offset + size]))
            offset += size
        return messages

    try:
        address, offset = _read_string(data, 0)
        if not address.startswith("/"):
            return []
        if offset >= len(data):
            return [(address, [])]
        tags, offset = _read_string(data, offset)
        args = []
        for tag in tags[1:]:
            if tag == "i":
                args.append(struct.unpack_from(">i", data, offset)[0])
                offset += 4
            elif tag == "f":
                args.append(round(struct.unpack_from(">f", data, offset)[0], 4))
                offset += 4
            elif tag == "s":
                value, offset = _read_string(data, offset)
                args.append(value)
            elif tag == "T":
                args.append(True)
            elif tag == "F":
                args.append(False)
            elif tag == "b":
                size = struct.unpack_from(">i", data, offset)[0]
                offset += 4
                args.append(f"<blob {size}B>")
                offset += size + ((4 - (size % 4)) % 4)
            else:
                break
        return [(address, args)]
    except (ValueError, struct.error):
        return []


# ── Bridge client ──────────────────────────────────────────────────────────────


class BridgeClient:
    def __init__(self, listen_port: int, quest_host: str = "", quest_port: int = DEFAULT_QUEST_PORT):
        self.quest_host = quest_host
        self.quest_port = quest_port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("0.0.0.0", listen_port))
        self.running = True
        self.received = 0
        self._last_hello = 0.0

    def send(self, address: str, args) -> bool:
        if not self.quest_host:
            return False
        self.sock.sendto(encode(address, args), (self.quest_host, self.quest_port))
        return True

    def listen(self, on_message=None):
        while self.running:
            try:
                data, peer = self.sock.recvfrom(65507)
            except OSError:
                return
            for address, args in decode(data):
                if address == HELLO_ADDRESS:
                    self._handle_hello(args, peer)
                    continue
                self.received += 1
                if on_message:
                    on_message(address, args, peer)

    def _handle_hello(self, args, peer):
        host = args[0] if args and isinstance(args[0], str) else peer[0]
        port = args[1] if len(args) > 1 and isinstance(args[1], int) else DEFAULT_QUEST_PORT
        changed = (host, port) != (self.quest_host, self.quest_port)
        self.quest_host, self.quest_port = host, port
        now = time.time()
        if changed or now - self._last_hello > 30:
            self._last_hello = now
            print(f"[bridge] headset at {host}, replies go to {host}:{port}", flush=True)

    def close(self):
        self.running = False
        self.sock.close()


def parse_value(raw: str):
    lowered = raw.lower()
    if lowered == "true":
        return True
    if lowered == "false":
        return False
    try:
        return int(raw)
    except ValueError:
        pass
    try:
        return float(raw)
    except ValueError:
        pass
    return raw


def main() -> int:
    parser = argparse.ArgumentParser(description="VRC OSC Companion PC bridge client")
    parser.add_argument("--listen-port", type=int, default=DEFAULT_LISTEN_PORT,
                        help=f"UDP port the headset sends to (default {DEFAULT_LISTEN_PORT})")
    parser.add_argument("--quest", default="",
                        help="headset IP. Optional: learned from the hello message.")
    parser.add_argument("--quest-port", type=int, default=DEFAULT_QUEST_PORT,
                        help=f"headset bridge listen port (default {DEFAULT_QUEST_PORT})")
    parser.add_argument("--send", nargs="+", metavar="ARG",
                        help="send one message then keep listening: --send /address value ...")
    parser.add_argument("--filter", default="",
                        help="only print addresses containing this text")
    parser.add_argument("--echo", action="store_true",
                        help="mirror every avatar parameter back with an Echo suffix, as a round trip test")
    args = parser.parse_args()

    client = BridgeClient(args.listen_port, args.quest, args.quest_port)
    print(f"[bridge] listening on 0.0.0.0:{args.listen_port}", flush=True)
    if not args.quest:
        print("[bridge] waiting for the headset to announce itself", flush=True)

    def on_message(address, values, peer):
        if args.filter and args.filter not in address:
            return
        rendered = " ".join(str(v) for v in values)
        print(f"{address} {rendered}".rstrip(), flush=True)
        if args.echo and address.startswith("/avatar/parameters/"):
            client.send(address + "Echo", values)

    thread = threading.Thread(target=client.listen, args=(on_message,), daemon=True)
    thread.start()

    if args.send:
        address = args.send[0]
        values = [parse_value(v) for v in args.send[1:]]
        # Give the hello message a moment to arrive when no address was supplied.
        deadline = time.time() + 5
        while not client.quest_host and time.time() < deadline:
            time.sleep(0.2)
        if client.send(address, values):
            print(f"[bridge] sent {address} {' '.join(str(v) for v in values)}".rstrip(), flush=True)
        else:
            print("[bridge] no headset address yet, nothing sent", flush=True)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"\n[bridge] {client.received} messages received", flush=True)
        client.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
