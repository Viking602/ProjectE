from pathlib import Path
import json, shutil
root=Path('src/datagen/generated')
def save(p,o):
 p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(json.dumps(o,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def ingredient(value):
 if isinstance(value,dict):
  if list(value)==['item']:return value['item']
  if list(value)==['tag']:return '#'+value['tag']
  if value.get('type')=='neoforge:components':
   value['components']={k:v for k,v in value['components'].items() if k.startswith('projecte:')}
  return {k:ingredient(v) for k,v in value.items()}
 if isinstance(value,list):return [ingredient(v) for v in value]
 return value
recipes=list((root/'data/projecte/recipe').glob('*.json'))
for p in recipes:save(p,ingredient(json.loads(p.read_text(encoding='utf-8'))))
models=list((root/'assets/projecte/models/item').glob('*.json'))
for p in models:
 data=json.loads(p.read_text(encoding='utf-8'))
 overrides=data.pop('overrides',[])
 base={'type':'minecraft:model','model':'projecte:item/'+p.stem}
 # Legacy overrides are ordered, with the last matching threshold winning.
 for override in overrides:
  entry={'type':'minecraft:model','model':override['model']}
  for prop,threshold in override['predicate'].items():
   entry={'type':'minecraft:range_dispatch','property':prop,'fallback':base,'entries':[{'threshold':threshold,'model':entry}]}
  base=entry
 save(root/'assets/projecte/items'/p.name, {'model':base})
 if overrides:save(p,data if data else {'parent':overrides[0]['model']})
for name in ['dark_matter','red_matter','gem_armor']:
 save(root/f'assets/projecte/equipment/{name}.json',{'layers':{'humanoid':[{'texture':f'projecte:{name}'}],'humanoid_leggings':[{'texture':f'projecte:{name}'}]}})
 for suffix,kind in [('1','humanoid'),('2','humanoid_leggings')]:
  src=Path(f'src/main/resources/assets/projecte/textures/models/armor/{name}_layer_{suffix}.png')
  dst=Path(f'src/main/resources/assets/projecte/textures/entity/equipment/{kind}/{name}.png')
  dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dst)
save(root/'pack.mcmeta', {'pack':{'description':{'translate':'misc.projecte.pack_description'},'min_format':84,'max_format':[101,1]}})
print(f'Migrated {len(recipes)} recipes, {len(models)} item definitions and 3 equipment assets.')
