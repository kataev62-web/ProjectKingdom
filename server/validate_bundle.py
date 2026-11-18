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
import tarfile
import tempfile
import threading
import zlib
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
    check(values.get("online-mode") == "true",
          "online-mode=true: the launcher token gate is not wired into the login flow yet")
    check(values.get("spawn-animals") == "true",
          "spawn-animals=true: the ranch counts and feeds real animals")
    check(values.get("spawn-npcs") == "true",
          "spawn-npcs=true: the professions work with real villagers")
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


def check_encoding() -> None:
    """The server speaks Russian: the encoding must not depend on the host locale."""
    start = (HERE / "start_server.sh").read_text(encoding="utf-8")
    check("-Dfile.encoding=UTF-8" in start,
          "the server starts with an explicit UTF-8 encoding")
    check("LC_ALL" in start, "the start script fixes the locale of the server")
    backup = (HERE / "backup_world.sh").read_text(encoding="utf-8")
    check("LC_ALL" in backup, "the backup script fixes the locale too")


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



def check_admin() -> None:
    """The operator tool must read the journal and report a damaged tail."""
    with tempfile.TemporaryDirectory() as folder:
        journal = Path(folder) / "settlement-authority.log"
        lines = []
        for tx, kind, extra in (("a", "PREPARED", "item=tfc:ore;qty=20"),
                                ("a", "COMMITTED", ""),
                                ("a", "RECEIPT", ""),
                                ("b", "PREPARED", "item=tfc:ore;qty=5"),
                                ("b", "QUARANTINED", "reason=decision_not_durable")):
            body = f"{kind}|tx={tx};{extra}".rstrip(";")
            crc = format(zlib.crc32(body.encode("utf-8")) & 0xFFFFFFFF, "x")
            lines.append(f"{body}|{crc}")
        journal.write_text("\n".join(lines) + "\n", encoding="utf-8")
        result = subprocess.run([sys.executable, str(HERE / "settlement_admin.py"), str(journal)],
                                capture_output=True, text=True)
        check(result.returncode == 0, "the operator tool reads an intact journal")
        check("RECEIPT" in result.stdout, "the operator tool shows the state of a delivery")
        check("decision_not_durable" in result.stdout, "the operator tool shows the reason of a quarantine")

        with journal.open("a", encoding="utf-8") as handle:
            handle.write("garbage\n")
        broken = subprocess.run([sys.executable, str(HERE / "settlement_admin.py"), str(journal)],
                                capture_output=True, text=True)
        check(broken.returncode == 1, "the operator tool reports a damaged journal")
        check("Проблемы" in broken.stdout, "the operator tool explains the damage")


def check_verify_backup() -> None:
    """A backup must be checked before it is restored."""
    with tempfile.TemporaryDirectory() as folder:
        root = Path(folder)
        world = root / "world"
        (world / "data" / "kingdom_settlement").mkdir(parents=True)
        (world / "level.dat").write_bytes(b"level")
        body = "RECEIPT|tx=11111111-1111-1111-1111-111111111111"
        crc = format(zlib.crc32(body.encode("utf-8")) & 0xFFFFFFFF, "x")
        (world / "data" / "kingdom_settlement" / "settlement-authority.log").write_text(
            f"{body}|{crc}\n", encoding="utf-8")
        archive = root / "backup.tar.gz"
        with tarfile.open(archive, "w:gz") as tar:
            tar.add(world, arcname="world")
        result = subprocess.run([sys.executable, str(HERE / "verify_backup.py"), str(archive)],
                                capture_output=True, text=True)
        check(result.returncode == 0, "a complete archive passes the verification")
        check("пригоден" in result.stdout, "the verification states that the archive can be restored")

        broken = root / "broken.tar.gz"
        with tarfile.open(broken, "w:gz") as tar:
            tar.add(world / "level.dat", arcname="level.dat")
        failed = subprocess.run([sys.executable, str(HERE / "verify_backup.py"), str(broken)],
                                capture_output=True, text=True)
        check(failed.returncode == 1, "an archive without a world does not pass the verification")
        check("world/level.dat" in failed.stdout, "the verification names the missing part")

        broken_world = root / "broken-world"
        (broken_world / "data" / "kingdom_settlement").mkdir(parents=True)
        (broken_world / "level.dat").write_bytes(b"level")
        (broken_world / "data" / "kingdom_settlement" / "settlement-authority.log").write_text(
            "RECEIPT|tx=11111111-1111-1111-1111-111111111111|deadbeef\n", encoding="utf-8")
        damaged = root / "damaged.tar.gz"
        with tarfile.open(damaged, "w:gz") as tar:
            tar.add(broken_world, arcname="world")
        result = subprocess.run([sys.executable, str(HERE / "verify_backup.py"), str(damaged)],
                                capture_output=True, text=True)
        check(result.returncode == 1, "an archive with a damaged journal does not pass the verification")
        check("журнал" in result.stdout, "the verification names the damaged journal")


