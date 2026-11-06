package com.arcanemod.magicmod.spell.impl;

import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellCastContext;
import com.arcanemod.magicmod.spell.SpellCastResult;
import com.arcanemod.magicmod.spell.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

/** Magias que aplicam efeitos de status no proprio lancador (buffs). */
public class SelfBuffSpell extends Spell {

    private final List<Supplier<MobEffectInstance>> effects;
    private final float healAmount;

    public SelfBuffSpell(ResourceLocation id, SpellSchool school, int manaCost, int cooldownTicks, int tier,
                          List<Supplier<MobEffectInstance>> effects, float healAmount) {
        super(id, school, manaCost, cooldownTicks, tier);
        this.effects = effects;
        this.healAmount = healAmount;
    }

    @Override
    public SpellCastResult cast(Level level, Player caster, SpellCastContext context) {
        for (Supplier<MobEffectInstance> e : effects) {
            caster.addEffect(new MobEffectInstance(e.get()));
        }
        if (healAmount > 0) {
            caster.heal(healAmount);
        }
        return SpellCastResult.SUCCESS;
    }

    @Override
    public String getVisualProfileId() {
        return getId().getPath();
    }
}
