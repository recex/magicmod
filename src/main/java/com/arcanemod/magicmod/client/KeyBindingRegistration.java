package com.arcanemod.magicmod.client;

import com.arcanemod.magicmod.MagicMod;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MagicMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class KeyBindingRegistration {

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.CAST_SPELL);
        event.register(KeyBindings.NEXT_SPELL);
        event.register(KeyBindings.PREV_SPELL);
    }
}