def check_restore_script() -> None:
    path = HERE / "restore_world.sh"
    check(path.is_file(), "restore_world.sh exists")
    result = subprocess.run(["bash", "-n", str(path)], capture_output=True, text=True)
    check(result.returncode == 0, "restore_world.sh has a valid shell syntax")
    check(os.access(path, os.X_OK), "restore_world.sh is executable")
    text = path.read_text(encoding="utf-8")
    check("verify_backup.py" in text, "the restore verifies the archive before it touches the world")
    check("world.before-restore" in text, "the restore keeps the current world aside")



def check_backup_failure() -> None:
    """A failed flush must not leave the world frozen."""
    with tempfile.TemporaryDirectory() as folder:
        root = Path(folder)
        instance = root / "run"
        (instance / "world").mkdir(parents=True)
        (instance / "world" / "level.dat").write_bytes(b"level")
        log = root / "rcon.log"
        fake = root / "fake_rcon.py"
        fake.write_text(
            "#!/usr/bin/env python3\n"
            "import sys\n"
            "from pathlib import Path\n"
            "Path(__import__('os').environ['KINGDOM_TEST_RCON_LOG']).open('a').write(' '.join(sys.argv[1:]) + chr(10))\n"
            "if any('save-all' in arg for arg in sys.argv):\n"
            "    print('rcon error: the world did not answer')\n"
            "    raise SystemExit(1)\n"
            "print('Saved the game')\n", encoding="utf-8")
        environment = dict(os.environ)
        environment.update({
            "KINGDOM_SERVER_DIR": str(instance),
            "KINGDOM_RCON_BIN": str(fake),
            "KINGDOM_RCON_PASSWORD": "test",
            "KINGDOM_FORCE_LIVE": "1",
            "KINGDOM_TEST_RCON_LOG": str(log),
        })
        result = subprocess.run(["bash", str(HERE / "backup_world.sh")],
                                capture_output=True, text=True, env=environment)
        check(result.returncode != 0, "a failed flush must fail the backup")
        calls = log.read_text(encoding="utf-8") if log.exists() else ""
        check("save-off" in calls, "the saving is switched off before the copy")
        check("save-on" in calls, "the saving is switched on again even when the backup fails")
        check("saving is enabled again" in result.stdout.lower()
              or "save-on" in result.stdout.lower()
              or "enabled again" in result.stdout,
              "the operator is told that the world is writable again")


def check_restore() -> None:
    """A restore must bring the world back and keep the old one aside."""
    with tempfile.TemporaryDirectory() as folder:
        root = Path(folder)
        source = root / "source" / "world"
        (source / "data").mkdir(parents=True)
        (source / "level.dat").write_bytes(b"level")
        (source / "marker.txt").write_text("restored", encoding="utf-8")
        archive = root / "backup.tar.gz"
        with tarfile.open(archive, "w:gz") as tar:
            tar.add(source, arcname="world")

        instance = root / "run"
        (instance / "world").mkdir(parents=True)
        (instance / "world" / "old.txt").write_text("old", encoding="utf-8")
        environment = dict(os.environ)
        environment["KINGDOM_SERVER_DIR"] = str(instance)
        result = subprocess.run(["bash", str(HERE / "restore_world.sh"), str(archive)],
                                capture_output=True, text=True, env=environment)
        check(result.returncode == 0, "the restore must succeed on a valid archive")
        check((instance / "world" / "marker.txt").is_file(), "the world of the archive must be restored")
        check(not (instance / "world" / "old.txt").exists(), "the old world must not stay in place")
        kept = list(instance.glob("world.before-restore-*"))
        check(bool(kept), "the previous world must be kept aside")
        if kept:
            check((kept[0] / "old.txt").is_file(), "the kept world must hold the previous state")


def main() -> int:
    check_properties()
    check_eula()
    check_scripts()
    check_gitignore()
    check_encoding()
    check_rcon()
    check_admin()
    check_verify_backup()
    check_restore_script()
    check_backup_failure()
    check_restore()
    if FAILURES:
        print(f"\n{len(FAILURES)} check(s) failed")
        return 1
    print("\nAll dedicated server bundle checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
