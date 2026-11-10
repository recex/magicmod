package com.arcanemod.magicmod.engine.math;

/**
 * Gradiente de cor (RGBA, com RGB em faixa HDR) definido por paradas em
 * [0, 1]. Imutavel. Usado para a vida de particulas, rastros e luzes.
 */
public final class ColorRamp {

    private final float[] stops;
    private final float[] rgba;

    private ColorRamp(float[] stops, float[] rgba) {
        this.stops = stops;
        this.rgba = rgba;
    }

    /** Sequencia de (t, r, g, b, a) em ordem crescente de {@code t}. */
    public static ColorRamp of(float... tRgba) {
        if (tRgba.length == 0 || tRgba.length % 5 != 0) {
            throw new IllegalArgumentException("esperado: t, r, g, b, a, ...");
        }
        int n = tRgba.length / 5;
        float[] stops = new float[n];
        float[] rgba = new float[n * 4];
        for (int i = 0; i < n; i++) {
            stops[i] = tRgba[i * 5];
            if (i > 0 && stops[i] < stops[i - 1]) {
                throw new IllegalArgumentException("paradas fora de ordem");
            }
            System.arraycopy(tRgba, i * 5 + 1, rgba, i * 4, 4);
        }
        return new ColorRamp(stops, rgba);
    }

    /**
     * Rampa "energia": nucleo quase branco e superexposto, corpo na cor base e
     * cauda escura e saturada que some. {@code heat} multiplica o brilho.
     */
    public static ColorRamp energy(float r, float g, float b, float heat) {
        return of(
                0.00f, (0.6f + 0.4f * r) * heat * 2.2f, (0.6f + 0.4f * g) * heat * 2.2f, (0.6f + 0.4f * b) * heat * 2.2f, 1.0f,
                0.25f, r * heat * 1.4f, g * heat * 1.4f, b * heat * 1.4f, 1.0f,
                0.70f, r * r * heat * 0.6f, g * g * heat * 0.6f, b * b * heat * 0.6f, 0.7f,
                1.00f, r * r * 0.1f, g * g * 0.1f, b * b * 0.1f, 0.0f);
    }

    /** Amostra o gradiente em {@code t}; escreve r, g, b, a em {@code out}. */
    public float[] sample(float t, float[] out) {
        int n = stops.length;
        if (n == 1 || t <= stops[0]) {
            System.arraycopy(rgba, 0, out, 0, 4);
            return out;
        }
        if (t >= stops[n - 1]) {
            System.arraycopy(rgba, (n - 1) * 4, out, 0, 4);
            return out;
        }
        int hi = 1;
        while (stops[hi] < t) {
            hi++;
        }
        int lo = hi - 1;
        float span = stops[hi] - stops[lo];
        float f = span <= 0.0f ? 1.0f : (t - stops[lo]) / span;
        for (int c = 0; c < 4; c++) {
            out[c] = MathUtil.lerp(rgba[lo * 4 + c], rgba[hi * 4 + c], f);
        }
        return out;
    }
}
