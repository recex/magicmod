package com.arcanemod.magicmod.spell.impl;

import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellCastContext;
import com.arcanemod.magicmod.spell.SpellCastResult;
import com.arcanemod.magicmod.spell.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class AreaHealSpell extends Spell {

    private final float healAmount;
    private final double radius;

    public AreaHealSpell(ResourceLocation id, SpellSchool school, int manaCost, int cooldownTicks, int tier,
                          float healAmount, double radius) {
        super(id, school, manaCost, cooldownTicks, tier);
        this.healAmount = healAmount;
        this.radius = radius;
    }

    @Override
    public SpellCastResult cast(Level level, Player caster, SpellCastContext context) {
        AABB area = caster.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);
        caster.heal(healAmount);
        for (LivingEntity target : targets) {
            if (target instanceof Player || isAllyMob(target)) {
                target.heal(healAmount);
            }
        }
        return SpellCastResult.SUCCESS;
    }

    private boolean isAllyMob(LivingEntity entity) {
        return entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.CREATURE;
    }

    @Override
    public String getVisualProfileId() {
        return getId().getPath();
    }
}
