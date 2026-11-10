package com.arcanemod.magicmod.engine.math;

import java.util.Locale;

/**
 * Funcoes de easing (suavizacao) usadas por curvas, linhas do tempo, camera e
 * efeitos. Todas recebem {@code t} em [0, 1] e devolvem o progresso
 * suavizado; as variantes BACK e ELASTIC passam de proposito um pouco dos
 * limites para dar "peso" ao movimento.
 */
public enum Easing {
    LINEAR, STEP, SMOOTHSTEP, SMOOTHERSTEP,
    IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
    IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
    IN_QUART, OUT_QUART, IN_OUT_QUART,
    IN_QUINT, OUT_QUINT, IN_OUT_QUINT,
    IN_SINE, OUT_SINE, IN_OUT_SINE,
    IN_EXPO, OUT_EXPO, IN_OUT_EXPO,
    IN_CIRC, OUT_CIRC, IN_OUT_CIRC,
    IN_BACK, OUT_BACK, IN_OUT_BACK,
    IN_ELASTIC, OUT_ELASTIC, IN_OUT_ELASTIC,
    IN_BOUNCE, OUT_BOUNCE, IN_OUT_BOUNCE;

    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C2 = BACK_C1 * 1.525f;
    private static final float BACK_C3 = BACK_C1 + 1.0f;
    private static final float ELASTIC_C4 = (float) (2.0 * Math.PI / 3.0);
    private static final float ELASTIC_C5 = (float) (2.0 * Math.PI / 4.5);

    /** Aplica a funcao ao progresso {@code t}, que e limitado a [0, 1]. */
    public float apply(float t) {
        if (t <= 0.0f) {
            return 0.0f;
        }
        if (t >= 1.0f) {
            return 1.0f;
        }
        switch (this) {
            case STEP: return 0.0f;
            case SMOOTHSTEP: return t * t * (3.0f - 2.0f * t);
            case SMOOTHERSTEP: return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
            case IN_QUAD: return t * t;
            case OUT_QUAD: return 1.0f - (1.0f - t) * (1.0f - t);
            case IN_OUT_QUAD: return inOut(t, 2.0f);
            case IN_CUBIC: return t * t * t;
            case OUT_CUBIC: return 1.0f - pow(1.0f - t, 3.0f);
            case IN_OUT_CUBIC: return inOut(t, 3.0f);
            case IN_QUART: return t * t * t * t;
            case OUT_QUART: return 1.0f - pow(1.0f - t, 4.0f);
            case IN_OUT_QUART: return inOut(t, 4.0f);
            case IN_QUINT: return t * t * t * t * t;
            case OUT_QUINT: return 1.0f - pow(1.0f - t, 5.0f);
            case IN_OUT_QUINT: return inOut(t, 5.0f);
            case IN_SINE: return 1.0f - (float) Math.cos(t * Math.PI / 2.0);
            case OUT_SINE: return (float) Math.sin(t * Math.PI / 2.0);
            case IN_OUT_SINE: return -((float) Math.cos(Math.PI * t) - 1.0f) / 2.0f;
            case IN_EXPO: return pow(2.0f, 10.0f * t - 10.0f);
            case OUT_EXPO: return 1.0f - pow(2.0f, -10.0f * t);
            case IN_OUT_EXPO:
                return t < 0.5f ? pow(2.0f, 20.0f * t - 10.0f) / 2.0f : (2.0f - pow(2.0f, -20.0f * t + 10.0f)) / 2.0f;
            case IN_CIRC: return 1.0f - (float) Math.sqrt(1.0f - t * t);
            case OUT_CIRC: return (float) Math.sqrt(1.0f - (t - 1.0f) * (t - 1.0f));
            case IN_OUT_CIRC:
                return t < 0.5f
                        ? (1.0f - (float) Math.sqrt(1.0f - 4.0f * t * t)) / 2.0f
                        : ((float) Math.sqrt(1.0f - pow(-2.0f * t + 2.0f, 2.0f)) + 1.0f) / 2.0f;
            case IN_BACK: return BACK_C3 * t * t * t - BACK_C1 * t * t;
            case OUT_BACK: {
                float u = t - 1.0f;
                return 1.0f + BACK_C3 * u * u * u + BACK_C1 * u * u;
            }
            case IN_OUT_BACK: {
                if (t < 0.5f) {
                    float u = 2.0f * t;
                    return (u * u * ((BACK_C2 + 1.0f) * u - BACK_C2)) / 2.0f;
                }
                float u = 2.0f * t - 2.0f;
                return (u * u * ((BACK_C2 + 1.0f) * u + BACK_C2) + 2.0f) / 2.0f;
            }
            case IN_ELASTIC:
                return -pow(2.0f, 10.0f * t - 10.0f) * (float) Math.sin((t * 10.0f - 10.75f) * ELASTIC_C4);
            case OUT_ELASTIC:
                return pow(2.0f, -10.0f * t) * (float) Math.sin((t * 10.0f - 0.75f) * ELASTIC_C4) + 1.0f;
            case IN_OUT_ELASTIC: {
                float s = (float) Math.sin((20.0f * t - 11.125f) * ELASTIC_C5);
                return t < 0.5f
                        ? -(pow(2.0f, 20.0f * t - 10.0f) * s) / 2.0f
                        : (pow(2.0f, -20.0f * t + 10.0f) * s) / 2.0f + 1.0f;
            }
            case IN_BOUNCE: return 1.0f - bounceOut(1.0f - t);
            case OUT_BOUNCE: return bounceOut(t);
            case IN_OUT_BOUNCE:
                return t < 0.5f ? (1.0f - bounceOut(1.0f - 2.0f * t)) / 2.0f : (1.0f + bounceOut(2.0f * t - 1.0f)) / 2.0f;
            case LINEAR:
            default:
                return t;
        }
    }

    /** Interpola {@code a} e {@code b} com esta funcao. */
    public float lerp(float a, float b, float t) {
        return a + (b - a) * apply(t);
    }

    /** Procura pelo nome ("out_cubic", "outCubic", "OUT-CUBIC"...); LINEAR se desconhecido. */
    public static Easing byName(String name) {
        if (name == null || name.isEmpty()) {
            return LINEAR;
        }
        String key = name.replaceAll("([a-z])([A-Z])", "$1_$2").replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        for (Easing e : values()) {
            if (e.name().equals(key)) {
                return e;
            }
        }
        return LINEAR;
    }

    private static float pow(float base, float exp) {
        return (float) Math.pow(base, exp);
    }

    private static float inOut(float t, float power) {
        return t < 0.5f ? pow(2.0f, power - 1.0f) * pow(t, power) : 1.0f - pow(-2.0f * t + 2.0f, power) / 2.0f;
    }

    private static float bounceOut(float t) {
        final float n1 = 7.5625f;
        final float d1 = 2.75f;
        if (t < 1.0f / d1) {
            return n1 * t * t;
        } else if (t < 2.0f / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5f / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        }
        t -= 2.625f / d1;
        return n1 * t * t + 0.984375f;
    }
}
