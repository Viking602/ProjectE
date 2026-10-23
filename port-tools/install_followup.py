"""Install only the three validated mod replacements, preserving the previous JARs."""
from pathlib import Path
import hashlib, json, re, shutil

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / '.work/verification/projecte-followup'
LOGS = ROOT / '.work/verification/projecte-release'
MODS = ROOT / 'mods'
CANDIDATES = ROOT / '.work/outputs/projecte-followup-candidates'
changes = [
    ('ProjectE-26.1.2-1.1.0-project02.2.jar', ROOT / '.work/ProjectE/build/libs/ProjectE-26.1.2-1.1.0-project02.3.jar'),
    ('titanium-26.1.2-4.0.8.jar', CANDIDATES / 'titanium-26.1.2-4.0.8-project02.1.jar'),
    ('Mekanism-26.1.2-10.8.0-project02.1.homebaked.jar', CANDIDATES / 'Mekanism-26.1.2-10.8.0-project02.1.homebaked-projecte.1.jar'),
]
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return path.read_text(encoding='utf-8', errors='replace').lstrip('\ufeff')
sha = digest(changes[0][1])
for label in ['audit-core', 'audit-minimal-optional', 'audit-full', 'full']:
    log = read(LOGS / f'{label}-launch.log')
    process = json.loads(read(LOGS / f'{label}-process.json'))
    assert process['releaseSha256'] == sha and 'All dimensions are saved' in log, label
    if label == 'full':
        assert 'PORT_SMOKE SERVER COMPLETE' in log and 'PORT_SMOKE CLIENT COMPLETE: 15' in log
        assert 'PORT_SMOKE FAILED' not in log
    else:
        result = re.search(r'PORT_AUDIT COMPLETE: (\d+) passed, (\d+) failed', log)
        assert result and int(result[2]) == 0, label
    if label in ['audit-full', 'full']:
        for _, source in changes:
            assert digest(Path(process['fixture']) / 'mods' / source.name) == digest(source)
    for path, value in json.loads(read(LOGS / f'{label}-hashes-before.json')).items():
        assert digest(Path(path)) == value, path
assert json.loads(read(ROOT / '.work/test-projecte-audit-full/projecte-economy-audit.json')) == []
before = {Path(row['Path']).name: row['Hash'].lower() for row in json.loads(read(OUT / 'mods-before.json'))}
for old, source in changes:
    assert digest(MODS / old) == before[old], ('installed original changed concurrently', old)
    assert not (MODS / source.name).exists(), ('unexpected destination', source.name)
backup = ROOT / '.work/outputs/projecte-followup-backup'
backup.mkdir(exist_ok=True)
for old, source in changes:
    shutil.copy2(MODS / old, backup / old)
    shutil.copy2(source, ROOT / '.work/outputs' / source.name)
installed = []
try:
    for old, source in changes:
        shutil.copy2(source, MODS / source.name)
        installed.append(MODS / source.name)
        assert digest(MODS / source.name) == digest(source)
        (MODS / old).unlink()
except BaseException:
    for old, source in changes:
        if not (MODS / old).exists(): shutil.copy2(backup / old, MODS / old)
    for path in installed:
        assert path.parent == MODS
        path.unlink()
    raise
print(json.dumps({path.name: digest(path) for path in installed}, indent=2))
