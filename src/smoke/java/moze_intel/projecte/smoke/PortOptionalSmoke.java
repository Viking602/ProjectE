package moze_intel.projecte.smoke;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Packaged alone to verify Mekanism remains loadable when ProjectE is absent. */
@Mod(value = "projecte_smoke", dist = Dist.CLIENT)
public final class PortOptionalSmoke {
    private int ticks;
    private net.minecraft.client.gui.screens.Screen handledBackup;
    public PortOptionalSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup && backup != handledBackup) {
            for (var child : backup.children()) if (child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().equals(net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton"))) {
                handledBackup = backup;
                button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0)); break;
            }
        }
        if (client.level == null || client.player == null || ++ticks != 120) return;
        if (ModList.get().isLoaded("projecte") || !ModList.get().isLoaded("mekanism")) throw new AssertionError("Invalid optional integration fixture");
        com.mojang.logging.LogUtils.getLogger().info("PORT_OPTIONAL PASS: full pack world loaded with patched Mekanism and without ProjectE");
        client.stop();
    }
}
