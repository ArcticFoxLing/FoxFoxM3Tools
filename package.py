from pathlib import Path
import hashlib
import json
import sys
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
root = Path(__file__).resolve().parent
spec = json.loads((root / 'project.json').read_text('utf-8'))
stem = spec['name'] + '-1.7.10-' + spec['version']
artifact = root / 'dist' / (stem + '.jar')
digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
build = json.loads((root / 'build/verification.json').read_text('utf-8'))
assert build['sha256'] == digest and build['regressions'] == 'PASS', 'Rebuild first'
fixtures = []
for component in spec['components']:
    selected = None
    for suffix in ('-combined', ''):
        fixture = root / 'build' / ('client-' + component['tag'] + suffix)
        record = fixture / 'verification.json'
        if not record.is_file(): continue
        data = json.loads(record.read_text('utf-8'))
        if (data.get('status') == 'PASS' and data.get('artifacts', {}).get(artifact.name) == digest
                and 'status=PASS' in (fixture / 'result.txt').read_text('utf-8')):
            if component['tag'] == 'chess':
                assert data.get('scope') == 'key controls and full game', 'Run the complete chess game test'
            if component['tag'] == 'water':
                assert data.get('scope') == 'key controls and three complete randomized water trials with original rewards', 'Run all three water trials'
            selected = fixture
            break
    assert selected is not None, 'Run client test on this JAR: ' + component['tag']
    fixtures.append(selected)
packet_fixtures = []
for tag in ('beehive', 'bosssummon', 'skillrow', 'outputcollect'):
    source = next((fixture for fixture in fixtures if fixture.name.startswith('client-' + tag)), None)
    if source is None:
        continue
    replay = root / ('build/server-' + tag + '-8.0.7')
    server = json.loads((replay / 'verification.json').read_text('utf-8'))
    client = json.loads((source / 'verification.json').read_text('utf-8'))
    packets = hashlib.sha256((source / 'packet-cases.nbt').read_bytes()).hexdigest()
    assert server['status'] == 'PASS' and server['artifacts'][artifact.name] == digest, 'Run server-tests/run-' + tag + '.py'
    assert server['packetCasesSha256'] == client['packetCasesSha256'] == packets
    assert 'status=PASS' in (replay / 'result.txt').read_text('utf-8')
    assert server['addonInstalledOnServer'] is False
    packet_fixtures.append(source)
    fixtures.append(replay)
bundle = root / 'dist' / (stem + '-with-source.zip')
with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as out:
    out.write(artifact, artifact.name)
    out.write(root / '使用说明.md', '使用说明.md')
    out.write(root / 'README.md', 'README.md')
    out.write(root / 'LICENSE', 'LICENSE')
    out.write(root / '产物自动收取适配表.md', '产物自动收取适配表.md')
    for name in ('verification.json', 'test-results.txt'):
        out.write(root / 'build' / name, 'validation/' + name)
    for fixture in fixtures:
        for name in ('result.txt', 'verification.json'):
            out.write(fixture / name, 'validation/' + fixture.name + '/' + name)
        for path in sorted((fixture / 'screenshots').glob('*.png')):
            out.write(path, 'screenshots/' + fixture.name + '/' + path.name)
        if fixture in packet_fixtures:
            out.write(fixture / 'packet-cases.nbt', 'validation/' + fixture.name + '/packet-cases.nbt')
    for name in ('src', 'tests', 'client-tests', 'server-tests'):
        for path in sorted((root / name).rglob('*')):
            if path.is_file() and path.suffix in ('.java', '.py', '.info', '.lang'):
                out.write(path, 'source/' + path.relative_to(root).as_posix())
    for name in ('project.json', 'build.py', 'build.ps1', 'package.py', '使用说明.md', '产物自动收取适配表.md',
                 'README.md', 'LICENSE', '.gitignore', '.gitattributes'):
        if (root / name).is_file():
            out.write(root / name, 'source/' + name)
(root / 'dist/SHA256SUMS.txt').write_text(''.join(
    hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
    for path in (artifact, bundle)), encoding='ascii')
print(bundle)
