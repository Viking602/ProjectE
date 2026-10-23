from pathlib import Path
import json, re, secrets
root=Path('src/main/java/moze_intel/projecte')
# All recipe subdirectories must use the new ingredient representation.
def convert(o):
 if isinstance(o,dict):
  if list(o)==['item']:return o['item']
  if list(o)==['tag']:return '#'+o['tag']
  return {k:convert(v) for k,v in o.items()}
 if isinstance(o,list):return [convert(v) for v in o]
 return o
for p in Path('src/datagen/generated/data/projecte/recipe').rglob('*.json'):
 p.write_text(json.dumps(convert(json.loads(p.read_text(encoding='utf-8'))),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
p=root/'PECore.java';s=p.read_text(encoding='utf-8').replace('private void dataPackSync(OnDatapackSyncEvent event) {','private void updateEmcValues() {').replace('\n\t\tif (event.getPlayer() == null) {','\n\t}\n\n\tprivate void dataPackSync(OnDatapackSyncEvent event) {\n\t\tupdateEmcValues();\n\t\tif (event.getPlayer() == null) {',1).replace('private void serverStarting(ServerStartingEvent event) {','private void serverStarting(ServerStartingEvent event) {\n\t\tupdateEmcValues();');p.write_text(s,encoding='utf-8')
for n in ['GemFeet','GemLegs']:
 p=root/f'gameObjs/items/armor/{n}.java';s=p.read_text(encoding='utf-8')
 s=s.replace('\t\tsuper.inventoryTick(stack, level, entity, slot);','''        super.inventoryTick(stack, level, entity, slot);
        tickArmor(stack, level, entity, slot);
    }

    public void tickArmor(ItemStack stack, Level level, Entity entity, net.minecraft.world.entity.EquipmentSlot slot) {''')
 p.write_text(s,encoding='utf-8')
p=root/'gameObjs/items/armor/GemFeet.java';s=p.read_text(encoding='utf-8').replace('extends GemArmorBase {','extends GemArmorBase implements moze_intel.projecte.gameObjs.items.IHasConditionalAttributes {')
a=s.index('\tprivate final Supplier<ItemAttributeModifiers>');b=s.index('\n\tpublic static void toggleStepAssist',a)
s=s[:a]+'''    public GemFeet(Properties props) {
        super(ArmorType.BOOTS, props.component(PEDataComponentTypes.STEP_ASSIST, STEP_ASSIST_DEFAULT));
    }

    @Override public void adjustAttributes(net.neoforged.neoforge.event.ItemAttributeModifierEvent event) {
        event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(PECore.rl("armor"), 1.0, Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.FEET);
        if (isStepAssist(event.getItemStack())) event.addModifier(Attributes.STEP_HEIGHT,
                new AttributeModifier(PECore.rl("gem_step_assist"), 0.4, Operation.ADD_VALUE), EquipmentSlotGroup.FEET);
    }
'''+s[b:];p.write_text(s,encoding='utf-8')
p=root/'gameObjs/items/rings/Arcana.java';s=p.read_text(encoding='utf-8').replace('ICapabilityAware, IExposesCurioAttributes {','ICapabilityAware, IExposesCurioAttributes, moze_intel.projecte.gameObjs.items.IHasConditionalAttributes {')
a=s.index('\n\t@NotNull\n\t@Override\n\t@Deprecated\n\tpublic ItemAttributeModifiers');b=s.index('\n\t@Override\n\tpublic void addAttributes',a);s=s[:a]+'''
    @Override public void adjustAttributes(net.neoforged.neoforge.event.ItemAttributeModifierEvent event) {
        event.addModifier(NeoForgeMod.CREATIVE_FLIGHT, FLIGHT, EquipmentSlotGroup.ANY);
    }
'''+s[b:];p.write_text(s,encoding='utf-8')
p=root/'client/PEClient.java';s=p.read_text(encoding='utf-8').replace('NeoForge.EVENT_BUS.addListener(this::onEntityJoinWorld);','NeoForge.EVENT_BUS.addListener(this::onEntityJoinWorld);\n\t\tNeoForge.EVENT_BUS.addListener(this::tickClientArmor);');a=s.index('\n\tprivate void onEntityJoinWorld');s=s[:a]+'''
    private void tickClientArmor(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.isPaused()) return;
        var feet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if (feet.getItem() instanceof moze_intel.projecte.gameObjs.items.armor.GemFeet boots)
            boots.tickArmor(feet, player.level(), player, net.minecraft.world.entity.EquipmentSlot.FEET);
        var legs = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        if (legs.getItem() instanceof moze_intel.projecte.gameObjs.items.armor.GemLegs leggings)
            leggings.tickArmor(legs, player.level(), player, net.minecraft.world.entity.EquipmentSlot.LEGS);
    }
'''+s[a:];p.write_text(s,encoding='utf-8')
p=Path('../test-projecte-server/server.properties');s=p.read_text(encoding='utf-8');password=secrets.token_hex(16)
for k,v in {'server-ip':'127.0.0.1','server-port':'25586','enable-rcon':'true','rcon.port':'25587','rcon.password':password,'spawn-protection':'0','max-players':'2'}.items():
 s=re.sub('^'+re.escape(k)+'=.*$',k+'='+v,s,flags=re.M)
p.write_text(s,encoding='utf-8')
