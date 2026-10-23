package moze_intel.projecte.smoke;

import java.util.List;
import java.util.function.BiConsumer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import mekanism.common.integration.projecte.NSSChemical;
import moze_intel.projecte.api.ProjectERegistries;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.mapper.recipe.INSSFakeGroupManager;
import moze_intel.projecte.api.nss.NSSFake;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.utils.AnnotationHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

final class PortMekanismAudit {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server, BiConsumer<String, Runnable> test) {
        test.accept("mekanism_chemical_serializer_registered_roundtrip", () -> {
            check(ProjectERegistries.NSS_SERIALIZER.getValue(Identifier.parse("mekanism:chemical")) == NSSChemical.CODEC, "chemical serializer not registered");
            for (String name : List.of("oxygen", "hydrogen", "sulfuric_acid", "clean_iron", "dirty_iron")) {
                var chemical = NSSChemical.createChemical(Identifier.fromNamespaceAndPath("mekanism", name));
                var ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                var encoded = NSSChemical.CODEC.codec().encodeStart(ops, chemical).getOrThrow();
                check(NSSChemical.CODEC.codec().parse(ops, encoded).getOrThrow().equals(chemical), "NSS chemical roundtrip failed");
            }
        });
        var mappers = AnnotationHelper.getRecipeTypeMappers().stream().filter(m -> m.getClass().getName().startsWith("mekanism.")).toList();
        test.accept("mekanism_contents_preserve_stored_item_components", () -> {
            var tool = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
            tool.setDamageValue(tool.getMaxDamage() / 2);
            var carrier = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST);
            carrier.set(mekanism.common.registries.MekanismDataComponents.ATTACHED_ITEMS,
                    new mekanism.common.component.containers.resource.AttachedResources<>(List.of(new mekanism.api.resource.LargeResourceStack<>(
                            net.neoforged.neoforge.transfer.item.ItemResource.of(tool), 2))));
            var processor = new mekanism.common.integration.projecte.processors.MekanismContentsProcessor();
            long contentEmc = moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(tool);
            check(contentEmc > 0 && processor.recalculateEMC(moze_intel.projecte.api.ItemInfo.fromStack(carrier), 100) == 100 + contentEmc * 2,
                    "damaged contents were valued as new items");
        });
        test.accept("mekanism_fourteen_mappers_discovered", () -> check(mappers.size() == 14, "missing recipe mappers: " + mappers.size()));
        for (var mapper : mappers) test.accept("mekanism_actual_recipe_mappings/" + mapper.getClass().getSimpleName(), () -> {
            int[] conversions = {0};
            IMappingCollector<NormalizedSimpleStack, Long> collector = (IMappingCollector<NormalizedSimpleStack, Long>) java.lang.reflect.Proxy.newProxyInstance(
                    IMappingCollector.class.getClassLoader(), new Class<?>[]{IMappingCollector.class}, (proxy, method, args) -> {
                        if (method.getName().equals("addConversion")) {
                            check((int) args[0] > 0 && args[1] != null, "invalid conversion");
                            conversions[0]++;
                        }
                        return null;
                    });
            INSSFakeGroupManager groups = new INSSFakeGroupManager() {
                int sequence;
                public FakeGroupData getOrCreateFakeGroup(Object2IntMap<NormalizedSimpleStack> stacks, boolean ingredient, boolean skip) {
                    return new FakeGroupData(NSSFake.create("mekanism_audit_" + sequence++), true);
                }
                public FakeGroupData getOrCreateFakeGroupDirect(Object2IntMap<NormalizedSimpleStack> stacks, boolean ingredient, boolean skip) {
                    return getOrCreateFakeGroup(stacks, ingredient, skip);
                }
            };
            int handled = 0;
            for (var recipe : server.getRecipeManager().recipeMap().values()) {
                if (mapper.canHandle(recipe.value().getType()) && mapper.handleRecipe(collector, recipe, server.registryAccess(), groups)) handled++;
            }
            check(handled > 0 && conversions[0] >= handled, "mapper produced no conversions");
        });
    }
}
