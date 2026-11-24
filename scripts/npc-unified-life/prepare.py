#!/usr/bin/env python3
"""Download/verify locked research files and stage a dedicated-server profile."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile


LOCK = Path(__file__).with_name("dependencies.lock.json")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare_artifact(row, directory, verify_only):
    target = directory / row["filename"]
    if target.is_file() and sha256(target) == row["sha256"]:
        return target
    if verify_only:
        raise ValueError(f"Missing or invalid SHA-256: {target}")

    with tempfile.TemporaryDirectory(prefix="kingdom-npc-", dir=directory) as temporary:
        candidate = Path(temporary) / target.name
        subprocess.run(
            ["curl", "--fail", "--location", "--silent", "--show-error",
             "--retry", "2", "--max-time", "180", "--output", str(candidate), row["url"]],
            check=True,
        )
        if sha256(candidate) != row["sha256"]:
            raise ValueError(f"SHA-256 mismatch for {row['url']}")
        candidate.replace(target)
    return target


def stage_server(server, selected, assets, known_mod_filenames, kingdom_jar):
    mods = server / "mods"
    if mods.exists() and any(mods.iterdir()):
        raise ValueError(f"{mods} must be empty. Select a fresh research directory.")
    if kingdom_jar and kingdom_jar.name in known_mod_filenames:
        raise ValueError("The Kingdom JAR filename conflicts with a locked mod.")
    server.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="npc-profile-", dir=server) as temporary:
        staging = Path(temporary) / "mods"
        staging.mkdir()
        for artifact_id in selected:
            shutil.copy2(assets[artifact_id], staging)
        if kingdom_jar:
            shutil.copy2(kingdom_jar, staging)
        if mods.is_dir():
            mods.rmdir()
        staging.rename(mods)


def main():
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    parser = argparse.ArgumentParser(description=__doc__)
    default_output = Path(__file__).resolve().parents[3] / "kingdom-npc-assets"
    parser.add_argument("--output", type=Path, default=default_output)
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--profile", choices=["all", *lock["profiles"]], default="all")
    parser.add_argument("--server-dir", type=Path)
    parser.add_argument("--kingdom-jar", type=Path)
    options = parser.parse_args()

    if options.server_dir and options.profile == "all":
        parser.error("--server-dir requires a single server profile")
    if options.kingdom_jar and (not options.server_dir or not options.kingdom_jar.is_file()):
        parser.error("--kingdom-jar requires --server-dir and an existing JAR")

    selected = lock["profiles"].get(options.profile, [])
    requested = [row for row in lock["artifacts"]
                 if options.profile == "all" or row["id"] in {*selected, "forge"}]
    options.output.mkdir(parents=True, exist_ok=True)
    try:
        with ThreadPoolExecutor(max_workers=5) as workers:
            paths = list(workers.map(
                lambda row: prepare_artifact(row, options.output, options.verify_only),
                requested,
            ))
        assets = {row["id"]: path for row, path in zip(requested, paths)}
        for row, path in zip(requested, paths):
            print(f"{row['sha256']}  {path}")
        if options.server_dir:
            mod_names = {row["filename"] for row in requested}
            stage_server(options.server_dir, selected, assets, mod_names, options.kingdom_jar)
            print(f"Staged mods: {options.server_dir.resolve() / 'mods'}")
            print(f"Forge installer: {assets['forge'].resolve()}")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
