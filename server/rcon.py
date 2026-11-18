#!/usr/bin/env python3
"""Minimal Source RCON client for the Project Kingdom dedicated server.

Used by backup_world.sh to flush and save a running world without stopping it.
The protocol is the Source RCON one: a length prefixed packet with a request
id, a type and a null terminated body.
"""
from __future__ import annotations

import argparse
import socket
import struct

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0

MAX_PACKET = 4096


def encode(request_id: int, packet_type: int, body: str) -> bytes:
    """Builds one RCON packet."""
    payload = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(payload) + 8, request_id, packet_type) + payload


def decode_header(data: bytes) -> tuple[int, int, int]:
    """Returns (length, request_id, packet_type) of a packet header."""
    if len(data) < 4:
        raise ValueError("short RCON header")
    (length,) = struct.unpack("<i", data[:4])
    if length < 10 or length > MAX_PACKET:
        raise ValueError(f"invalid RCON packet length: {length}")
    request_id, packet_type = struct.unpack("<ii", data[4:12])
    return length, request_id, packet_type


def read_packet(sock: socket.socket) -> tuple[int, int, str]:
    """Reads one RCON packet and returns (request_id, packet_type, body)."""
    header = b""
    while len(header) < 4:
        chunk = sock.recv(4 - len(header))
        if not chunk:
            raise ConnectionError("the server closed the RCON connection")
        header += chunk
    (length,) = struct.unpack("<i", header)
    if length < 10 or length > MAX_PACKET:
        raise ValueError(f"invalid RCON packet length: {length}")
    body = b""
    while len(body) < length:
        chunk = sock.recv(length - len(body))
        if not chunk:
            raise ConnectionError("the server closed the RCON connection")
        body += chunk
    request_id, packet_type = struct.unpack("<ii", body[:8])
    return request_id, packet_type, body[8:].rstrip(b"\x00").decode("utf-8", "replace")


def send(host: str, port: int, password: str, command: str, timeout: float = 15.0) -> str:
    """Authenticates and runs one command, returning the answer of the server."""
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        sock.sendall(encode(1, SERVERDATA_AUTH, password))
        request_id, packet_type, _ = read_packet(sock)
        if packet_type != SERVERDATA_AUTH_RESPONSE:
            raise ConnectionError("unexpected answer to the RCON authentication")
        # A failed login is answered with an empty body or a special id.
        if request_id == -1:
            raise PermissionError("the RCON password is wrong")
        sock.sendall(encode(2, SERVERDATA_EXECCOMMAND, command))
        answers: list[str] = []
        while True:
            request_id, packet_type, body = read_packet(sock)
            if packet_type != SERVERDATA_RESPONSE_VALUE:
                continue
            answers.append(body)
            if request_id == 2:
                break
        return "\n".join(answer for answer in answers if answer)


def main() -> int:
    parser = argparse.ArgumentParser(description="run a command over RCON")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", required=True)
    parser.add_argument("command")
    arguments = parser.parse_args()
    try:
        print(send(arguments.host, arguments.port, arguments.password, arguments.command))
    except (OSError, ValueError, ConnectionError, PermissionError) as error:
        print(f"rcon error: {error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
