from pathlib import Path
from urllib.request import urlopen
from concurrent.futures import ThreadPoolExecutor
repo=Path('../devtools/maven')
coords=[('org.junit','junit-bom','5.13.4',False),('org.opentest4j','opentest4j','1.3.0',True),('org.apiguardian','apiguardian-api','1.1.2',True),('net.neoforged','testframework','26.1.2.114',True)]
coords += [('org.junit.jupiter','junit-jupiter'+suffix,'5.13.4',True) for suffix in ['', '-api','-params','-engine']]
coords += [('org.junit.platform','junit-platform-'+suffix,'1.13.4',True) for suffix in ['commons','engine','launcher','testkit']]
def get(coord):
 group,artifact,version,jar=coord
 prefix=group.replace('.','/')+'/'+artifact+'/'+version+'/'+artifact+'-'+version
 base='https://maven.neoforged.net/releases/' if group=='net.neoforged' else 'https://repo.maven.apache.org/maven2/'
 for ext in (['pom','jar'] if jar else ['pom']):
  path=repo/(prefix+'.'+ext)
  if not path.exists():
   path.parent.mkdir(parents=True,exist_ok=True)
   with urlopen(base+prefix+'.'+ext,timeout=30) as response: data=response.read()
   path.write_bytes(data)
 return artifact
with ThreadPoolExecutor(max_workers=6) as pool:
 for result in pool.map(get,coords):print(result)
