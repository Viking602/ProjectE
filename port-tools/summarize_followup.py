"""Verify final installed binaries and collect the economy/Titanium/Mekanism evidence."""
from pathlib import Path
import hashlib, json, re, shutil, xml.etree.ElementTree as ET, zipfile

SOURCE = Path(__file__).resolve().parents[1]
ROOT = SOURCE.parent.parent
OUT = ROOT / '.work/verification/projecte-followup'
LOGS = ROOT / '.work/verification/projecte-release'
NAMES = ['ProjectE-26.1.2-1.1.0-project02.3.jar',
         'titanium-26.1.2-4.0.8-project02.1.jar',
         'Mekanism-26.1.2-10.8.0-project02.1.homebaked-projecte.1.jar']

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return path.read_text(encoding='utf-8', errors='replace')
def data(path): return json.loads(read(path).lstrip('\ufeff'))

result = {'installed': {name: digest(ROOT / 'mods' / name) for name in NAMES}, 'runtime': {}, 'unit': {}}
sha = result['installed'][NAMES[0]]
assert digest(SOURCE / 'build/libs' / NAMES[0]) == sha
protected = {}
for label in ['audit-core', 'audit-minimal-optional', 'audit-full', 'full', 'without-projecte']:
    log = read(LOGS / f'{label}-launch.log')
    process = data(LOGS / f'{label}-process.json')
    if label != 'without-projecte': assert process['releaseSha256'] == sha, (label, 'wrong ProjectE build')
    assert 'All dimensions are saved' in log, (label, 'did not save and stop')
    fixture = Path(process['fixture'])
    if label.startswith('audit-'):
        completed = re.search(r'PORT_AUDIT COMPLETE: (\d+) passed, (\d+) failed', log)
        assert completed and int(completed[2]) == 0, (label, 'failed runtime checks')
        names = re.findall(r'PORT_AUDIT PASS: (.+)', log)
        assert len(names) == int(completed[1])
        result['runtime'][label] = {'passed': len(names), 'failed': 0, 'jarCount': process['mods'], 'tests': names}
        assert data(fixture / 'projecte-economy-audit.json') == []
    elif label == 'full':
        assert 'PORT_SMOKE SERVER COMPLETE' in log and 'PORT_SMOKE CLIENT COMPLETE: 15' in log
        assert 'PORT_SMOKE FAILED' not in log
        menus = re.findall(r'PORT_SMOKE menu rendered: (.+)', log)
        checks = re.findall(r'PORT_SMOKE PASS: (.+)', log)
        assert len(menus) == 15 and len(checks) >= 10
        result['gui'] = {'menus': menus, 'machinePickupAndNetworkChecks': checks}
        (OUT / 'screenshots').mkdir(exist_ok=True)
        for name in re.findall(r'PORT_SMOKE screenshot: Saved screenshot as (.+\.png)', log):
            shutil.copy2(fixture / 'screenshots' / name, OUT / 'screenshots' / name)
    else:
        assert 'PORT_OPTIONAL PASS:' in log and not list((fixture / 'mods').glob('ProjectE-26*.jar'))
        result['mekanismWithoutProjectE'] = 'passed'
    if label in ['audit-full', 'full', 'without-projecte']:
        for name in NAMES[1:]: assert digest(fixture / 'mods' / name) == result['installed'][name], (label, name)
    for path, value in data(LOGS / f'{label}-hashes-before.json').items():
        assert digest(Path(path)) == value, ('original game file changed', path)
        protected[path] = value
    shutil.copy2(LOGS / f'{label}-launch.log', OUT / f'{label}-launch.log')
result['protectedOriginalFiles'] = len(protected)
(OUT / 'protected-files.json').write_text(json.dumps(protected, indent=2), encoding='utf-8')

for project in ['ProjectE', 'Mekanism']:
    totals = {k: 0 for k in ['tests', 'failures', 'errors', 'skipped']}
    files = list((ROOT / '.work' / project / 'build/test-results/test').glob('TEST-*.xml'))
    assert files
    for path in files:
        suite = ET.parse(path).getroot()
        for key in totals: totals[key] += int(suite.attrib.get(key, 0))
    assert totals['failures'] == totals['errors'] == totals['skipped'] == 0
    result['unit'][project] = totals

fixture = ROOT / '.work/test-projecte-audit-full'
checked = data(fixture / 'projecte-economy-checked.json')
prices = data(fixture / 'projecte-final-prices.json')
by_id = {row['recipe']: row for row in checked}
assert all(row['gain'] <= 0 and row['repairedGain'] <= 0 for row in checked if row['outputSellEmc'] > 0)
baseline = data(ROOT / '.work/verification/projecte-audit/economy-findings.json')
resolved = []
for old in baseline:
    row = by_id.get(old['recipe'])
    if row:
        assert row['gain'] <= 0 and row['repairedGain'] <= 0
        resolved.append({'recipe': old['recipe'], 'previousGain': old['gain'], **row})
    else:
        unavailable = [name for name in old['ingredients'] if prices.get(name, 0) == 0]
        assert unavailable or prices.get(old['output'], 0) == 0, ('baseline recipe not checked', old['recipe'])
        resolved.append({'recipe': old['recipe'], 'previousGain': old['gain'], 'unvaluedInputs': unavailable,
                         'outputEmc': prices.get(old['output'], 0)})
