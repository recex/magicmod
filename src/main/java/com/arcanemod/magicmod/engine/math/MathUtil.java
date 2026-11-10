package com.arcanemod.magicmod.engine.math;

/** Utilitarios numericos pequenos, sem dependencia do Minecraft. */
public final class MathUtil {

    public static final float PI = (float) Math.PI;
    public static final float TAU = (float) (Math.PI * 2.0);

    private MathUtil() {
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static float saturate(float v) {
        return clamp(v, 0.0f, 1.0f);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Progresso de {@code v} entre {@code a} e {@code b}, limitado a [0, 1]. */
    public static float inverseLerp(float a, float b, float v) {
        if (a == b) {
            return v >= b ? 1.0f : 0.0f;
        }
        return saturate((v - a) / (b - a));
    }

    public static float remap(float v, float inLo, float inHi, float outLo, float outHi) {
        return lerp(outLo, outHi, inverseLerp(inLo, inHi, v));
    }

    public static float smoothstep(float edge0, float edge1, float v) {
        float t = inverseLerp(edge0, edge1, v);
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Aproximacao exponencial independente da taxa de quadros: move
     * {@code current} em direcao a {@code target}; {@code halfLife} e o tempo
     * (s) para percorrer metade da distancia restante.
     */
    public static float damp(float current, float target, float halfLife, float dt) {
        if (halfLife <= 0.0f) {
            return target;
        }
        return target + (current - target) * (float) Math.pow(0.5, dt / halfLife);
    }

    /** Menor diferenca angular (graus) de {@code from} para {@code to}, em [-180, 180). */
    public static float angleDelta(float from, float to) {
        float d = (to - from) % 360.0f;
        if (d >= 180.0f) {
            d -= 360.0f;
        }
        if (d < -180.0f) {
            d += 360.0f;
        }
        return d;
    }

    public static float lerpAngle(float from, float to, float t) {
        return from + angleDelta(from, to) * t;
    }

    /** Envelope 0 -> 1 -> 0: sobe em {@code attack}, sustenta e cai em {@code release} (fracoes de [0,1]). */
    public static float envelope(float t, float attack, float release) {
        if (t <= 0.0f || t >= 1.0f) {
            return 0.0f;
        }
        float up = attack <= 0.0f ? 1.0f : saturate(t / attack);
        float down = release <= 0.0f ? 1.0f : saturate((1.0f - t) / release);
        return Math.min(up, down);
    }

    /** Cor 0xRRGGBB para componentes lineares [0, 1]. */
    public static float[] rgb(int hex) {
        return new float[] {((hex >> 16) & 0xFF) / 255.0f, ((hex >> 8) & 0xFF) / 255.0f, (hex & 0xFF) / 255.0f};
    }
}
