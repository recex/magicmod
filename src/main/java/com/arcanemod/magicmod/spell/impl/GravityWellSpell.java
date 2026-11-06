package com.arcanemod.magicmod.spell.impl;

import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellCastContext;
import com.arcanemod.magicmod.spell.SpellCastResult;
import com.arcanemod.magicmod.spell.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Magia "Singularidade": cria um ponto de gravidade que puxa
 * entidades proximas em direcao ao centro antes de aplicar dano.
 * Usada pela magia experimental de efeito "buraco negro" (ver
 * VisualProfile#singularityShader). A logica de gameplay (puxao +
 * dano) e comum e estavel; so o shader de tela associado a ela e
 * experimental.
 */
public class GravityWellSpell extends Spell {

    private final double radius;
    private final double pullStrength;
    private final float damage;

    public GravityWellSpell(ResourceLocation id, SpellSchool school, int manaCost, int cooldownTicks, int tier,
                             double radius, double pullStrength, float damage) {
        super(id, school, manaCost, cooldownTicks, tier);
        this.radius = radius;
        this.pullStrength = pullStrength;
        this.damage = damage;
    }

    @Override
    public SpellCastResult cast(Level level, Player caster, SpellCastContext context) {
        HitResult hit = context.getTargetHit();
        Vec3 center;

        if (hit instanceof EntityHitResult eh) {
            center = eh.getLocation();
        } else if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            center = hit.getLocation();
        } else {
            center = caster.getEyePosition().add(caster.getLookAngle().scale(10));
        }

        AABB area = new AABB(center, center).inflate(radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != caster && e.isAlive());

        for (LivingEntity target : targets) {
            Vec3 toCenter = center.subtract(target.position());
            double dist = Math.max(0.5, toCenter.length());
            Vec3 pull = toCenter.normalize().scale(pullStrength / dist);
            target.setDeltaMovement(target.getDeltaMovement().add(pull));
            target.hurtMarked = true;

            target.hurt(caster.damageSources().indirectMagic(caster, caster), damage);
        }

        return SpellCastResult.SUCCESS;
    }

    @Override
    public String getVisualProfileId() {
        return getId().getPath();
    }
}
