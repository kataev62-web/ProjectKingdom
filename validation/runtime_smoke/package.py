"""Package only Kingdom's test JAR, provenance and the runner; never third-party mods."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
jar, = (root / 'build/libs').glob('kingdomnpc-*.jar')
out = root / 'build/rc-smoke-artifact'
out.mkdir(parents=True, exist_ok=True)
sha = hashlib.sha256(jar.read_bytes()).hexdigest()
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
shutil.copyfile(jar, out / jar.name)
(out / 'SHA256SUMS').write_text(f'{sha}  {jar.name}\n')
(out / 'manifest.json').write_text(json.dumps(dict(commit=commit, packaging_commit=commit, jar=jar.name, sha256=sha,
    base_pr67='67fbe6796dacab90c1ae391a945577366454c569',
    pr66='43ac75f34aa1c555e71dcbc1bf944223eaebce13',
    base_pr64='00ace4ef9d00c2ddcba3b7b7525418d0cf6badea',
    pr56='28385d3fa12ea720f0184d9df1b15103368fa06a',
    pr57='1644465d93358ab89b8da0a399ab924aee6c6f2b', minecraft='1.20.1', forge='47.3.0'), indent=2)+'\n')
for name in ('run.py', 'mods.lock.json', 'README_RU.md'):
    shutil.copyfile(root / 'validation/runtime_smoke' / name, out / name)
print(f'TEST ARTIFACT: {jar.name}, SHA256 {sha}, commit {commit}')
