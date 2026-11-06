package com.arcanemod.magicmod.spell.impl;

import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellCastContext;
import com.arcanemod.magicmod.spell.SpellCastResult;
import com.arcanemod.magicmod.spell.SpellSchool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Lanca um projetil vanilla (reaproveitando SmallFireball como base
 * fisica confiavel e ja testada pelo jogo) na direcao do olhar do
 * jogador. O rastro visual real (particulas customizadas seguindo o
 * projetil) e desenhado no cliente a partir do pacote de VFX;
 * aqui cuidamos apenas da fisica/dano no servidor.
 */
public class SummonProjectileSpell extends Spell {

    private final float speed;
    private final float explosionPower;

    public SummonProjectileSpell(ResourceLocation id, SpellSchool school, int manaCost, int cooldownTicks, int tier,
                                  float speed, float explosionPower) {
        super(id, school, manaCost, cooldownTicks, tier);
        this.speed = speed;
        this.explosionPower = explosionPower;
    }

    @Override
    public SpellCastResult cast(Level level, Player caster, SpellCastContext context) {
        Vec3 look = caster.getLookAngle();
        // Nota: SmallFireball nao expoe um setter publico de poder de
        // explosao no mapeamento oficial; o campo "explosionPower" desta
        // instancia fica guardado so para referencia futura/VFX, o dano
        // de impacto usa o comportamento vanilla padrao do projetil.
        SmallFireball fireball = new SmallFireball(level, caster,
                look.x * speed, look.y * speed, look.z * speed);
        fireball.setPos(caster.getX(), caster.getEyeY() - 0.2, caster.getZ());
        level.addFreshEntity(fireball);
        return SpellCastResult.SUCCESS;
    }

    @Override
    public String getVisualProfileId() {
        return getId().getPath();
    }
}
