package com.arcanemod.magicmod.spell;

import net.minecraft.resources.ResourceLocation;

/**
 * Contrato de dados de um lancador de magias: mana atual/maxima e
 * cooldowns por magia. Implementado pela capability do jogador.
 */
public interface SpellCasterData {

    int getMana();

    int getMaxMana();

    void setMana(int mana);

    default void addMana(int amount) {
        setMana(Math.min(getMaxMana(), getMana() + amount));
    }

    default void consumeMana(int amount) {
        setMana(Math.max(0, getMana() - amount));
    }

    boolean isOnCooldown(ResourceLocation spellId);

    int getRemainingCooldown(ResourceLocation spellId);

    void setCooldown(ResourceLocation spellId, int ticks);

    void tickCooldowns();

    ResourceLocation getSelectedSpell();

    void setSelectedSpell(ResourceLocation id);
}
