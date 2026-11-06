package com.arcanemod.magicmod.client;

/**
 * Descreve a "receita" visual de uma magia: quantas particulas de
 * impacto, se dispara shockwave de anel, se ativa shader de tela
 * cheia e por quantos ticks, cor base e intensidade. Um unico
 * dispatcher generico (SpellVfxDispatcher) le este perfil e produz
 * efeitos consistentes e configuraveis para as 30 magias, sem
 * precisar de uma classe de efeito dedicada para cada uma.
 */
public class VisualProfile {

    public final float r, g, b;
    public final int impactParticleCount;
    public final boolean ringShockwave;
    public final boolean groundCrater;
    public final boolean fullscreenShader;
    public final int shaderDurationTicks;
    public final boolean singularityShader;
    public final boolean castTrail;
    public final boolean beamEffect;
    public final boolean starfallEffect;
    public final float particleScale;
    public final String soundEvent;

    private VisualProfile(Builder b) {
        this.r = b.r;
        this.g = b.g;
        this.b = b.b;
        this.impactParticleCount = b.impactParticleCount;
        this.ringShockwave = b.ringShockwave;
        this.groundCrater = b.groundCrater;
        this.fullscreenShader = b.fullscreenShader;
        this.shaderDurationTicks = b.shaderDurationTicks;
        this.singularityShader = b.singularityShader;
        this.castTrail = b.castTrail;
        this.beamEffect = b.beamEffect;
        this.starfallEffect = b.starfallEffect;
        this.particleScale = b.particleScale;
        this.soundEvent = b.soundEvent;
    }

    public static Builder builder(int colorHex) {
        return new Builder(colorHex);
    }

    public static class Builder {
        private final float r, g, b;
        private int impactParticleCount = 40;
        private boolean ringShockwave = false;
        private boolean groundCrater = false;
        private boolean fullscreenShader = false;
        private int shaderDurationTicks = 10;
        private boolean singularityShader = false;
        private boolean castTrail = true;
        private boolean beamEffect = false;
        private boolean starfallEffect = false;
        private float particleScale = 1.0f;
        private String soundEvent = "entity.generic.explode";

        public Builder(int colorHex) {
            this.r = ((colorHex >> 16) & 0xFF) / 255f;
            this.g = ((colorHex >> 8) & 0xFF) / 255f;
            this.b = (colorHex & 0xFF) / 255f;
        }

        public Builder impactParticles(int count) { this.impactParticleCount = count; return this; }
        public Builder ringShockwave(boolean v) { this.ringShockwave = v; return this; }
        public Builder groundCrater(boolean v) { this.groundCrater = v; return this; }
        public Builder fullscreenShader(int durationTicks) { this.fullscreenShader = true; this.shaderDurationTicks = durationTicks; return this; }
        /** Usa o shader experimental de "buraco negro" em vez do shader padrao de distorcao. Implica fullscreenShader. */
        public Builder singularityShader(int durationTicks) { this.fullscreenShader = true; this.singularityShader = true; this.shaderDurationTicks = durationTicks; return this; }
        public Builder castTrail(boolean v) { this.castTrail = v; return this; }
        public Builder beamEffect(boolean v) { this.beamEffect = v; return this; }
        /** Chuva de particulas caindo do ceu em torno do ponto de impacto, tipo chuva de estrelas. */
        public Builder starfallEffect(boolean v) { this.starfallEffect = v; return this; }
        public Builder particleScale(float v) { this.particleScale = v; return this; }
        public Builder sound(String event) { this.soundEvent = event; return this; }

        public VisualProfile build() { return new VisualProfile(this); }
    }
}
