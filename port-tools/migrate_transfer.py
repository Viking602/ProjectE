from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'src/main/java/moze_intel/projecte'
adapter='moze_intel.projecte.utils.ItemTransferAdapter'
for p in JAVA.rglob('*.java'):
 s=p.read_text(encoding='utf-8')
 s=s.replace('import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;', 'import net.neoforged.neoforge.capabilities.Capabilities;')
 s=s.replace('import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;', 'import net.neoforged.neoforge.capabilities.Capabilities;')
 s=s.replace('ItemHandler.BLOCK','Capabilities.Item.BLOCK').replace('ItemHandler.ENTITY','Capabilities.Item.ENTITY').replace('ItemHandler.ITEM','Capabilities.Item.ITEM').replace('FluidHandler.BLOCK','Capabilities.Fluid.BLOCK').replace('FluidHandler.ITEM','Capabilities.Fluid.ITEM')
 s=re.sub(r'(\b\w+\.getCapability\(Capabilities.Item.ENTITY\))',adapter+r'.legacy(\1)',s)
 s=re.sub(r'(\b(?:this\.)?\w+)\.getCapability\(Capabilities.Item.ITEM\)',lambda m:adapter+'.legacy('+m[1]+'.getCapability(Capabilities.Item.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forStack('+m[1]+')))',s)
 # Wrap exactly the argument list, including nested calls, for world lookups.
 for cap,wrap in [('Item',adapter+'.legacy'),('Fluid','moze_intel.projecte.utils.FluidTransferHelper.legacy')]:
  pattern=r'WorldHelper.getCapability\([^;\n]+?Capabilities\.'+cap+r'\.BLOCK'
  matches=list(re.finditer(pattern,s))
  for m in reversed(matches):
   start=m.start();opening=s.index('(',start);depth=1;end=opening+1
   while depth:
    if s[end]=='(':depth+=1
    elif s[end]==')':depth-=1
    end+=1
   s=s[:start]+wrap+'('+s[start:end]+')'+s[end:]
 if p.name=='PEBlockEntityTypes.java':
  s=re.sub(r'\.with\(Capabilities.Item.BLOCK, ([\w.]+)\)',lambda m:'.with(Capabilities.Item.BLOCK, (be, side) -> '+adapter+'.of('+m[1]+'.getCapability(be, side)))',s)
 if p.name=='DMFurnaceBlockEntity.java':
  s=s.replace('BlockCapabilityCache<IItemHandler,', 'BlockCapabilityCache<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>,')
  s=s.replace('= pullTarget.getCapability();','= '+adapter+'.legacy(pullTarget.getCapability());').replace('= pushTarget.getCapability();','= '+adapter+'.legacy(pushTarget.getCapability());')
 if p.name=='MercurialEye.java':
  s=s.replace('(stack, context) -> new EyeItemHandler(stack)', '(stack, context) -> '+adapter+'.of(new EyeItemHandler(stack))')
 if p.name=='EvertideAmulet.java':
  s=s.replace('(stack, context) -> new InfiniteFluidHandler(stack)', '(stack, context) -> moze_intel.projecte.utils.FluidTransferHelper.INFINITE_WATER')
 p.write_text(s,encoding='utf-8')
