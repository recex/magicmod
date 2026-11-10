package com.arcanemod.magicmod.engine.math;

/**
 * Ruido procedural deterministico (hash inteiro + ruido de gradiente) para
 * animacao no lado da CPU: tremor de camera, raios, orbitas irregulares.
 * Os shaders tem a sua propria biblioteca de ruido em GLSL.
 */
public final class Noise {

    private Noise() {
    }

    /** Hash inteiro bem misturado (variante do lowbias32). */
    public static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    public static int hash(int x, int y) {
        return hash(x * 0x1f123bb5 ^ hash(y + 0x9e3779b9));
    }

    public static int hash(int x, int y, int z) {
        return hash(hash(x, y) ^ hash(z * 0x632be5ab + 0x165667b1));
    }

    /** Valor pseudoaleatorio em [0, 1) para o inteiro dado. */
    public static float random(int seed) {
        return (hash(seed) >>> 8) * (1.0f / 16777216.0f);
    }

    public static float random(int seed, int index) {
        return (hash(seed, index) >>> 8) * (1.0f / 16777216.0f);
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float grad1(int h, float x) {
        return ((h & 1) == 0 ? x : -x) * (1.0f + (h >>> 1 & 3) * 0.25f);
    }

    /** Ruido de gradiente 1D em aproximadamente [-1, 1]; continuo e suave. */
    public static float noise1(float x, int seed) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float a = grad1(hash(i, seed), f);
        float b = grad1(hash(i + 1, seed), f - 1.0f);
        return MathUtil.lerp(a, b, fade(f)) * 1.6f;
    }

    private static float grad3(int h, float x, float y, float z) {
        switch (h & 15) {
            case 0: return x + y;
            case 1: return -x + y;
            case 2: return x - y;
            case 3: return -x - y;
            case 4: return x + z;
            case 5: return -x + z;
            case 6: return x - z;
            case 7: return -x - z;
            case 8: return y + z;
            case 9: return -y + z;
            case 10: return y - z;
            case 11: return -y - z;
            case 12: return x + y;
            case 13: return -x + y;
            case 14: return -y + z;
            default: return -y - z;
        }
    }

    /** Ruido de Perlin 3D em aproximadamente [-1, 1]. */
    public static float noise3(float x, float y, float z, int seed) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;
        float u = fade(xf);
        float v = fade(yf);
        float w = fade(zf);
        float n000 = grad3(hash(xi, yi, zi) ^ seed, xf, yf, zf);
        float n100 = grad3(hash(xi + 1, yi, zi) ^ seed, xf - 1, yf, zf);
        float n010 = grad3(hash(xi, yi + 1, zi) ^ seed, xf, yf - 1, zf);
        float n110 = grad3(hash(xi + 1, yi + 1, zi) ^ seed, xf - 1, yf - 1, zf);
        float n001 = grad3(hash(xi, yi, zi + 1) ^ seed, xf, yf, zf - 1);
        float n101 = grad3(hash(xi + 1, yi, zi + 1) ^ seed, xf - 1, yf, zf - 1);
        float n011 = grad3(hash(xi, yi + 1, zi + 1) ^ seed, xf, yf - 1, zf - 1);
        float n111 = grad3(hash(xi + 1, yi + 1, zi + 1) ^ seed, xf - 1, yf - 1, zf - 1);
        float x00 = MathUtil.lerp(n000, n100, u);
        float x10 = MathUtil.lerp(n010, n110, u);
        float x01 = MathUtil.lerp(n001, n101, u);
        float x11 = MathUtil.lerp(n011, n111, u);
        return MathUtil.lerp(MathUtil.lerp(x00, x10, v), MathUtil.lerp(x01, x11, v), w);
    }

    /** Soma fractal de oitavas de {@link #noise1}; resultado em aproximadamente [-1, 1]. */
    public static float fbm1(float x, int octaves, int seed) {
        float sum = 0.0f;
        float amp = 0.5f;
        float norm = 0.0f;
        for (int i = 0; i < octaves; i++) {
            sum += noise1(x, seed + i * 131) * amp;
            norm += amp;
            x *= 2.03f;
            amp *= 0.5f;
        }
        return norm > 0.0f ? sum / norm : 0.0f;
    }

    public static float fbm3(float x, float y, float z, int octaves, int seed) {
        float sum = 0.0f;
        float amp = 0.5f;
        float norm = 0.0f;
        for (int i = 0; i < octaves; i++) {
            sum += noise3(x, y, z, seed + i * 131) * amp;
            norm += amp;
            x *= 2.03f;
            y *= 2.03f;
            z *= 2.03f;
            amp *= 0.5f;
        }
        return norm > 0.0f ? sum / norm : 0.0f;
    }
}
