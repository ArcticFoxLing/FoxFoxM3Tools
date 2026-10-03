from pathlib import Path
import hashlib
import json
import os
import shutil
import socket
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
root = Path(__file__).resolve().parents[1]
workspace = Path(os.environ.get('FOXFOX_WORKSPACE', root.parent)).resolve()
spec = json.loads((root / 'project.json').read_text('utf-8'))
artifact = root / 'dist' / (spec['name'] + '-1.7.10-' + spec['version'] + '.jar')
digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
client = None
for suffix in ('-combined', ''):
    candidate = root / ('build/client-outputcollect' + suffix)
    record = candidate / 'verification.json'
    if not record.is_file():
        continue
    data = json.loads(record.read_text('utf-8'))
    packets = candidate / 'packet-cases.nbt'
    if (data.get('status') == 'PASS' and data.get('artifacts', {}).get(artifact.name) == digest
            and packets.is_file() and data.get('packetCasesSha256') == hashlib.sha256(packets.read_bytes()).hexdigest()):
        client = candidate
        break
assert client is not None, 'Run the outputcollect client test on this release first'
packet_digest = hashlib.sha256((client / 'packet-cases.nbt').read_bytes()).hexdigest()
version = '8.0.7'
fixture = root / ('build/server-outputcollect-' + version)
source = workspace / 'FoxFoxAccessories/test-server'
fixture.mkdir(parents=True, exist_ok=True)
for name in ('libraries', 'config', 'falsepattern'):
    if not (fixture / name).exists():
        shutil.copytree(source / name, fixture / name)
for name in ('Crucible-1.7.10-83c2ff2.jar', 'java21args.txt', 'eula.txt'):
    shutil.copy2(source / name, fixture / name)


def reset_output(path):
    resolved = path.resolve()
    if resolved == (root / 'build').resolve() or not resolved.is_relative_to((root / 'build').resolve()):
        raise ValueError('Output outside isolated build directory: ' + str(resolved))
    if path.exists():
        shutil.rmtree(resolved)
    path.mkdir(parents=True)


mods = fixture / 'mods'
reset_output(mods)
# Fixtures substitute many tile types at the same coordinates; never reuse that world.
reset_output(fixture / 'outputcollect-replay-fixture')
for pattern in ('*Muya*.jar', 'lwjgl3ify-*.jar', '+unimixins-*.jar', 'gtnhlib-*.jar'):
    matches = list((source / 'mods').glob(pattern))
    assert len(matches) == 1, 'Expected one dependency for ' + pattern
    shutil.copy2(matches[0], mods / matches[0].name)
mana = workspace / ('fcwqmmmserver/mods/manametalmod-' + version + '.jar')
shutil.copy2(mana, mods / mana.name)
shutil.copy2(client / 'packet-cases.nbt', fixture / 'packet-cases.nbt')
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    port = sock.getsockname()[1]
(fixture / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=' + str(port) + '\nonline-mode=false\n'
    'level-name=outputcollect-replay-fixture\nlevel-type=FLAT\ngenerator-settings=2;7,2x3,2;1;\n'
    'view-distance=2\nspawn-protection=0\nspawn-monsters=false\nspawn-animals=false\n'
    'generate-structures=false\nenable-query=false\nenable-rcon=false\nmax-players=1\nallow-nether=false\n', encoding='ascii')
classes = root / 'build/server-outputcollect-probe'
reset_output(classes)
deps = [workspace / 'FoxFoxAccessories/tools/forge-1.7.10-srg.jar',
        mods / mana.name, *sorted((workspace / '.minecraft/libraries').rglob('*.jar'))]
subprocess.run(['javac', '--release', '8', '-Xlint:-options', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)),
                '-d', str(classes), str(root / 'server-tests/OutputCollectServerCheck.java'), str(root / 'client-tests/OutputFixtures.java')], check=True)
with zipfile.ZipFile(mods / 'outputcollect-packet-replay-test.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
    for p in classes.rglob('*.class'):
        jar.write(p, p.relative_to(classes).as_posix())
report = fixture / 'result.txt'
report.write_text('RUNNING\n', encoding='utf-8')
with (fixture / 'console.log').open('w', encoding='utf-8') as log:
    proc = subprocess.Popen(['java', '-Xms512M', '-Xmx2G', '-noverify', '@java21args.txt',
                            '-Djava.awt.headless=true', '-Doutputcollect.server.root=' + str(fixture),
                            '-jar', 'Crucible-1.7.10-83c2ff2.jar', 'nogui'], cwd=fixture, stdin=subprocess.PIPE,
                            stdout=log, stderr=subprocess.STDOUT,
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    print('Isolated outputcollect server', version, 'PID', proc.pid, 'port', port, flush=True)
    try:
        code = proc.wait(timeout=240)
    except subprocess.TimeoutExpired:
        proc.stdin.write(b'stop\n'); proc.stdin.flush()
        try:
            proc.wait(timeout=25)
        except subprocess.TimeoutExpired:
            proc.kill(); proc.wait()
        raise SystemExit('Runtime timeout; inspect ' + str(fixture / 'console.log'))
result = report.read_text('utf-8')
print(result, flush=True)
if code != 0 or 'status=PASS' not in result or 'status=FAIL' in result:
    raise SystemExit('OutputCollect server replay failed: ' + str(fixture / 'console.log'))
record = {'status': 'PASS', 'manametal': version, 'artifacts': {artifact.name: digest},
          'packetCasesSha256': packet_digest, 'sourceClient': client.name, 'addonInstalledOnServer': False}
(fixture / 'verification.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
