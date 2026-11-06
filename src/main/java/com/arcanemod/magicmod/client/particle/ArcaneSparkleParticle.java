package com.arcanemod.magicmod.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * Particula generica com fade suave in/out, leve rotacao e gravidade
 * configuravel. Base visual compartilhada por praticamente todas as
 * 30 magias (trilhas, impactos, auras, runas).
 */
public class ArcaneSparkleParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float baseScale;

    protected ArcaneSparkleParticle(ClientLevel level, double x, double y, double z,
                                     double dx, double dy, double dz,
                                     float r, float g, float b, float scale, int lifetime,
                                     SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.baseScale = scale;
        this.quadSize = scale;
        this.lifetime = Math.max(1, lifetime);
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.96f;
        this.setSpriteFromAge(sprites);
        this.roll = (float) (Math.random() * Math.PI * 2);
        this.oRoll = this.roll;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);

        float lifeRatio = (float) age / (float) lifetime;
        if (lifeRatio < 0.15f) {
            alpha = Mth.clamp(lifeRatio / 0.15f, 0f, 1f);
        } else if (lifeRatio > 0.6f) {
            alpha = Mth.clamp(1.0f - (lifeRatio - 0.6f) / 0.4f, 0f, 1f);
        } else {
            alpha = 1.0f;
        }

        quadSize = baseScale * (0.7f + 0.3f * (1.0f - lifeRatio));
        oRoll = roll;
        roll += 0.08f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class SparkleProvider implements ParticleProvider<ArcaneSparkleParticleOption> {
        private final SpriteSet sprites;

        public SparkleProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ArcaneSparkleParticleOption options, ClientLevel level,
                                        double x, double y, double z,
                                        double dx, double dy, double dz) {
            return new ArcaneSparkleParticle(level, x, y, z, dx, dy, dz,
                    options.getR(), options.getG(), options.getB(),
                    options.getScale(), options.getLifetimeTicks(), sprites);
        }
    }
}
