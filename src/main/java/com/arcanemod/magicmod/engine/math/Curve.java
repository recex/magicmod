package com.arcanemod.magicmod.engine.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Curva escalar por quadros-chave. Cada chave guarda o instante, o valor e a
 * forma de chegar ate ela (um {@link Easing} ou uma {@link CubicBezier}).
 * Imutavel: pode ser compartilhada entre efeitos e threads.
 *
 * <p>E a base de toda animacao do motor: intensidade de shaders, raio de
 * ondas de choque, FOV da camera, volume de audio, etc.
 */
public final class Curve {

    private final float[] times;
    private final float[] values;
    private final Easing[] easings;
    private final CubicBezier[] beziers;

    private Curve(float[] times, float[] values, Easing[] easings, CubicBezier[] beziers) {
        this.times = times;
        this.values = values;
        this.easings = easings;
        this.beziers = beziers;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Curve constant(float value) {
        return new Curve(new float[] {0.0f}, new float[] {value}, new Easing[] {Easing.LINEAR}, new CubicBezier[1]);
    }

    /** De {@code from} (t=0) ate {@code to} (t=1), linear. */
    public static Curve line(float from, float to) {
        return builder().key(0.0f, from).key(1.0f, to).build();
    }

    /** De {@code from} (t=0) ate {@code to} (t=1) com o easing dado. */
    public static Curve ease(float from, float to, Easing easing) {
        return builder().key(0.0f, from).key(1.0f, to, easing).build();
    }

    /** Pares (tempo, valor) ligados por segmentos lineares. */
    public static Curve of(float... timeValuePairs) {
        if (timeValuePairs.length == 0 || timeValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("esperado: tempo, valor, tempo, valor...");
        }
        Builder b = builder();
        for (int i = 0; i < timeValuePairs.length; i += 2) {
            b.key(timeValuePairs[i], timeValuePairs[i + 1]);
        }
        return b.build();
    }

    /**
     * Envelope em [0, 1]: sobe de 0 a 1 ate {@code attack}, sustenta e volta a
     * 0 a partir de {@code 1 - release}. Subida rapida, queda suave.
     */
    public static Curve pulse(float attack, float release) {
        float a = MathUtil.clamp(attack, 0.0f, 1.0f);
        float r = MathUtil.clamp(1.0f - release, a, 1.0f);
        Builder b = builder().key(0.0f, 0.0f);
        if (a > 0.0f) {
            b.key(a, 1.0f, Easing.OUT_CUBIC);
        } else {
            b.key(0.0f, 1.0f);
        }
        if (r > a) {
            b.key(r, 1.0f);
        }
        return b.key(1.0f, 0.0f, Easing.IN_OUT_SINE).build();
    }

    /** Valor da curva no instante {@code t}; fora do dominio, repete a chave da ponta. */
    public float sample(float t) {
        int n = times.length;
        if (n == 1 || t <= times[0]) {
            return values[0];
        }
        if (t >= times[n - 1]) {
            return values[n - 1];
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= t) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float span = times[hi] - times[lo];
        float local = span <= 0.0f ? 1.0f : (t - times[lo]) / span;
        float eased = beziers[hi] != null ? beziers[hi].evaluate(local) : easings[hi].apply(local);
        return values[lo] + (values[hi] - values[lo]) * eased;
    }

    /** Instante da ultima chave. */
    public float duration() {
        return times[times.length - 1];
    }

    public int keyCount() {
        return times.length;
    }

    /** Nova curva com os valores multiplicados por {@code factor}. */
    public Curve scaled(float factor) {
        float[] scaled = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            scaled[i] = values[i] * factor;
        }
        return new Curve(times, scaled, easings, beziers);
    }

    /** Construtor fluente; as chaves devem ser adicionadas em ordem de tempo. */
    public static final class Builder {
        private final List<float[]> keys = new ArrayList<>();
        private final List<Easing> easings = new ArrayList<>();
        private final List<CubicBezier> beziers = new ArrayList<>();

        public Builder key(float time, float value) {
            return key(time, value, Easing.LINEAR);
        }

        public Builder key(float time, float value, Easing easing) {
            add(time, value, easing == null ? Easing.LINEAR : easing, null);
            return this;
        }

        public Builder key(float time, float value, CubicBezier bezier) {
            add(time, value, Easing.LINEAR, bezier);
            return this;
        }

        private void add(float time, float value, Easing easing, CubicBezier bezier) {
            if (!keys.isEmpty() && time < keys.get(keys.size() - 1)[0]) {
                throw new IllegalArgumentException("chaves fora de ordem: " + time);
            }
            keys.add(new float[] {time, value});
            easings.add(easing);
            beziers.add(bezier);
        }

        public Curve build() {
            if (keys.isEmpty()) {
                throw new IllegalStateException("curva sem chaves");
            }
            int n = keys.size();
            float[] t = new float[n];
            float[] v = new float[n];
            for (int i = 0; i < n; i++) {
                t[i] = keys.get(i)[0];
                v[i] = keys.get(i)[1];
            }
            return new Curve(t, v, easings.toArray(new Easing[0]), beziers.toArray(new CubicBezier[0]));
        }
    }
}
