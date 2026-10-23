from pathlib import Path
import re
root=Path('src/main/java/moze_intel/projecte')
p=root/'client/PEClient.java';s=p.read_text(encoding='utf-8')
s=re.sub(r'import net.minecraft.client.renderer.item.ItemPropert[^\n]+\n','',s)
s=s.replace('import net.minecraft.client.renderer.entity.player.PlayerRenderer;','import net.minecraft.client.renderer.entity.player.AvatarRenderer;')
s=s.replace('modEventBus.addListener(this::addLayers);','modEventBus.addListener(this::addLayers);\n\t\tmodEventBus.addListener(this::registerProperties);\n\t\tmodEventBus.addListener(LayerYue::registerState);')
a=s.index('\n\t\tevt.enqueueWork(() -> {');b=s.index('\n\tprivate void registerKeybindings',a);s=s[:a]+'\n\t}\n'+s[b:]
a=s.index('\tprivate void addLayers(');s=s[:a]+'''    private void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var model : event.getSkins()) {
            var skin = event.getPlayerRenderer(model);
            skin.addLayer(new LayerYue(skin));
        }
    }

    private void registerProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(ACTIVE_OVERRIDE, PEItemProperty.ACTIVE.type());
        event.register(MODE_OVERRIDE, PEItemProperty.MODE.type());
    }
}
''';p.write_text(s,encoding='utf-8')
p=root/'integration/recipe_viewer/jei/PEJeiPlugin.java';s=p.read_text(encoding='utf-8').replace('RecipeTypes.FUEL','RecipeTypes.SMELTING_FUEL');s=re.sub(r'\t\t@NotNull\n\t\t@Override\n\t\tpublic String getLegacyStringSubtypeInfo.*?\n\t\t}\n','',s,flags=re.S);p.write_text(s,encoding='utf-8')
p=root/'rendering/TransmutationRenderingOverlay.java';s=p.read_text(encoding='utf-8').replace('import net.minecraft.client.gui.LayeredDraw;','import net.neoforged.neoforge.client.gui.GuiLayer;').replace('RenderHighlightEvent','ExtractBlockOutlineRenderStateEvent').replace('implements LayeredDraw.Layer','implements GuiLayer').replace('ExtractBlockOutlineRenderStateEvent.Block','ExtractBlockOutlineRenderStateEvent').replace('activeRenderInfo.getEntity()', 'activeRenderInfo.entity()').replace('activeRenderInfo.getPosition()', 'activeRenderInfo.position()').replace('graphics.renderItem(', 'graphics.item(')
s=s.replace('mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(properties.getStillTexture())', 'graphics.getSprite(new net.minecraft.client.resources.model.sprite.SpriteId(TextureAtlas.LOCATION_BLOCKS, properties.getStillTexture()))')
s=s.replace('graphics.blit(1, 1, 0, 16, 16, sprite, red, green, blue, alpha)', 'graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, 1, 1, 16, 16, color)')
a=s.index('\t\t\t\tVertexConsumer builder = event.');b=s.index('\n\t\t\t}',a)
s=s[:a]+'''                java.util.List<net.minecraft.world.phys.AABB> boxes = new java.util.ArrayList<>();
                CollisionContext selectionContext = CollisionContext.of(player);
                for (BlockPos pos : changes.keySet()) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) state.getShape(level, pos, selectionContext).forAllBoxes((x1,y1,z1,x2,y2,z2) ->
                            boxes.add(new net.minecraft.world.phys.AABB(x1,y1,z1,x2,y2,z2).move(
                                    pos.getX() - viewPosition.x, pos.getY() - viewPosition.y, pos.getZ() - viewPosition.z)));
                }
                var snapshot = java.util.List.copyOf(boxes);
                event.addCustomRenderer((outline, buffers, pose, translucent, renderState) -> {
                    VertexConsumer builder = buffers.getBuffer(PERenderType.TRANSMUTATION_OVERLAY);
                    for (var box : snapshot) drawBox(pose.last(), builder, box, alpha);
                    return false;
                });'''+s[b:]
a=s.index('\n\tprivate float getPulseProportion()');s=s[:a]+'''
    private static void drawBox(PoseStack.Pose pose, VertexConsumer buffer, net.minecraft.world.phys.AABB b, float alpha) {
        float x=(float)b.minX, y=(float)b.minY, z=(float)b.minZ, X=(float)b.maxX, Y=(float)b.maxY, Z=(float)b.maxZ;
        float[][] vertices = {
            {x,y,z},{X,y,z},{X,Y,z},{x,Y,z}, {x,y,Z},{x,Y,Z},{X,Y,Z},{X,y,Z},
            {x,y,z},{x,Y,z},{x,Y,Z},{x,y,Z}, {X,y,z},{X,y,Z},{X,Y,Z},{X,Y,z},
            {x,y,z},{x,y,Z},{X,y,Z},{X,y,z}, {x,Y,z},{X,Y,z},{X,Y,Z},{x,Y,Z}
        };
        for (float[] v : vertices) buffer.addVertex(pose,v[0],v[1],v[2]).setColor(1F,1F,1F,alpha);
    }
'''+s[a:];p.write_text(s,encoding='utf-8')
