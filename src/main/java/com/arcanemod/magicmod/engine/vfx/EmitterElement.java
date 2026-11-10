package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.particle.Burst;
import com.arcanemod.magicmod.engine.particle.GpuParticleSystem;
import com.arcanemod.magicmod.engine.render.FrameContext;

import java.util.Random;

/**
 * Emissor continuo: solta particulas de uma receita a uma taxa (particulas por
 * segundo) que varia ao longo da vida. Como todo elemento, pode seguir uma
 * entidade ou ser animado.
 */
public final class EmitterElement extends VfxElement<EmitterElement> {

    private final GpuParticleSystem system;
    private final Burst spec;
    private final Random random;
    public Curve rate = Curve.constant(30.0f);
    private float pending;

    public EmitterElement(GpuParticleSystem system, Burst spec, Random random) {
        this.system = system;
        this.spec = spec;
        this.random = random;
    }

    @Override
    protected EmitterElement self() {
        return this;
    }

    public EmitterElement rate(Curve particlesPerSecond) {
        this.rate = particlesPerSecond;
        return this;
    }

    public EmitterElement rate(float particlesPerSecond) {
        this.rate = Curve.constant(particlesPerSecond);
        return this;
    }

    @Override
    public float boundRadius() {
        return 1.0e6f;
    }

    @Override
    protected void onUpdate(FrameContext ctx, float dt) {
        pending += Math.max(0.0f, rate.sample(progress())) * dt * ctx.quality.particleScale;
        int n = (int) pending;
        if (n > 0) {
            pending -= n;
            spec.emit(system, ctx.time, x, y, z, random, Math.min(n, 512), dt);
        }
    }
}
