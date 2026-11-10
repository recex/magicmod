package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.math.Noise;
import com.arcanemod.magicmod.engine.vfx.StripElement.StripPath;

/** Caminhos prontos para {@link StripElement}: retas, raios, helices e rastros de um ponto em movimento. */
public final class Paths {

    /** Posicao de um ponto em movimento, relativa a ancora, no instante {@code seconds}. */
    @FunctionalInterface
    public interface Motion {
        void at(float seconds, float[] out3);
    }

    private Paths() {
    }

    /** Reta da ancora ate ancora + (dx, dy, dz). */
    public static StripPath line(float dx, float dy, float dz, int segments) {
        int n = MathUtil.clamp(segments, 1, StripElement.MAX_POINTS - 1);
        return (e, out) -> {
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                out[i * 3] = dx * f;
                out[i * 3 + 1] = dy * f;
                out[i * 3 + 2] = dz * f;
            }
            return n + 1;
        };
    }

    /**
     * Raio: reta quebrada por deslocamentos aleatorios, sorteada de novo
     * {@code rate} vezes por segundo (o tremor caracteristico do relampago).
     */
    public static StripPath bolt(float dx, float dy, float dz, int segments, float amplitude, float rate) {
        int n = MathUtil.clamp(segments, 2, StripElement.MAX_POINTS - 1);
        return (e, out) -> {
            int frame = (int) Math.floor(e.age * rate);
            int seed = Float.floatToIntBits(e.seed) * 31 + frame * 7919;
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                float env = (float) Math.sin(f * Math.PI) * amplitude;
                out[i * 3] = dx * f + (Noise.random(seed, i * 3) - 0.5f) * 2.0f * env;
                out[i * 3 + 1] = dy * f + (Noise.random(seed, i * 3 + 1) - 0.5f) * 2.0f * env;
                out[i * 3 + 2] = dz * f + (Noise.random(seed, i * 3 + 2) - 0.5f) * 2.0f * env;
            }
            return n + 1;
        };
    }

    /** Helice que sobe da ancora: o raio vai de {@code r0} a {@code r1} em {@code height} blocos. */
    public static StripPath helix(float r0, float r1, float height, float turns, float phase, int segments) {
        int n = MathUtil.clamp(segments, 2, StripElement.MAX_POINTS - 1);
        return (e, out) -> {
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;
                float a = (phase + f * turns) * MathUtil.TAU;
                float r = MathUtil.lerp(r0, r1, f);
                out[i * 3] = (float) Math.cos(a) * r;
                out[i * 3 + 1] = height * f;
                out[i * 3 + 2] = (float) Math.sin(a) * r;
            }
            return n + 1;
        };
    }

    /**
     * Arco da ancora ate ancora + (dx, dy, dz), abaulado por {@code bulge}
     * blocos na direcao (bx, by, bz). So o trecho ja percorrido aparece: a
     * ponta avanca com o progresso do elemento e a cauda tem {@code tail}
     * (fracao do percurso) de comprimento.
     */
    public static StripPath arcSweep(float dx, float dy, float dz, float bx, float by, float bz, float tail, int segments) {
        int n = MathUtil.clamp(segments, 2, StripElement.MAX_POINTS - 1);
        return (e, out) -> {
            float head = MathUtil.saturate(e.progress() * (1.0f + tail));
            float start = Math.max(0.0f, head - tail);
            head = Math.min(head, 1.0f);
            for (int i = 0; i <= n; i++) {
                float f = MathUtil.lerp(start, head, i / (float) n);
                float bow = 4.0f * f * (1.0f - f);
                out[i * 3] = dx * f + bx * bow;
                out[i * 3 + 1] = dy * f + by * bow;
                out[i * 3 + 2] = dz * f + bz * bow;
            }
            return n + 1;
        };
    }

    /** Rastro de um ponto em movimento: os ultimos {@code seconds} do percurso, da cauda a ponta. */
    public static StripPath trail(Motion motion, float seconds, int segments) {
        int n = MathUtil.clamp(segments, 2, StripElement.MAX_POINTS - 1);
        float[] p = new float[3];
        return (e, out) -> {
            for (int i = 0; i <= n; i++) {
                float t = Math.max(0.0f, e.age - seconds * (1.0f - i / (float) n));
                motion.at(t, p);
                out[i * 3] = p[0];
                out[i * 3 + 1] = p[1];
                out[i * 3 + 2] = p[2];
            }
            return n + 1;
        };
    }
}
