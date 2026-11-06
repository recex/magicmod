package com.arcanemod.magicmod.client;

import com.arcanemod.magicmod.client.particle.ArcaneSparkleParticleOption;
import com.arcanemod.magicmod.client.shader.ShaderEffectManager;
import com.arcanemod.magicmod.network.SpellVfxPacket;
import com.arcanemod.magicmod.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * Le o VisualProfile associado ao id da magia e produz os efeitos
 * (particulas de impacto, shockwave em anel, trilha de cast, shader
 * de pos-processamento) de forma generica. Isso e o que permite que
 * as 30 magias compartilhem um unico pipeline visual robusto,
 * variando apenas os parametros do perfil.
 */
public class SpellVfxDispatcher {

    public static void dispatch(SpellVfxPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        VisualProfile profile = VisualProfiles.get(msg.spellId.getPath());

        spawnImpactBurst(level, msg.x, msg.y, msg.z, profile);

        if (profile.ringShockwave) {
            spawnRingShockwave(level, msg.x, msg.y, msg.z, profile);
        }

        if (profile.groundCrater) {
            spawnGroundCrater(level, msg.x, msg.y, msg.z, profile);
        }

        if (profile.beamEffect) {
            Entity caster = level.getEntity(msg.casterEntityId);
            if (caster != null) {
                spawnBeam(level, caster.getX(), caster.getEyeY(), caster.getZ(), msg.x, msg.y, msg.z, profile);
            }
        }

        if (profile.starfallEffect) {
            spawnStarfall(level, msg.x, msg.y, msg.z, profile);
        }

        if (profile.fullscreenShader) {
            if (profile.singularityShader) {
                ShaderEffectManager.get().triggerSingularity(profile.shaderDurationTicks, profile.r, profile.g, profile.b);
            } else {
                ShaderEffectManager.get().triggerSpellDistortion(profile.shaderDurationTicks, profile.r, profile.g, profile.b);
            }
        }

        level.playLocalSound(msg.x, msg.y, msg.z,
                net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f, false);
    }

    private static void spawnImpactBurst(ClientLevel level, double x, double y, double z, VisualProfile profile) {
        for (int i = 0; i < profile.impactParticleCount; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double speed = 0.1 + level.random.nextDouble() * 0.35;
            double dx = Math.cos(angle) * speed;
            double dz = Math.sin(angle) * speed;
            double dy = 0.1 + level.random.nextDouble() * 0.4;

            level.addParticle(
                    new ArcaneSparkleParticleOption(ModParticles.ARCANE_SPARKLE.get(),
                            profile.r, profile.g, profile.b, profile.particleScale, 30 + level.random.nextInt(20)),
                    x, y + 0.1, z, dx, dy, dz
            );
        }
    }

    private static void spawnRingShockwave(ClientLevel level, double x, double y, double z, VisualProfile profile) {
        int points = 48;
        double radius = 0.3;
        for (int ring = 0; ring < 3; ring++) {
            double r = radius + ring * 0.8;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2 * i) / points;
                double px = x + Math.cos(angle) * r;
                double pz = z + Math.sin(angle) * r;
                level.addParticle(
                        new ArcaneSparkleParticleOption(ModParticles.IMPACT_SHOCKWAVE.get(),
                                profile.r, profile.g, profile.b, 0.6f, 15),
                        px, y + 0.05, pz,
                        Math.cos(angle) * 0.15, 0.02, Math.sin(angle) * 0.15
                );
            }
        }
    }

    private static void spawnGroundCrater(ClientLevel level, double x, double y, double z, VisualProfile profile) {
        for (int i = 0; i < 20; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 2.0;
            double oz = (level.random.nextDouble() - 0.5) * 2.0;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    x + ox, y, z + oz, 0, 0.05, 0);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA,
                    x + ox * 0.5, y + 0.1, z + oz * 0.5, 0, 0.1, 0);
        }
    }

    /**
     * Gera pontos de particula caindo do ceu em um raio ao redor do
     * impacto, simulando uma chuva de estrelas/meteoros caindo em
     * area. Usado por magias como Starfall.
     */
    private static void spawnStarfall(ClientLevel level, double x, double y, double z, VisualProfile profile) {
        int streaks = 14;
        double radius = 4.0;
        for (int i = 0; i < streaks; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 2.0 * radius;
            double oz = (level.random.nextDouble() - 0.5) * 2.0 * radius;
            double startY = y + 10 + level.random.nextDouble() * 6;
            double px = x + ox;
            double pz = z + oz;

            int trailPoints = 10;
            for (int t = 0; t < trailPoints; t++) {
                double frac = t / (double) trailPoints;
                level.addParticle(
                        new ArcaneSparkleParticleOption(ModParticles.MANA_TRAIL.get(),
                                profile.r, profile.g, profile.b, profile.particleScale * 0.7f, 20),
                        px, startY - frac * 4, pz,
                        0, -0.25, 0
                );
            }
        }
    }

    private static void spawnBeam(ClientLevel level, double x1, double y1, double z1,
                                   double x2, double y2, double z2, VisualProfile profile) {
        double dist = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        int steps = Math.max(4, (int) (dist * 4));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double px = x1 + (x2 - x1) * t;
            double py = y1 + (y2 - y1) * t;
            double pz = z1 + (z2 - z1) * t;
            level.addParticle(
                    new ArcaneSparkleParticleOption(ModParticles.MANA_TRAIL.get(),
                            profile.r, profile.g, profile.b, profile.particleScale * 0.5f, 8),
                    px, py, pz, 0, 0, 0
            );
        }
    }
}
