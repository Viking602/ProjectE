"""Summarize completed packaged-mod checks; fail rather than report incomplete runs."""
from pathlib import Path
import collections, hashlib, json, re, shutil, xml.etree.ElementTree as ET, zipfile

SOURCE = Path(__file__).resolve().parents[1]
ROOT = SOURCE.parent.parent
LOGS = ROOT / '.work/verification/projecte-release'
OUT = ROOT / '.work/verification/projecte-audit'
version = next(line.split('=', 1)[1].strip() for line in (SOURCE / 'gradle.properties').read_text().splitlines() if line.startswith('projecte_version='))
jar = SOURCE / f'build/libs/ProjectE-{version}.jar'
sha = hashlib.sha256(jar.read_bytes()).hexdigest()
result = {'version': version, 'sha256': sha, 'runtime': {}, 'unit': {}, 'optional': {}, 'protectedFiles': {}}

for label in ['audit-core', 'audit-minimal-optional', 'audit-full']:
    log = (LOGS / f'{label}-launch.log').read_text(encoding='utf-8', errors='replace')
    process = json.loads((LOGS / f'{label}-process.json').read_text())
    complete = re.search(r'PORT_AUDIT COMPLETE: (\d+) passed, (\d+) failed', log)
    assert complete and int(complete[2]) == 0 and 'All dimensions are saved' in log, label
    assert process['releaseSha256'] == sha, (label, 'wrong release')
    names = re.findall(r'PORT_AUDIT PASS: (.+)', log)
    assert len(names) == int(complete[1])
    result['runtime'][label] = {'passed': len(names), 'failed': 0, 'jarCount': process['mods'], 'tests': names,
                                'groups': dict(collections.Counter(name.split('/')[0] for name in names))}
    before = json.loads((LOGS / f'{label}-hashes-before.json').read_text())
    for name, value in before.items():
        assert hashlib.sha256(Path(name).read_bytes()).hexdigest() == value, ('protected file changed', name)
        result['protectedFiles'][name] = value

gui = (LOGS / 'full-launch.log').read_text(encoding='utf-8', errors='replace')
assert json.loads((LOGS / 'full-process.json').read_text())['releaseSha256'] == sha
assert 'PORT_SMOKE SERVER COMPLETE' in gui and 'All dimensions are saved' in gui
assert 'PORT_SMOKE FAILED' not in gui
result['menus'] = re.findall(r'PORT_SMOKE menu rendered: (.+)', gui)
assert len(result['menus']) == 15 and 'PORT_SMOKE CLIENT COMPLETE: 15' in gui
screenshots = OUT / 'screenshots'
screenshots.mkdir(exist_ok=True)
result['screenshots'] = []
for filename in re.findall(r'PORT_SMOKE screenshot: Saved screenshot as (.+\.png)', gui):
    source = ROOT / '.work/test-projecte-release-client/screenshots' / filename
    assert source.is_file()
    shutil.copy2(source, screenshots / filename)
    result['screenshots'].append(str(screenshots / filename))
result['machineAndNetworkChecks'] = re.findall(r'PORT_SMOKE PASS: (.+)', gui)
assert len(result['machineAndNetworkChecks']) >= 9
economyPath = ROOT / '.work/test-projecte-audit-full/projecte-economy-audit.json'
result['unresolvedEconomyFindings'] = json.loads(economyPath.read_text(encoding='utf-8'))
(OUT / 'economy-findings.json').write_text(json.dumps(result['unresolvedEconomyFindings'], ensure_ascii=False, indent=2), encoding='utf-8')
result['unresolvedPickupCrash'] = 'titanium-pickup-crash.txt: Industrial Foregoing / Titanium PlayerInventoryFinder null SlotAccess'

for path in (SOURCE / 'build/test-results/test').glob('TEST-*.xml'):
    test = ET.parse(path).getroot()
    for key in ('tests', 'failures', 'errors', 'skipped'):
        result['unit'][key] = result['unit'].get(key, 0) + int(test.attrib.get(key, 0))
assert result['unit']['failures'] == result['unit']['errors'] == result['unit']['skipped'] == 0

