package com.arcanemod.magicmod.client;

import com.arcanemod.magicmod.capability.ModCapabilities;
import com.arcanemod.magicmod.network.ModNetwork;
import com.arcanemod.magicmod.network.RequestCastSpellPacket;
import com.arcanemod.magicmod.registry.ModSpells;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le a tecla de cast a cada client tick, suporta carregar a magia
 * (hold-to-charge) e navegar entre as 30 magias com NEXT/PREV.
 */
public class SpellCastInputHandler {

    private int chargeTicks = 0;
    private boolean wasDown = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (KeyBindings.NEXT_SPELL.consumeClick()) {
            cycleSpell(mc, 1);
        }
        if (KeyBindings.PREV_SPELL.consumeClick()) {
            cycleSpell(mc, -1);
        }

        boolean down = KeyBindings.CAST_SPELL.isDown();
        if (down) {
            chargeTicks++;
            wasDown = true;
        } else if (wasDown) {
            mc.player.getCapability(ModCapabilities.SPELL_CASTER).ifPresent(data -> {
                ResourceLocation selected = data.getSelectedSpell();
                ModNetwork.CHANNEL.sendToServer(new RequestCastSpellPacket(selected, chargeTicks));
            });
            chargeTicks = 0;
            wasDown = false;
        }
    }

    private void cycleSpell(Minecraft mc, int direction) {
        mc.player.getCapability(ModCapabilities.SPELL_CASTER).ifPresent(data -> {
            List<ResourceLocation> ids = new ArrayList<>();
            ModSpells.all().forEach(spell -> ids.add(spell.getId()));
            if (ids.isEmpty()) return;
            int idx = ids.indexOf(data.getSelectedSpell());
            idx = (idx + direction + ids.size()) % ids.size();
            data.setSelectedSpell(ids.get(idx));
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "spell.magicmod." + ids.get(idx).getPath()), true);
            }
        });
    }
}