result['economy'] = {'evaluatedCraftingRecipes': len(checked), 'positiveProfit': 0, 'freeToolRepairProfit': 0,
                     'previousFindings': len(baseline), 'resolved': resolved,
                     'samplePrices': {name: prices.get(name, 0) for name in ['minecraft:iron_ingot', 'minecraft:gold_ingot',
                                     'minecraft:diamond', 'minecraft:coal', 'minecraft:charcoal', 'minecraft:gunpowder',
                                     'projecte:alchemical_coal', 'projecte:mobius_fuel', 'projecte:aeternalis_fuel', 'minecraft:ice',
                                     'minecraft:dirt', 'minecraft:grass_block', 'prefab:block_dirt_stairs', 'prefab:block_grass_stairs']}}
for name in ['projecte-final-prices.json', 'projecte-economy-checked.json', 'projecte-economy-audit.json']:
    shutil.copy2(fixture / name, OUT / name)

with zipfile.ZipFile(ROOT / '.work/outputs/titanium-26.1.2-4.0.8-before-pickup-fix.jar') as old, zipfile.ZipFile(ROOT / 'mods' / NAMES[1]) as new:
    a = {n: old.read(n) for n in old.namelist() if not n.endswith('/')}
    b = {n: new.read(n) for n in new.namelist() if not n.endswith('/')}
    changed = [n for n in a.keys() | b.keys() if a.get(n) != b.get(n)]
    assert all(n == 'META-INF/MANIFEST.MF' or n.startswith('com/hrznstudio/titanium/network/locator/PlayerInventoryFinder') for n in changed), changed
    result['titaniumChangedJarEntries'] = sorted(changed)

before = {Path(row['Path']).name: row['Hash'].lower() for row in data(OUT / 'mods-before.json')}
after = {p.name: digest(p) for p in (ROOT / 'mods').glob('*.jar')}
removed = sorted(before.keys() - after.keys())
added = sorted(after.keys() - before.keys())
modified = [n for n in before.keys() & after.keys() if before[n] != after[n]]
assert sorted(added) == sorted(NAMES) and not modified
assert removed == sorted(['ProjectE-26.1.2-1.1.0-project02.2.jar', 'titanium-26.1.2-4.0.8.jar', 'Mekanism-26.1.2-10.8.0-project02.1.homebaked.jar'])
result['modChanges'] = {'removed': removed, 'added': added, 'otherModsUnchanged': len(before) - len(removed)}
result['limits'] = [
    'PRC and sawmill mappers keep their upstream disabled defaults; twelve Mekanism mappers are enabled by default.',
    'Economy audit enumerates placeable crafting recipes using valued ingredient representatives. It is not exhaustive over all component variants, external machines, loot, or world generation.',
    'Integer EMC corrections can lower prices or remove EMC from free/sub-unit outputs; recipes/items themselves are retained.',
    'Both Mekanism and CakeStickLib trigger 1:1 coal/charcoal recipes; otherwise the original ratios remain.',
    'One additional launch failed before EMC mapping while loading apotheosis:tower_sand: ArrayIndexOutOfBoundsException in HolderSet.Named.addInvalidationListener via NeoForge NotHolderSet. Suspected registry-loading concurrency issue; preserved in audit-full-registry-race.log, not fixed by these three mod patches.',
    'Unrelated third-party JEI/logging warnings remain; this is not certification of the entire modpack.'
]
(OUT / 'REPORT.json').write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
lines = ['ProjectE / Titanium / Mekanism 修复验证 — 2026-10-11', '',
         '已只替换 3 个模组 JAR；其他 %d 个模组未变，%d 个原始游戏文件哈希未变。' % (result['modChanges']['otherModsUnchanged'], len(protected)),
         'EMC：原有 %d 项正收益记录已处理；本轮 %d 个可枚举合成配方未发现正收益，免费修复返还工具后也为 0。' % (len(baseline), len(checked)),
         'Titanium：修复背包槽位读取/写入，验证普通拾取以及主手、背包、末位和副手无限背包拾取。',
         'Mekanism：化学序列化、14 种映射实现、物品组件价格检查通过；12 种默认启用，PRC/锯木机保持上游默认关闭。',
         '未安装 ProjectE 的完整组合启动检查通过。', '']
for name, run in result['runtime'].items(): lines.append(f"{name}：{run['passed']} 项通过，0 失败；{run['jarCount']} 个测试 JAR。")
for name, run in result['unit'].items(): lines.append(f"{name} 单元测试：{run['tests']} 项通过，0 失败。")
lines += ['GUI：15 个菜单、120 个物品/激活模型画廊，以及真实玩家拾取、网络兑换、机器 EMC 守恒。', '',
          '重要行为变化：Mekanism + CakeStickLib 同时加载时煤/木炭转换为 1:1；部分 EMC 重算。冰等免费产物不再提供可兑换的正 EMC。',
          'Prefab 泥土/草楼梯需要小数 EMC，因此取消这两种楼梯的 EMC，保留配方与泥土、草方块的 EMC。',
          '最终价格样例：' + json.dumps(result['economy']['samplePrices'], ensure_ascii=False), '',
          '范围限制：未穷举所有物品组件组合、外部机器、战利品和世界生成。整合包原有第三方 JEI/日志警告仍存在。',
          '额外发现：另一次启动在 Apotheosis 世界生成数据加载阶段出现 HolderSet.Named.addInvalidationListener 越界，疑似并发问题；发生在 EMC 映射之前。这三项补丁未修复该问题，失败日志保存在 audit-full-registry-race.log。', '', '已安装文件及 SHA-256：']
for name, value in result['installed'].items(): lines += [name, value]
(OUT / 'REPORT.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(json.dumps({k: result[k] for k in ['installed', 'unit', 'protectedOriginalFiles', 'modChanges']}, ensure_ascii=False))
print(OUT / 'REPORT.txt')
