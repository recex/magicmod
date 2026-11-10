package com.arcanemod.magicmod.engine.math;

/**
 * Curva de tempo Bezier cubica no estilo {@code cubic-bezier(x1, y1, x2, y2)}:
 * os pontos extremos sao fixos em (0,0) e (1,1) e os dois pontos de controle
 * moldam a aceleracao. Dado o progresso {@code x}, resolve o parametro da
 * curva (Newton com queda para bissecao) e devolve {@code y}.
 */
public final class CubicBezier {

    public static final CubicBezier EASE = new CubicBezier(0.25f, 0.1f, 0.25f, 1.0f);
    public static final CubicBezier EASE_IN = new CubicBezier(0.42f, 0.0f, 1.0f, 1.0f);
    public static final CubicBezier EASE_OUT = new CubicBezier(0.0f, 0.0f, 0.58f, 1.0f);
    public static final CubicBezier EASE_IN_OUT = new CubicBezier(0.42f, 0.0f, 0.58f, 1.0f);
    /** Arranque seco e chegada longa: bom para cortes de camera de impacto. */
    public static final CubicBezier CINEMATIC = new CubicBezier(0.16f, 1.0f, 0.3f, 1.0f);

    private final float ax, bx, cx;
    private final float ay, by, cy;

    public CubicBezier(float x1, float y1, float x2, float y2) {
        float cx1 = MathUtil.clamp(x1, 0.0f, 1.0f);
        float cx2 = MathUtil.clamp(x2, 0.0f, 1.0f);
        this.cx = 3.0f * cx1;
        this.bx = 3.0f * (cx2 - cx1) - cx;
        this.ax = 1.0f - cx - bx;
        this.cy = 3.0f * y1;
        this.by = 3.0f * (y2 - y1) - cy;
        this.ay = 1.0f - cy - by;
    }

    private float sampleX(float t) {
        return ((ax * t + bx) * t + cx) * t;
    }

    private float sampleY(float t) {
        return ((ay * t + by) * t + cy) * t;
    }

    private float sampleDerivativeX(float t) {
        return (3.0f * ax * t + 2.0f * bx) * t + cx;
    }

    /** Resolve o parametro {@code t} tal que x(t) = {@code x}. */
    private float solveT(float x) {
        float t = x;
        for (int i = 0; i < 8; i++) {
            float error = sampleX(t) - x;
            if (Math.abs(error) < 1.0e-6f) {
                return t;
            }
            float d = sampleDerivativeX(t);
            if (Math.abs(d) < 1.0e-6f) {
                break;
            }
            t -= error / d;
        }
        float lo = 0.0f;
        float hi = 1.0f;
        t = x;
        for (int i = 0; i < 32 && lo < hi; i++) {
            float value = sampleX(t);
            if (Math.abs(value - x) < 1.0e-6f) {
                break;
            }
            if (x > value) {
                lo = t;
            } else {
                hi = t;
            }
            t = (hi - lo) * 0.5f + lo;
        }
        return t;
    }

    /** Progresso suavizado para {@code x} em [0, 1]. */
    public float evaluate(float x) {
        if (x <= 0.0f) {
            return 0.0f;
        }
        if (x >= 1.0f) {
            return 1.0f;
        }
        return sampleY(solveT(x));
    }
}
