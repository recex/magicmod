package com.arcanemod.magicmod.spell.impl;

import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellCastContext;
import com.arcanemod.magicmod.spell.SpellCastResult;
import com.arcanemod.magicmod.spell.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;

/**
 * Implementacao generica e configuravel de magia usada por grande
 * parte das 30 magias: dano direto no alvo mirado (ou em area ao
 * redor do ponto de impacto), com efeito de status opcional aplicado.
 * Isso evita precisar de uma classe Java dedicada para cada magia
 * simples de dano/debuff - apenas magias com logica especial
 * (invocar entidade, teletransporte, cura, etc) ganham subclasses
 * proprias mais abaixo neste pacote.
 */
public class SimpleDamageSpell extends Spell {

    private final float damage;
    private final float areaRadius;
    private final Supplier<MobEffectInstance> effect;
    private final String visualProfileId;

    public SimpleDamageSpell(ResourceLocation id, SpellSchool school, int manaCost, int cooldownTicks, int tier,
                              float damage, float areaRadius, Supplier<MobEffectInstance> effect) {
        super(id, school, manaCost, cooldownTicks, tier);
        this.damage = damage;
        this.areaRadius = areaRadius;
        this.effect = effect;
        this.visualProfileId = id.getPath();
    }

    @Override
    public SpellCastResult cast(Level level, Player caster, SpellCastContext context) {
        HitResult hit = context.getTargetHit();
        Vec3 point;

        if (hit instanceof EntityHitResult eh) {
            point = eh.getLocation();
        } else if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            point = hit.getLocation();
        } else {
            point = caster.getEyePosition().add(caster.getLookAngle().scale(8));
        }

        if (areaRadius <= 0 && hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity target) {
            applyDamage(caster, target);
            return SpellCastResult.SUCCESS;
        }

        AABB area = new AABB(point, point).inflate(Math.max(1.0, areaRadius));
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != caster && e.isAlive());

        if (targets.isEmpty() && areaRadius <= 0) {
            return SpellCastResult.NO_VALID_TARGET;
        }

        for (LivingEntity target : targets) {
            applyDamage(caster, target);
        }

        return SpellCastResult.SUCCESS;
    }

    private void applyDamage(Player caster, LivingEntity target) {
        target.hurt(caster.damageSources().indirectMagic(caster, caster), damage);
        if (effect != null) {
            MobEffectInstance instance = effect.get();
            if (instance != null) {
                target.addEffect(new MobEffectInstance(instance));
            }
        }
    }

    @Override
    public String getVisualProfileId() {
        return visualProfileId;
    }
}
