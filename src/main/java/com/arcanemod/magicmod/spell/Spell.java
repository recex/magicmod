package com.arcanemod.magicmod.spell;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Classe base para todas as magias do mod.
 * Cada magia concreta define custo de mana, cooldown, escola de magia
 * e a logica de efeito via cast(). O framework cuida de cooldown,
 * consumo de mana, som de cast e disparo de efeitos visuais no cliente
 * via pacotes de rede (ver SpellCastPacket).
 */
public abstract class Spell {

    private final ResourceLocation id;
    private final String translationKey;
    private final SpellSchool school;
    private final int manaCost;
    private final int cooldownTicks;
    private final int tier;

    protected Spell(ResourceLocation id, SpellSchool school, int manaCost, int cooldownTicks, int tier) {
        this.id = id;
        this.school = school;
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
        this.tier = tier;
        this.translationKey = "spell.magicmod." + id.getPath();
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public SpellSchool getSchool() {
        return school;
    }

    public int getManaCost() {
        return manaCost;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getTier() {
        return tier;
    }

    /**
     * Executa a logica de gameplay da magia no servidor (dano, spawn de entidade,
     * efeitos de status, etc). Efeitos puramente visuais devem ser emitidos
     * separadamente no lado cliente a partir do pacote de cast.
     */
    public abstract SpellCastResult cast(Level level, Player caster, SpellCastContext context);

    /**
     * Identificador do perfil de efeito visual usado pelo renderer do cliente
     * para decidir quais particulas/shaders disparar ao lancar esta magia.
     */
    public abstract String getVisualProfileId();

    public boolean canAfford(SpellCasterData data) {
        return data.getMana() >= manaCost && !data.isOnCooldown(id);
    }
}
