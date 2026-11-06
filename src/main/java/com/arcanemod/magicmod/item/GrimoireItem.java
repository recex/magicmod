package com.arcanemod.magicmod.item;

import com.arcanemod.magicmod.capability.ModCapabilities;
import com.arcanemod.magicmod.registry.ModSpells;
import com.arcanemod.magicmod.spell.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Item simples que, ao usar, mostra na barra de acao qual das 30
 * magias esta selecionada e seu custo/cooldown. A selecao em si e
 * trocada pelas teclas NEXT/PREV_SPELL (ver KeyBindings).
 */
public class GrimoireItem extends Item {

    public GrimoireItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            player.getCapability(ModCapabilities.SPELL_CASTER).ifPresent(data -> {
                Spell spell = ModSpells.get(data.getSelectedSpell());
                if (spell != null) {
                    player.displayClientMessage(Component.translatable(spell.getTranslationKey())
                            .append(Component.literal(" - " + spell.getManaCost() + " mana")), true);
                }
            });
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
