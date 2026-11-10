package com.arcanemod.magicmod.engine.particle;

import com.arcanemod.magicmod.engine.math.MathUtil;

import java.util.Random;

/**
 * Receita de uma rajada de particulas: quantas, de onde saem, para onde vao e
 * como envelhecem. Imutavel na pratica (configure uma vez e reutilize); a
 * aleatoriedade vem do gerador passado a {@link #emit}.
 */
public final class Burst {

    /** Estilos de sprite (ver particle.fsh). */
    public static final int GLOW = 0;
    public static final int SPARK = 1;
    public static final int SMOKE = 2;
    public static final int STAR = 3;
    public static final int SHARD = 4;
    public static final int RING = 5;
    public static final int LEAF = 6;
    public static final int FLAME = 7;

    private int count = 16;
    private float speedMin = 1.0f;
    private float speedMax = 2.0f;
    private float lifeMin = 0.6f;
    private float lifeMax = 1.0f;
    private float sizeMin = 0.08f;
    private float sizeMax = 0.12f;
    private float sizeEnd = 0.3f;
    private float drag;
    private float gravity;
    private final float[] color0 = {1.0f, 1.0f, 1.0f, 0.0f};
    private final float[] color1 = {1.0f, 1.0f, 1.0f, 0.0f};
    private int style = GLOW;
    private float stretch;
    private float turbulence;
    private float spawnRadius;
    private boolean spawnOnShell;
    private boolean spawnOnRing;
    private float upBias;
    private float flatten = 1.0f;
    private float coneX;
    private float coneY = 1.0f;
    private float coneZ;
    private float coneAngle = -1.0f;
    private boolean hasTarget;
    private double targetX;
    private double targetY;
    private double targetZ;
    private boolean targetAbsolute;
    private float attract;
    private float swirl;
    private float fadeIn;
    private float fadeOut = 0.6f;
    private float flicker;
    private float spread;
    private float colorJitter;

    public static Burst of(int count) {
        Burst b = new Burst();
        b.count = count;
        return b;
    }

    public Burst style(int style) {
        this.style = style;
        return this;
    }

    public Burst speed(float min, float max) {
        this.speedMin = min;
        this.speedMax = max;
        return this;
    }

    public Burst life(float min, float max) {
        this.lifeMin = min;
        this.lifeMax = max;
        return this;
    }

    /** Tamanho inicial (faixa) e fator do tamanho final em relacao ao inicial. */
    public Burst size(float min, float max, float endFactor) {
        this.sizeMin = min;
        this.sizeMax = max;
        this.sizeEnd = endFactor;
        return this;
    }

    public Burst physics(float drag, float gravity) {
        this.drag = drag;
        this.gravity = gravity;
        return this;
    }

    /** Cor inicial (HDR) e opacidade: 0 = luz aditiva, 1 = encobre o fundo. */
    public Burst color(float r, float g, float b, float opacity) {
        color0[0] = r;
        color0[1] = g;
        color0[2] = b;
        color0[3] = opacity;
        return this;
    }

    public Burst colorEnd(float r, float g, float b, float opacity) {
        color1[0] = r;
        color1[1] = g;
        color1[2] = b;
        color1[3] = opacity;
        return this;
    }

    /** Variacao aleatoria de brilho entre particulas (0..1). */
    public Burst colorJitter(float amount) {
        this.colorJitter = amount;
        return this;
    }

    public Burst stretch(float amount) {
        this.stretch = amount;
        return this;
    }

    public Burst turbulence(float amount) {
        this.turbulence = amount;
        return this;
    }

    /** Nascem dentro de uma esfera deste raio. */
    public Burst spawnSphere(float radius) {
        this.spawnRadius = radius;
        this.spawnOnShell = false;
        this.spawnOnRing = false;
        return this;
    }

    /** Nascem na superficie de uma esfera deste raio (implosoes). */
    public Burst spawnShell(float radius) {
        this.spawnRadius = radius;
        this.spawnOnShell = true;
        this.spawnOnRing = false;
        return this;
    }

    /** Nascem num anel horizontal deste raio. */
    public Burst spawnRing(float radius) {
        this.spawnRadius = radius;
        this.spawnOnRing = true;
        this.spawnOnShell = false;
        return this;
    }

    /** 0 = todas as direcoes; 1 = so para cima. */
    public Burst upBias(float amount) {
        this.upBias = amount;
        return this;
    }

    /** Achata as direcoes na vertical (0 = disco horizontal, 1 = esfera). */
    public Burst flatten(float amount) {
        this.flatten = amount;
        return this;
    }