for slug in ('crafttweaker', 'emi', 'the-one-probe', 'wthit', 'badpackets'):
    versions = json.loads((OUT / f'{slug}-versions.json').read_text())
    compatible = [v for v in versions if '26.1.2' in v['game_versions'] and 'neoforge' in v['loaders']]
    result['optional'][slug] = [{'version': v['version_number'], 'id': v['id'], 'published': v['date_published'],
                               'source': f'https://modrinth.com/mod/{slug}/version/{v["id"]}'} for v in compatible]
    for v in compatible:
        for f in v['files']:
            local = ROOT / '.work/devtools/projecte-optional' / f['filename']
            if local.exists():
                assert hashlib.sha512(local.read_bytes()).hexdigest() == f['hashes']['sha512']

with zipfile.ZipFile(jar) as archive:
    names = archive.namelist()
    assert any('/integration/top/PEProbeInfoProvider.class' in n for n in names)
    assert any('/integration/wthit/PEWTHITPlugin.class' in n for n in names)
    assert not any('/smoke/' in n or '/integration/crafttweaker/' in n or '/recipe_viewer/emi/' in n or n.endswith('.jar') for n in names)
    result['packaging'] = {'items': sum(n.startswith('assets/projecte/items/') and n.endswith('.json') for n in names),
                           'recipes': sum(n.startswith('data/projecte/recipe/') and n.endswith('.json') for n in names)}
installed = ROOT / 'mods' / jar.name
assert installed.exists() and hashlib.sha256(installed.read_bytes()).hexdigest() == sha, 'release not installed'
assert len(list((ROOT / 'mods').glob('ProjectE-*.jar'))) == 1
result['installed'] = str(installed)
(OUT / 'REPORT.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')

lines = [
    'ProjectE 功能审计与修复 — 2026-10-11',
    '目标：Minecraft 26.1.2 / NeoForge 26.1.2.114 / Java 25',
    f'已安装：{installed}', f'SHA256：{sha}', '',
    '已复现并修复的问题：',
    '1. 克莱因之星在新物品能力接口下只修改临时副本，获得燃料但实际 EMC 不减；同时修正 Curios 燃料缓存写回。',
    '2. 修复护符只修改物品副本，玩家物品耐久没有恢复；现在明确写回玩家、饰品槽位。',
    '3. 探矿杖模式越过等级范围，低级最终卡住；限制模式循环、恢复非法存档模式，并修正最高 EMC 提示标签。',
    '4. 经验储存石接近容量上限时多扣经验；现在只扣实际可存入的数量，满容量不扣经验。',
    '5. 点燃戒指/奥术指环共用点燃逻辑使用了被点击实心方块的位置；现在在点击面外放火，并保留营火和 TNT 点燃。', '',
    f'构建：BUILD SUCCESSFUL；JUnit {result["unit"]["tests"]} 项，失败/错误/跳过均为 0。',
]
for label, run in result['runtime'].items():
    lines.append(f'{label}: {run["passed"]} 项通过，0 失败；{run["jarCount"]} 个测试 JAR（含一次性测试模组）。')
