#!/usr/bin/env python3
"""Static and behavioural checks of the dedicated server bundle.

Run: python3 server/validate_bundle.py
Every check must pass before the bundle is used on a real server.
"""
from __future__ import annotations

import importlib.util
import os
import socket
import struct
import subprocess
import sys
import threading
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
FAILURES: list[str] = []


def check(condition: bool, message: str) -> None:
    if condition:
        print(f"OK: {message}")
    else:
        FAILURES.append(message)
        print(f"FAIL: {message}")


def load_rcon():
    spec = importlib.util.spec_from_file_location("kingdom_rcon", HERE / "rcon.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def check_properties() -> None:
    text = (HERE / "server.properties").read_text(encoding="utf-8")
    values: dict[str, str] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    for key in ("server-port", "level-name", "online-mode", "white-list", "enforce-whitelist",
                "sync-chunk-writes", "spawn-protection", "op-permission-level", "view-distance"):
        check(key in values, f"server.properties defines {key}")
    check(values.get("sync-chunk-writes") == "true",
          "sync-chunk-writes=true: chunk writes are flushed, a settled delivery survives a power loss")
    check(values.get("white-list") == "true" and values.get("enforce-whitelist") == "true",
          "the white list is enforced")
    check(values.get("online-mode") == "false",
          "online-mode=false: the launcher token and the white list stay the authority")
    check(values.get("op-permission-level") == "4", "operators keep the full operator level")
    check(values.get("spawn-protection", "0").isdigit(), "spawn-protection is a number")


def check_eula() -> None:
    text = (HERE / "eula.txt").read_text(encoding="utf-8")
    check("eula=false" in text, "the template EULA is not accepted by the repository")


def check_scripts() -> None:
    for name in ("start_server.sh", "backup_world.sh"):
        path = HERE / name
        check(path.is_file(), f"{name} exists")
        result = subprocess.run(["bash", "-n", str(path)], capture_output=True, text=True)
        check(result.returncode == 0, f"{name} has a valid shell syntax")
        check(os.access(path, os.X_OK), f"{name} is executable")


def check_gitignore() -> None:
    text = (ROOT / ".gitignore").read_text(encoding="utf-8")
    check("server/run" in text, "the server instance directory is not committed")


def check_rcon() -> None:
    rcon = load_rcon()
    packet = rcon.encode(7, rcon.SERVERDATA_AUTH, "secret")
    length, request_id, packet_type = rcon.decode_header(packet[:12])
    check(length == len(packet) - 4, "the RCON packet length matches the body")
    check(request_id == 7 and packet_type == rcon.SERVERDATA_AUTH, "the RCON header keeps the request and the type")
    check(packet.endswith(b"\x00\x00"), "the RCON body is null terminated")

    # A fake server answers the authentication and the command.
    def read_exactly(sock: socket.socket, size: int) -> bytes:
        data = b""
        while len(data) < size:
            chunk = sock.recv(size - len(data))
            if not chunk:
                raise ConnectionError("closed")
            data += chunk
        return data

    def fake_server(sock: socket.socket) -> None:
        with sock:
            header = read_exactly(sock, 12)
            (body_length,) = struct.unpack("<i", header[:4])
            read_exactly(sock, body_length - 8)
            sock.sendall(rcon.encode(7, rcon.SERVERDATA_AUTH_RESPONSE, ""))
            command = read_exactly(sock, 12)
            request_id = struct.unpack("<ii", command[4:12])[0]
            sock.sendall(rcon.encode(request_id, rcon.SERVERDATA_RESPONSE_VALUE, "Saved the game"))

    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.bind(("127.0.0.1", 0))
    listener.listen(1)
    port = listener.getsockname()[1]
    thread = threading.Thread(target=lambda: fake_server(listener.accept()[0]), daemon=True)
    thread.start()
    try:
        answer = rcon.send("127.0.0.1", port, "secret", "save-all flush", timeout=10.0)
    finally:
        listener.close()
        thread.join(timeout=5.0)
    check("Saved the game" in answer, "the RCON client runs a command against a server")


def main() -> int:
    check_properties()
    check_eula()
    check_scripts()
    check_gitignore()
    check_rcon()
    if FAILURES:
        print(f"\n{len(FAILURES)} check(s) failed")
        return 1
    print("\nAll dedicated server bundle checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