    /** Direcoes dentro de um cone em torno do eixo dado. */
    public Burst cone(float ax, float ay, float az, float halfAngleRadians) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len > 1.0e-6f) {
            coneX = ax / len;
            coneY = ay / len;
            coneZ = az / len;
            coneAngle = halfAngleRadians;
        }
        return this;
    }

    /** Alvo relativo ao ponto de emissao; {@code exponent} maior = chegada mais brusca. */
    public Burst attractTo(double dx, double dy, double dz, float exponent) {
        hasTarget = true;
        targetAbsolute = false;
        targetX = dx;
        targetY = dy;
        targetZ = dz;
        attract = exponent;
        return this;
    }

    /** Alvo em coordenadas do mundo. */
    public Burst attractToWorld(double x, double y, double z, float exponent) {
        hasTarget = true;
        targetAbsolute = true;
        targetX = x;
        targetY = y;
        targetZ = z;
        attract = exponent;
        return this;
    }

    /** Voltas em torno do eixo vertical que passa pelo alvo (ou pelo ponto de emissao). */
    public Burst swirl(float turns) {
        this.swirl = turns;
        return this;
    }

    public Burst fade(float fadeIn, float fadeOutStart) {
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOutStart;
        return this;
    }

    public Burst flicker(float amount) {
        this.flicker = amount;
        return this;
    }

    /** Espalha os nascimentos ao longo de {@code seconds}. */
    public Burst over(float seconds) {
        this.spread = seconds;
        return this;
    }

    public int count() {
        return count;
    }

    /** Emite a rajada em (x, y, z). {@code scale} multiplica a quantidade (qualidade/orcamento). */
    public void emit(GpuParticleSystem system, float time, double x, double y, double z, Random rnd, float scale) {
        emit(system, time, x, y, z, rnd, Math.max(1, Math.round(count * scale)), spread);
    }

    /** Emite exatamente {@code n} particulas, nascendo dentro de {@code window} segundos. */
    public void emit(GpuParticleSystem system, float time, double x, double y, double z, Random rnd, int n, float window) {
        Particle p = SCRATCH.get();
        for (int i = 0; i < n; i++) {
            float dx;
            float dy;
            float dz;
            if (coneAngle >= 0.0f) {
                // Direcao uniforme dentro do cone.
                float cos = MathUtil.lerp(1.0f, (float) Math.cos(coneAngle), rnd.nextFloat());
                float sin = (float) Math.sqrt(Math.max(0.0f, 1.0f - cos * cos));
                float phi = rnd.nextFloat() * MathUtil.TAU;
                float ux = Math.abs(coneY) < 0.99f ? 0.0f : 1.0f;
                float uy = Math.abs(coneY) < 0.99f ? 1.0f : 0.0f;
                float tx = uy * coneZ;
                float ty = -ux * coneZ;
                float tz = ux * coneY - uy * coneX;
                float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                tx /= tl;
                ty /= tl;
                tz /= tl;
                float bx = coneY * tz - coneZ * ty;
                float by = coneZ * tx - coneX * tz;
                float bz = coneX * ty - coneY * tx;
                float c = (float) Math.cos(phi) * sin;
                float s = (float) Math.sin(phi) * sin;
                dx = coneX * cos + tx * c + bx * s;
                dy = coneY * cos + ty * c + by * s;
                dz = coneZ * cos + tz * c + bz * s;
            } else {
                float zz = rnd.nextFloat() * 2.0f - 1.0f;
                float phi = rnd.nextFloat() * MathUtil.TAU;
                float rr = (float) Math.sqrt(Math.max(0.0f, 1.0f - zz * zz));
                dx = rr * (float) Math.cos(phi);
                dz = rr * (float) Math.sin(phi);
                dy = zz * flatten;
                if (upBias > 0.0f) {
                    dy = MathUtil.lerp(dy, Math.abs(dy) + 0.15f, upBias);
                }
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len > 1.0e-5f) {
                    dx /= len;
                    dy /= len;
                    dz /= len;
                }
            }
            float sx = 0.0f;
            float sy = 0.0f;
            float sz = 0.0f;
            if (spawnRadius > 0.0f) {
                if (spawnOnRing) {
                    float a = rnd.nextFloat() * MathUtil.TAU;
                    sx = (float) Math.cos(a) * spawnRadius;
                    sz = (float) Math.sin(a) * spawnRadius;
                } else {
                    float zz = rnd.nextFloat() * 2.0f - 1.0f;
                    float phi = rnd.nextFloat() * MathUtil.TAU;
                    float rr = (float) Math.sqrt(Math.max(0.0f, 1.0f - zz * zz));
                    float radius = spawnOnShell ? spawnRadius : spawnRadius * (float) Math.cbrt(rnd.nextFloat());
                    sx = rr * (float) Math.cos(phi) * radius;
                    sy = zz * radius;
                    sz = rr * (float) Math.sin(phi) * radius;
                }
            }
            float speed = MathUtil.lerp(speedMin, speedMax, rnd.nextFloat());
            float size = MathUtil.lerp(sizeMin, sizeMax, rnd.nextFloat());
            float bright = 1.0f - colorJitter * rnd.nextFloat();
            p.x = x + sx;
            p.y = y + sy;
            p.z = z + sz;
            p.vx = dx * speed;
            p.vy = dy * speed;
            p.vz = dz * speed;
            p.life = MathUtil.lerp(lifeMin, lifeMax, rnd.nextFloat());
            p.size0 = size;
            p.size1 = size * sizeEnd;
            p.drag = drag;
            p.gravity = gravity;
            p.r0 = color0[0] * bright;
            p.g0 = color0[1] * bright;
            p.b0 = color0[2] * bright;
            p.a0 = color0[3];
            p.r1 = color1[0] * bright;
            p.g1 = color1[1] * bright;
            p.b1 = color1[2] * bright;
            p.a1 = color1[3];
            p.style = style;
            p.seed = rnd.nextFloat();
            p.stretch = stretch;
            p.turbulence = turbulence;
            if (hasTarget) {
                p.tx = targetAbsolute ? targetX : x + targetX;
                p.ty = targetAbsolute ? targetY : y + targetY;
                p.tz = targetAbsolute ? targetZ : z + targetZ;
                p.attract = attract;
            } else {
                p.tx = x;
                p.ty = y;
                p.tz = z;
                p.attract = 0.0f;
            }
            p.swirl = swirl;
            p.fadeIn = fadeIn;
            p.fadeOut = fadeOut;
            p.flicker = flicker;
            p.delay = window > 0.0f ? rnd.nextFloat() * window : 0.0f;
            system.emit(p, time);
        }
    }

    private static final ThreadLocal<Particle> SCRATCH = ThreadLocal.withInitial(Particle::new);
}
