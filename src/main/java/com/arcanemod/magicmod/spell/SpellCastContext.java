package com.arcanemod.magicmod.spell;

import net.minecraft.world.phys.HitResult;

/**
 * Dados contextuais de um cast especifico: para onde o jogador esta
 * mirando, se e um cast carregado (hold-to-charge) e por quantos ticks.
 */
public class SpellCastContext {

    private final HitResult targetHit;
    private final int chargeTicks;

    public SpellCastContext(HitResult targetHit, int chargeTicks) {
        this.targetHit = targetHit;
        this.chargeTicks = chargeTicks;
    }

    public HitResult getTargetHit() {
        return targetHit;
    }

    public int getChargeTicks() {
        return chargeTicks;
    }

    public float getChargePercent(int maxChargeTicks) {
        if (maxChargeTicks <= 0) return 1.0f;
        return Math.min(1.0f, chargeTicks / (float) maxChargeTicks);
    }
}
