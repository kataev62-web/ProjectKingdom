#!/usr/bin/env python3
"""Minimal RCON client for the local check of the Kingdom server."""
import socket, struct, sys

TYPE_LOGIN = 3
TYPE_COMMAND = 2
TYPE_RESPONSE = 0


def _packet(request_id: int, packet_type: int, body: str) -> bytes:
    payload = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", 4 + 4 + len(payload), request_id, packet_type) + payload


def _read(sock) -> str:
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4 - len(raw))
        if not chunk:
            return ""
        raw += chunk
    length = struct.unpack("<i", raw)[0]
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    # The answer holds the request id, the type and the body.
    return data[8:].decode("utf-8", "replace").strip("\x00")


def rcon(host: str, port: int, password: str, command: str, timeout: float = 15.0) -> str:
    with socket.create_connection((host, port), timeout) as sock:
        sock.sendall(_packet(1, TYPE_LOGIN, password))
        answer = _read(sock)
        if answer is None or "Bad" in answer:
            return "RCON LOGIN FAILED"
        sock.sendall(_packet(2, TYPE_COMMAND, command))
        return _read(sock)


if __name__ == "__main__":
    host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25575
    password = sys.argv[3] if len(sys.argv) > 3 else "kingdom-local-test"
    for command in sys.argv[4:]:
        print(f">>> {command}")
        print(rcon(host, port, password, command))
