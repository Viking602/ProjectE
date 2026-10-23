"""Run the packaged mod with the real launcher classpath in a disposable instance."""
from pathlib import Path
import hashlib, json, shutil, subprocess, sys, uuid, zipfile

SOURCE = Path(__file__).resolve().parents[1]
ROOT = SOURCE.parent.parent
core = '--core' in sys.argv
minimal = '--minimal' in sys.argv or core
baseline = '--baseline' in sys.argv
gallery = '--gallery' in sys.argv
audit = '--audit' in sys.argv
optional = '--optional' in sys.argv
without_projecte = '--without-projecte' in sys.argv
label = 'baseline' if baseline else ('gallery' if gallery else ('minimal' if minimal else 'full'))
if core: label = 'core'
if audit: label = 'audit-' + label
if optional: label += '-optional'
if without_projecte: label = 'without-projecte'
FIXTURE = ROOT / ('.work/test-projecte-release-baseline' if baseline else ('.work/test-projecte-release-minimal' if minimal else '.work/test-projecte-release-client'))
if audit or optional or without_projecte: FIXTURE = ROOT / ('.work/test-projecte-' + label)
OUT = ROOT / '.work/verification/projecte-release'
FIXTURE.mkdir(parents=True, exist_ok=True)
OUT.mkdir(parents=True, exist_ok=True)

def allowed(entry):
    if 'rules' not in entry: return True
    result = False
    for rule in entry['rules']:
        os = rule.get('os', {})
        if os.get('name', 'windows') == 'windows' and os.get('arch', 'amd64') in ('x86_64', 'amd64') and not rule.get('features'):
            result = rule['action'] == 'allow'
    return result

def arguments(items, replacements):
    result = []
    for item in items:
        if isinstance(item, dict):
            if not allowed(item): continue
            item = item['value']
        for value in item if isinstance(item, list) else [item]:
            for key, replacement in replacements.items(): value = value.replace('${' + key + '}', replacement)
            if '${' in value: raise ValueError(value)
            result.append(value)
    return result

mods = FIXTURE / 'mods'
mods.mkdir(exist_ok=True)
# Recreate the exact mod set in this owned disposable fixture (including removed candidates).
for old in mods.glob('*.jar'):
    assert old.resolve().parent == mods.resolve() and str(FIXTURE.resolve()).startswith(str((ROOT / '.work').resolve()))
    old.unlink()
if optional:
    for source in (ROOT / '.work/devtools/projecte-optional').glob('*.jar'):
        shutil.copy2(source, mods / source.name)
for source in (ROOT / 'mods').glob('*.jar'):
    if source.name.startswith('ProjectE-'): continue
    if core: continue
    if not minimal or source.name.startswith(('jei-', 'curios-', 'Jade-')): shutil.copy2(source, mods / source.name)
if not minimal:
    # Test mod replacements before installing them into the user's instance.
    for replacement in (ROOT / '.work/outputs/projecte-followup-candidates').glob('*.jar'):
        prefix = replacement.name.split('-26.1.2-')[0] + '-26.1.2-'
        for old in mods.glob(prefix + '*.jar'):
            assert old.resolve().parent == mods.resolve()
            old.unlink()
        shutil.copy2(replacement, mods / replacement.name)
version = next(line.split('=', 1)[1].strip() for line in (SOURCE / 'gradle.properties').read_text().splitlines() if line.startswith('projecte_version='))
release = SOURCE / f'build/libs/ProjectE-{version}.jar'
if not baseline:
    if not without_projecte: shutil.copy2(release, mods / release.name)
    with zipfile.ZipFile(mods / 'projecte-smoke.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
        for folder in ('build/classes/java/smoke', 'build/resources/smoke'):
            for entry in (SOURCE / folder).rglob('*'):
                if not entry.is_file(): continue
                if entry.suffix == '.class' and ((entry.stem == 'PortOptionalSmoke') != without_projecte): continue
                jar.write(entry, entry.relative_to(SOURCE / folder).as_posix())
for folder in ('config', 'defaultconfigs', 'kubejs'):
    if not minimal and (ROOT / folder).is_dir(): shutil.copytree(ROOT / folder, FIXTURE / folder, dirs_exist_ok=True)
(FIXTURE / 'config').mkdir(exist_ok=True)
(FIXTURE / 'config/dynamic_fps.json').write_text('{"enabled":false}\n')
shutil.copytree(ROOT / '.work/test-projecte-server/world', FIXTURE / 'saves/ProjectEPortTest', dirs_exist_ok=True)
shutil.copytree(ROOT / 'natives', FIXTURE / 'natives', dirs_exist_ok=True)
options = (ROOT / '.work/test-projecte-client/options.txt').read_text().splitlines()
overrides = {'pauseOnLostFocus':'false', 'onboardAccessibility':'false', 'skipMultiplayerWarning':'true', 'guiScale':'2', 'renderDistance':'4', 'simulationDistance':'5', 'maxFps':'60', 'tutorialStep':'none', 'soundCategory_master':'0.0'}
options = [line for line in options if line.split(':', 1)[0] not in overrides] + [k+':'+v for k,v in overrides.items()]
(FIXTURE / 'options.txt').write_text('\n'.join(options)+'\n')
metadata = json.loads((ROOT / 'project02.json').read_text())
libraries = [ROOT / 'libraries' / entry['downloads']['artifact']['path'] for entry in metadata['libraries'] if allowed(entry)]
protected = libraries + [ROOT / 'project02.jar', ROOT / 'project02.json'] + [p for p in (ROOT / 'natives').glob('*') if p.is_file() and not p.name.startswith('jna')]
before = {str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in protected}
(OUT / (label+'-hashes-before.json')).write_text(json.dumps(before,indent=2))
replacements = {'auth_player_name':'ProjectEPortTest', 'auth_uuid':uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:ProjectEPortTest').digest(),version=3).hex,
 'auth_access_token':'0', 'auth_xuid':'', 'clientid':'', 'user_type':'legacy', 'version_name':'project02', 'version_type':'release',
 'game_directory':str(FIXTURE), 'assets_root':str(ROOT / 'assets'), 'assets_index_name':metadata['assetIndex']['id'],
 'natives_directory':str(FIXTURE / 'natives'), 'library_directory':str(ROOT / 'libraries'), 'launcher_name':'projecte-verification',
 'launcher_version':'1', 'classpath':';'.join(str(p) for p in libraries+[ROOT / 'project02.jar'])}
args = ['-Xms1G','-Xmx6G', '-Dprojecte.smoke.galleryOnly='+str(gallery).lower()] + arguments(metadata['arguments']['jvm'],replacements) + [metadata['mainClass']] + arguments(metadata['arguments']['game'],replacements)
args.insert(0, '-Dprojecte.smoke.audit='+str(audit).lower())
args += ['--quickPlaySingleplayer','ProjectEPortTest','--width','1280','--height','800']
argfile = FIXTURE / 'launch-args.txt'
argfile.write_text('\n'.join(json.dumps(arg,ensure_ascii=False) for arg in args),encoding='utf-8')
with (OUT / (label+'-launch.log')).open('w',encoding='utf-8') as log:
    p = subprocess.Popen([str(ROOT / '.work/devtools/jdk-25/bin/java.exe'),'@'+str(argfile)],cwd=FIXTURE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
info = {'pid':p.pid,'fixture':str(FIXTURE),'mods':len(list(mods.glob('*.jar'))),'releaseSha256':hashlib.sha256(release.read_bytes()).hexdigest()}
(OUT / (label+'-process.json')).write_text(json.dumps(info,indent=2))
print(json.dumps(info))
