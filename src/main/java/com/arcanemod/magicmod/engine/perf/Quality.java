package com.arcanemod.magicmod.engine.perf;

/**
 * Niveis de qualidade do motor. Cada nivel fixa o custo dos itens mais caros:
 * passos de marcha de raios, resolucao do buffer de efeitos, quantidade de
 * particulas, niveis do bloom, luzes dinamicas e distorcao.
 */
public enum Quality {
    LOW(12, 0.5f, 0.35f, 3, 2, false, 4096),
    MEDIUM(18, 0.75f, 0.6f, 4, 4, true, 8192),
    HIGH(26, 1.0f, 1.0f, 5, 8, true, 16384),
    ULTRA(40, 1.0f, 1.5f, 6, 8, true, 32768);

    /** Passos de marcha de raios dos efeitos volumetricos. */
    public final int raySteps;
    /** Resolucao do buffer de efeitos em relacao a tela. */
    public final float bufferScale;
    /** Multiplicador da quantidade de particulas emitidas. */
    public final float particleScale;
    public final int bloomLevels;
    public final int maxLights;
    public final boolean distortion;
    public final int particleCapacity;

    Quality(int raySteps, float bufferScale, float particleScale, int bloomLevels, int maxLights, boolean distortion, int particleCapacity) {
        this.raySteps = raySteps;
        this.bufferScale = bufferScale;
        this.particleScale = particleScale;
        this.bloomLevels = bloomLevels;
        this.maxLights = maxLights;
        this.distortion = distortion;
        this.particleCapacity = particleCapacity;
    }

    public Quality lower() {
        return this == LOW ? LOW : values()[ordinal() - 1];
    }

    public Quality higher() {
        return this == ULTRA ? ULTRA : values()[ordinal() + 1];
    }

    public static Quality byName(String name, Quality fallback) {
        if (name != null) {
            for (Quality q : values()) {
                if (q.name().equalsIgnoreCase(name.trim())) {
                    return q;
                }
            }
        }
        return fallback;
    }
}