lines += [
    '覆盖：全部注册物品默认/激活/背包 tick 与物品网络/NBT 往返；充能上限和模式循环；EMC 储存与燃料守恒；',
    '全部可自动构造输入的 ProjectE 合成配方及贤者之石返还；全机器类型 NBT 往返；16 色炼金袋隔离；',
    '知识、超大 EMC 和死亡复制；转化桌学习/遗忘/知识之书/克莱因之星/余额不足；墨丘利之眼建造扣费；',
    '修复、凝缩、疗愈、饱食、飞行、盔甲属性与切换；发射物生成；全基座能力回调及部分实际效果；',
    '采集器/中继器/凝聚器/物质熔炉；工具剥皮、耕地、铺路、剪毛、武器伤害及敌对/全体过滤；',
    '水火护符、冻结、驱逐火把、天气；怀表时间控制/熔炉加速/生物减速；新星方块和箱内物品掉落；',
    '自定义 EMC 指令与磁盘重载；按键包编解码及处理；Curios 供能；TOP/WTHIT 的实际版本 API 回调。',
    '完整整合包另行通过 15 种菜单打开/截图，以及机器生产和客户端点击转化扣除 8192 EMC、取得 1 钻石。',
    '菜单场景重建后清理了旧机器掉落物，以隔离下述 Titanium 拾取崩溃。', '',
    '尚未修复的重要发现：',
    f'当前整合包发现 {len(result["unresolvedEconomyFindings"])} 条实际可匹配合成的 EMC 正收益候选配方，详见 economy-findings.json。',
    '审计使用各原料最低可购买 EMC、实际合成结果/返还物、当前回收损耗计算；它不是全部机器配方的穷举，也未检测每条多步循环。',
    '候选同时包含可再生资源的原有设计和整数舍入差，不能把 44 条都等同于 44 个移植缺陷；例如 ProjectE 水转冰为净增 1 EMC。',
    '这些整合包经济问题保留为显式发现，未擅自更改默认 EMC 表、其他模组配方或全局损耗。',
    '例如 tacz:gunpowder 原料成本 130 EMC，合成 3 份火药可回收 576 EMC；金粒合成金锭为 2043 -> 2048 EMC。',
    'Industrial Foregoing/Titanium 拾取掉落物崩溃：PlayerInventoryFinder.java:24 对 Player.getSlot 返回的 null 调用 get()。',
    '该崩溃发生于完整菜单测试的第一次尝试，报告保存在 titanium-pickup-crash.txt；本次未修改这两个模组。', '',
    '可选联动：',
    'JEI 29.43.0.107、Jade 26.1.11、Curios 15.0.0+26.1.2：使用实例已安装版本。',
    'The One Probe 26.1.2_neo-14.0.0：已恢复 ProjectE 联动并通过测试。',
    'WTHIT neo-19.0.1 + badpackets neo-0.12.2：已恢复 ProjectE 联动并通过测试；WTHIT 发布标记为 Beta。',
    'TOP/WTHIT 前置仅放在开发/测试目录，正式实例继续使用 Jade；ProjectE JAR 含可选适配代码。',
    'CraftTweaker、EMI：作者发布列表未找到 26.1.2 + NeoForge 匹配版，暂不编译/安装联动。',
    '版本依据：本目录各 *-versions.json，来源 https://api.modrinth.com/v2/project/<项目>/version。',
    'Mekanism 当前本地版本在它自己的构建中禁用了 ProjectE 专用化学配方映射；本次未宣称该联动已支持。', '',
    f'保护校验：{len(result["protectedFiles"])} 个原始游戏 JAR/库/版本元数据/原生文件 SHA256 不变。',
    '测试使用 .work/test-* 独立世界。正式实例只更新 ProjectE 模组；旧版保留在 .work/outputs。', '',
    '验证边界：',
    '这是一轮覆盖全注册项和主要功能路径的自动化审计，不等于穷尽所有功能组合。',
    '发射物命中所有目标、各维度特殊环境、所有领地/权限模组、多人真实网络、全部第三方机器加速、长时间压力测试仍未穷尽。',
    'TOP/WTHIT 校验了加载和 EMC 内容回调，未逐项人工检查其全部 HUD 布局；菜单和 120 物品模型画廊截图保存在 screenshots。',
    '整合包仍有 Twilight Forest 的 JEI 配方错误、Apotheosis/Ancient Reforging 词缀错误，以及 JustDynaThings 的 Patchouli 手册缺失引用。',
    '这些日志项不属于 ProjectE 功能检查，未在本次修改；不因 ProjectE 测试通过而认定整个整合包没有问题。',
    '旧 Java 数据生成器仍未完成移植；运行使用已迁移的资源和数据。', '',
    '证据：../projecte-release/*-launch.log；本目录 before-*.log；REPORT.json 包含逐项名称和哈希。',
    '复验脚本：../../ProjectE/port-tools/verify_release.py 与 summarize_audit.py。',
]
lines += ['', 'EMC 正收益配方示例（成本 -> 产物/返还物合计，单位 EMC）：']
for finding in result['unresolvedEconomyFindings'][:10]:
    lines.append(f'{finding["recipe"]}: {finding["inputEmc"]} -> {finding["outputSellEmc"] + finding["remainderSellEmc"]}，净增 {finding["gain"]}')
(OUT / 'REPORT.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(json.dumps({'unit':result['unit'], 'runtime':{k:{a:v[a] for a in ('passed','failed','jarCount')} for k,v in result['runtime'].items()},
                  'protected':len(result['protectedFiles']), 'sha256':sha}, ensure_ascii=False))
