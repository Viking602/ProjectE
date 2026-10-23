package moze_intel.projecte.smoke;

import java.util.ArrayList;
import java.util.List;
import moze_intel.projecte.gameObjs.gui.PEContainerScreen;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value="projecte_smoke", dist=Dist.CLIENT)
public class PortSmokeClient {
    private int ticks, screenTicks, captures, startupTicks;
    private Screen lastScreen, handledBackupScreen;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    public PortSmokeClient() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, message -> LOG.info("PORT_SMOKE screenshot: {}", message.getString()));
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            startupTicks++;
            if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup && backup != handledBackupScreen) {
                // This world is already a disposable copy of the server fixture.
                for (var child : backup.children()) {
                    if (child instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton"))) {
                        handledBackupScreen = backup;
                        LOG.info("PORT_SMOKE: continuing disposable world after experimental-registry notice");
                        button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                        break;
                    }
                }
            }
            if (startupTicks % 200 == 0 && startupTicks <= 1000) {
                LOG.info("PORT_SMOKE startup screen: {}", mc.screen == null ? "none" : mc.screen.getClass().getName());
                capture(mc, "startup-" + startupTicks);
            }
            return;
        }
        ticks++;
        if (Boolean.getBoolean("projecte.smoke.audit")) {
            if (PortGameplayAudit.completed) mc.stop();
            return;
        }
        if (Boolean.getBoolean("projecte.smoke.galleryOnly")) {
            if (ticks == 60) mc.setScreen(new Gallery());
            if (ticks == 100) capture(mc, "item-gallery");
            if (ticks == 180) mc.stop();
            return;
        }
        if (mc.screen != lastScreen) { lastScreen = mc.screen; screenTicks = 0; }
        screenTicks++;
        if (mc.screen instanceof moze_intel.projecte.gameObjs.gui.GUITransmutation screen && screenTicks == 20) {
            var menu = screen.getMenu();
            for (int i = 11; i < 27; i++) {
                if (menu.getSlot(i).getItem().is(net.minecraft.world.item.Items.DIAMOND)) {
                    mc.gameMode.handleContainerInput(menu.containerId, i, 1, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    break;
                }
            }
        }
        if (mc.screen instanceof PEContainerScreen<?> screen && screenTicks == 40) {
            capture(mc, "menu-" + (++captures) + "-" + screen.getMenu().getType().toString().replaceAll("[^A-Za-z0-9]", "_"));
            LOG.info("PORT_SMOKE menu rendered: {}", screen.getClass().getSimpleName());
        }
        if (ticks == 1860) capture(mc, "world-machines");
        if (ticks == 1900) mc.setScreen(new Gallery());
        if (ticks == 1940) capture(mc, "item-gallery");
        if (ticks == 2000) {
            LOG.info("PORT_SMOKE CLIENT COMPLETE: {} machine/item menus captured", captures);
            mc.stop();
        }
    }
    private static class Gallery extends Screen {
        private final List<ItemStack> stacks = new ArrayList<>();
        Gallery() {
            super(Component.literal("ProjectE 26.1.2 item model verification"));
            BuiltInRegistries.ITEM.forEach(item -> {
                if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("projecte")) {
                    var stack = new ItemStack(item);
                    stacks.add(stack);
                    if (stack.has(PEDataComponentTypes.ACTIVE)) {
                        stack = stack.copy();
                        stack.set(PEDataComponentTypes.ACTIVE, true);
                        stacks.add(stack);
                    }
                }
            });
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
            g.fill(0, 0, width, height, 0xFF202530);
            g.text(font, title, 12, 10, 0xFFFFFFFF);
            for (int i = 0; i < stacks.size(); i++) {
                int col = i % 20, row = i / 20;
                g.item(stacks.get(i), 12 + col * 22, 35 + row * 22);
            }
            g.text(font, Component.literal(stacks.size() + " registered item / active-state models"), 12, height - 18, 0xFFFFFFFF);
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
