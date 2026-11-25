#!/usr/bin/env python3
"""Start and stop an installed, loopback-only Forge 47.3.0 test server."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--timeout", type=int, default=180, help="startup timeout in seconds")
    options = parser.parse_args()

    server = options.server_dir.resolve()
    properties = server / "server.properties"
    values = dict(
        line.split("=", 1) for line in properties.read_text(encoding="utf-8").splitlines()
        if "=" in line and not line.lstrip().startswith(("#", "!"))
    ) if properties.is_file() else {}
    if values.get("server-ip") != "127.0.0.1":
        parser.error("Set server-ip=127.0.0.1 in the isolated server.properties.")
    eula = server / "eula.txt"
    if not eula.is_file() or "eula=true" not in eula.read_text(encoding="utf-8").splitlines():
        parser.error("The server owner must accept the Minecraft EULA in this directory.")

    argfile = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    forge_args = server / "libraries/net/minecraftforge/forge/1.20.1-47.3.0" / argfile
    if not forge_args.is_file():
        parser.error(f"Install Forge 47.3.0 before running this check: {forge_args}")
    java_home = os.environ.get("JAVA_HOME")
    java_name = "java.exe" if os.name == "nt" else "java"
    java = str(Path(java_home) / "bin" / java_name) if java_home else java_name

    log = server / "npc-dedicated-smoke.log"
    with log.open("wb") as output:
        process = subprocess.Popen(
            [java, "-Xms256m", "-Xmx2g", "-XX:ActiveProcessorCount=2", f"@{forge_args}", "nogui"],
            cwd=server, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
        )
        try:
            deadline = time.monotonic() + options.timeout
            while process.poll() is None and time.monotonic() < deadline:
                if re.search(r"Done \([\d.]+s\)!", log.read_text(errors="replace")):
                    process.stdin.write(b"list\nstop\n")
                    process.stdin.flush()
                    if process.wait(timeout=60) == 0:
                        print(f"PASS: world loaded and server stopped. {log}")
                        return 0
                    break
                time.sleep(1)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            pass
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
    print(f"FAIL: server did not reach readiness and close cleanly. {log}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
