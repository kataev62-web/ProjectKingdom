#!/usr/bin/env python3
"""Disposable, checksum-pinned Forge server. Never uses a production directory."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import signal
import subprocess
import threading
import time
import urllib.request
import zipfile
import tomllib

HERE = Path(__file__).resolve().parent

def open_url(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'ProjectKingdom-runtime-smoke/1.0'}), timeout=60)

def digest(path, algorithm='sha256'):
    h = hashlib.new(algorithm)
    with open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def download(url, target, expected, algorithm):
    if target.exists():
        if digest(target, algorithm) != expected:
            raise ValueError(f'Checksum mismatch: {target.name}; refusing replacement')
        return
    temporary = target.with_suffix(target.suffix + '.part')
    with open_url(url) as src, open(temporary, 'wb') as dst:
        shutil.copyfileobj(src, dst)
    if digest(temporary, algorithm) != expected:
        temporary.unlink()
        raise ValueError(f'Checksum mismatch: {target.name}')
    temporary.rename(target)

def claim_root(root):
    if root.exists() and any(root.iterdir()):
        raise ValueError('Use a NEW empty directory; existing server files are never overwritten')
    root.mkdir(parents=True, exist_ok=True)
    (root / '.kingdom-smoke').write_text('Disposable test directory, not production\n')

def java_options():
    flags = []
    for scheme in ('http', 'https'):
        from urllib.parse import urlsplit
        p = urlsplit(os.environ.get(scheme.upper() + '_PROXY', ''))
        if p.username or p.password:
            raise ValueError('Authenticated proxy requires supported platform configuration')
        if p.hostname:
            flags += [f'-D{scheme}.proxyHost={p.hostname}', f'-D{scheme}.proxyPort={p.port or 80}']
    trust = Path('/workspace/.cloud-setup/truststore.jks')
    if trust.is_file():
        flags += [f'-Djavax.net.ssl.trustStore={trust}', '-Djavax.net.ssl.trustStorePassword=changeit']
    return flags

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, required=True, help='NEW disposable server directory')
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--sha256', required=True, help='Expected checksum of the tested Kingdom JAR')
    p.add_argument('--commit', required=True, help='Commit from downloaded artifact manifest')
    p.add_argument('--accept-eula', action='store_true', help='Owner explicitly accepted https://www.minecraft.net/eula for this test server')
    p.add_argument('--prepare-only', action='store_true')
    p.add_argument('--timeout', type=int, default=240)
    args = p.parse_args()
    version = subprocess.run(['java', '-version'], capture_output=True, text=True, check=True)
    if not re.search(r'version "17\.', version.stderr + version.stdout):
        p.error('Java 17 is required')
    if not args.prepare_only and not args.accept_eula:
        p.error('Owner EULA acceptance required; use --prepare-only or explicitly --accept-eula')
    if not re.fullmatch('[0-9a-f]{40}', args.commit):
        p.error('Expected exact 40-character commit SHA')
    if digest(args.jar) != args.sha256:
        p.error('Kingdom JAR SHA-256 mismatch')
    root = args.root.resolve()
    claim_root(root)
    mods = root / 'mods'; mods.mkdir()
    lock = json.loads((HERE / 'mods.lock.json').read_text())
    for mod in lock['mods']:
        download(mod['url'], mods / mod['filename'], mod['sha512'], 'sha512')
    shutil.copyfile(args.jar, mods / args.jar.name)
    # Record declared mandatory dependencies, including Forge version ranges.
    inventory = []
    for jar in sorted(mods.glob('*.jar')):
        with zipfile.ZipFile(jar) as archive:
            metadata = tomllib.loads(archive.read('META-INF/mods.toml').decode())
        inventory.append({'file': jar.name, 'sha256': digest(jar), 'metadata': metadata})
    installed = {'minecraft', 'forge'}
    for entry in inventory:
        installed.update(mod['modId'] for mod in entry['metadata'].get('mods', []))
    # Create 0.5.1.j embeds Flywheel/Registrate through jar-in-jar; inspect nested metadata too.
    for jar in mods.glob('*.jar'):
        with zipfile.ZipFile(jar) as archive:
            import io
            for name in archive.namelist():
                if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
                        if 'META-INF/mods.toml' in nested.namelist():
                            data = tomllib.loads(nested.read('META-INF/mods.toml').decode())
                            installed.update(m['modId'] for m in data.get('mods', []))
    for entry in inventory:
        for dependencies in entry['metadata'].get('dependencies', {}).values():
            for dep in dependencies:
                if dep.get('mandatory') and dep.get('side') != 'CLIENT' and dep['modId'] not in installed:
                    raise ValueError(f"Missing mandatory dependency: {dep['modId']}")
    (root / 'inventory.json').write_text(json.dumps({'commit': args.commit, 'kingdom_sha256': args.sha256, 'mods': inventory}, indent=2))
    url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/forge-1.20.1-47.3.0-installer.jar'
    expected = open_url(url + '.sha1').read().decode().strip().split()[0]
    installer = root / 'forge-installer.jar'
    download(url, installer, expected, 'sha1')
    with open(root / 'install.log', 'w') as log:
        subprocess.run(['java', *java_options(), '-jar', str(installer), '--installServer', str(root)], cwd=root, stdout=log, stderr=subprocess.STDOUT, check=True)
    (root / 'user_jvm_args.txt').write_text('-Xms1G\n-Xmx4G\n' + '\n'.join(java_options()) + '\n')
    if args.prepare_only:
        print(f'PREPARED only: {root}; no server launched, EULA not accepted')
        return
    # Explicit owner acceptance only. Root is new, never an existing world.
    (root / 'eula.txt').write_text('eula=true\n')
    (root / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=true\nlevel-name=smoke-world\nmax-players=1\nview-distance=3\nsimulation-distance=3\n')
    messages = queue.Queue()
    proc = subprocess.Popen(['bash', './run.sh', 'nogui'], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)
    def read():
        for line in proc.stdout:
            messages.put(line)
    threading.Thread(target=read, daemon=True).start()
    ready = listed = False
    deadline = time.monotonic() + args.timeout
    try:
        with open(root / 'console.log', 'w') as log:
            while time.monotonic() < deadline:
                try:
                    line = messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None:
                        break
                    continue
                log.write(line); log.flush()
                if 'Done (' in line and not ready:
                    ready = True; proc.stdin.write('list\n'); proc.stdin.flush()
                if ready and 'players online' in line:
                    listed = True; proc.stdin.write('stop\n'); proc.stdin.flush()
            if proc.poll() is None:
                proc.stdin.write('stop\n'); proc.stdin.flush()
                proc.wait(timeout=45)
    finally:
        if proc.poll() is None:
            os.killpg(proc.pid, signal.SIGTERM)
            try: proc.wait(timeout=15)
            except subprocess.TimeoutExpired: os.killpg(proc.pid, signal.SIGKILL); proc.wait()
    result = {'ready': ready, 'list_response': listed, 'exit': proc.returncode}
    (root / 'result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
    if not ready or not listed or proc.returncode != 0:
        raise SystemExit('Runtime smoke FAILED: inspect console.log/install.log; no gameplay claim')

if __name__ == '__main__':
    main()
