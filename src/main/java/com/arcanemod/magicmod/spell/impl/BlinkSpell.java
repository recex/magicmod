package com.arcanemod.magicmod.spell.impl;

import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellCastContext;
import com.arcanemod.magicmod.spell.SpellCastResult;
import com.arcanemod.magicmod.spell.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BlinkSpell extends Spell {

    private final double distance;

    public BlinkSpell(ResourceLocation id, SpellSchool school, int manaCost, int cooldownTicks, int tier, double distance) {
        super(id, school, manaCost, cooldownTicks, tier);
        this.distance = distance;
    }

    @Override
    public SpellCastResult cast(Level level, Player caster, SpellCastContext context) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = start.add(look.scale(distance));

        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 destination = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(look.scale(0.5));

        caster.teleportTo(destination.x, destination.y, destination.z);
        return SpellCastResult.SUCCESS;
    }

    @Override
    public String getVisualProfileId() {
        return getId().getPath();
    }
}
