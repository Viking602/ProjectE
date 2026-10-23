from pathlib import Path
import json,subprocess
base=Path('src/datagen/generated/assets/projecte')
def model(name):return {'type':'minecraft:model','model':name}
def save(p,o):p.write_text(json.dumps(o,indent=2)+'\n',encoding='utf-8')
for p in (base/'models/item').glob('*.json'):
 original=json.loads(subprocess.check_output(['git','show',f'HEAD:{p.as_posix()}'],text=True,encoding='utf-8'))
 overrides=original.get('overrides',[])
 if not overrides:continue
 fallback=model('projecte:item/'+p.stem)
 if any('minecraft:trim_type' in o['predicate'] for o in overrides):
  cases=[{'when':'minecraft:'+o['model'].rsplit('_',2)[-2], 'model':model(o['model'])} for o in overrides]
  save(base/'items'/p.name,{'model':{'type':'minecraft:select','property':'minecraft:trim_material','cases':cases,'fallback':fallback}})
 else:
  # Compile ordered predicates into a compact threshold tree over their possible ranges.
  props=sorted({k for o in overrides for k in o['predicate']})
  def tree(i,values):
   if i==len(props):
    chosen=fallback
    for o in overrides:
     if all(values[k]>=v for k,v in o['predicate'].items()):chosen=model(o['model'])
    return chosen
   prop=props[i]; thresholds=sorted({o['predicate'][prop] for o in overrides if prop in o['predicate']})
   return {'type':'minecraft:range_dispatch','property':prop,
      'fallback':tree(i+1,values|{prop:float('-inf')}),
      'entries':[{'threshold':v,'model':tree(i+1,values|{prop:v})} for v in thresholds]}
  save(base/'items'/p.name,{'model':tree(0,{})})
p=base/'models/block/base_chest.json';data=json.loads(p.read_text(encoding='utf-8'));data.setdefault('textures',{})['particle']='#chest';save(p,data)
p=Path('src/test/java/moze_intel/projecte/port/PortDataTest.java');s=p.read_text(encoding='utf-8')
for n in ['GEM_BOOTS','DARK_MATTER_PICKAXE','PHILOSOPHERS_STONE']:s=s.replace('new ItemStack(PEItems.'+n+')','new ItemStack(PEItems.'+n+'.get())')
p.write_text(s,encoding='utf-8')
