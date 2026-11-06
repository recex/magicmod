package com.arcanemod.magicmod;

import com.arcanemod.magicmod.capability.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicMod.MOD_ID)
public class ServerTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player) {
            player.getCapability(ModCapabilities.SPELL_CASTER).ifPresent(data -> {
                data.tickCooldowns();
                if (data.getMana() < data.getMaxMana() && player.tickCount % 20 == 0) {
                    data.addMana(1);
                }
            });
        }
    }
}
