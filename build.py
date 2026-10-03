from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
ROOT = Path(__file__).resolve().parent
WORKSPACE = Path(os.environ.get('FOXFOX_WORKSPACE', str(ROOT.parent))).resolve()
SPEC = json.loads((ROOT / 'project.json').read_text('utf-8'))
BUILD = ROOT / 'build'
DIST = ROOT / 'dist'
BUILD.mkdir(exist_ok=True)
DIST.mkdir(exist_ok=True)
LIBS = WORKSPACE / '.minecraft/libraries'
DEPS = [Path(os.environ.get('FORGE_SRG_JAR', str(WORKSPACE / 'FoxFoxAccessories/tools/forge-1.7.10-srg.jar'))),
        LIBS / 'org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar',
        LIBS / 'net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar']
if SPEC['name'] == 'FoxFoxM3Tools':
    DEPS += [Path(os.environ.get('MANAMETAL_JAR', str(WORKSPACE / 'fcwqmmmserver/mods/manametalmod-8.0.7.jar'))),
             Path(os.environ.get('MUYA_JAR', str(WORKSPACE / '.minecraft/mods/[Muya]Muya_1.11.1.jar'))),
             LIBS / 'org/lwjgl/lwjgl/lwjgl/2.9.1/lwjgl-2.9.1.jar']
for dep in DEPS:
    if not dep.is_file():
        raise SystemExit('Missing build dependency: ' + str(dep))
LOGS = []


def reset_output(path):
    target = path.resolve()
    if not target.is_relative_to(BUILD.resolve()) or target == BUILD.resolve():
        raise ValueError('Output must stay within this project build directory: ' + str(target))
    if path.exists():
        shutil.rmtree(target)
    path.mkdir(parents=True)
    return path


def run(args):
    result = subprocess.run(list(map(str, args)), cwd=ROOT, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, encoding='utf-8', errors='replace')
    print(result.stdout, end='', flush=True)
    LOGS.append(result.stdout)
    (BUILD / 'test-results.txt').write_text(''.join(LOGS), encoding='utf-8')
    result.check_returncode()


def compile_java(source, output, dependencies):
    run(['javac', '--release', '8', '-Xlint:-options', '-encoding', 'UTF-8',
         '-cp', os.pathsep.join(map(str, dependencies)), '-d', output, *sorted(source.rglob('*.java'))])


classes = reset_output(BUILD / 'classes')
compile_java(ROOT / 'src', classes, DEPS)
artifact = DIST / (SPEC['name'] + '-1.7.10-' + SPEC['version'] + '.jar')
manifest = ('Manifest-Version: 1.0\r\nFMLCorePlugin: ' + SPEC['corePlugin']
            + '\r\nFMLCorePluginContainsFMLMod: true\r\nImplementation-Title: ' + SPEC['name']
            + '\r\nImplementation-Version: ' + SPEC['version'] + '\r\n\r\n')
with zipfile.ZipFile(artifact, 'w', zipfile.ZIP_DEFLATED) as jar:
    def put(name, data):
        entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        jar.writestr(entry, data)
    put('META-INF/MANIFEST.MF', manifest.encode('utf-8'))
    for path in sorted(classes.rglob('*.class')):
        data = path.read_bytes()
        if int.from_bytes(data[6:8], 'big') != 52:
            raise ValueError('Non-Java-8 bytecode: ' + str(path))
        put(path.relative_to(classes).as_posix(), data)
    for path in sorted((ROOT / 'src').rglob('*')):
        if path.is_file() and path.suffix != '.java':
            put(path.relative_to(ROOT / 'src').as_posix(), path.read_bytes())

if SPEC['name'] == 'FoxFoxFix':
    matches = list((WORKSPACE / '.minecraft/mods').glob('*ThaumicBasesReboot-1.7.10-2.4.2.jar'))
    if len(matches) != 1:
        raise SystemExit('Expected exactly one ThaumicBasesReboot 2.4.2 dependency')
    target = BUILD / 'ThaumicBasesReboot.jar'
    shutil.copy2(matches[0], target)
    suites = [('modularui', 'TransformRegression', [WORKSPACE / '.minecraft/mods/modularui2-2.3.90-1.7.10.jar', 'CLASSES']),
              ('scythe', 'ScytheRegression', [target, 'CLASSES'])]
else:
    suites = [('chess', 'local.foxfoxm3tools.chess.EngineCheck', []),
              ('minigame', 'AnvilSolverRegression', []),
              ('columns', 'LayoutRegression', []),
              ('columns', 'TransformRegression', [DEPS[3]])]
suites.append(('integration', 'JarIdentityCheck', [artifact, SPEC['modid'], SPEC['displayName'], SPEC['corePlugin']]))
compiled = {}
for tag, main, arguments in suites:
    if tag not in compiled:
        output = reset_output(BUILD / ('tests-' + tag))
        compile_java(ROOT / 'tests' / tag, output, [artifact, *DEPS])
        compiled[tag] = output
    output = compiled[tag]
    arguments = [output if item == 'CLASSES' else item for item in arguments]
    run(['java', '-Dfile.encoding=UTF-8', '-Xverify:all', '-cp',
         os.pathsep.join(map(str, [output, artifact, *DEPS])), main, *arguments])

report = {'name': SPEC['name'], 'displayName': SPEC['displayName'], 'modid': SPEC['modid'],
          'version': SPEC['version'], 'artifact': artifact.name,
          'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
          'minecraft': '1.7.10', 'javaTarget': 8, 'clientOnly': True,
          'classCount': len(list(classes.rglob('*.class'))), 'regressions': 'PASS'}
(BUILD / 'verification.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
