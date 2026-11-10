package com.arcanemod.magicmod.engine.math;

import org.joml.Vector3d;

/**
 * Caminho 3D suave que passa por todos os pontos de controle (Catmull-Rom),
 * com reparametrizacao por comprimento de arco: {@code position(0.5)} esta na
 * metade do <em>percurso</em>, nao na metade dos pontos. Usado por travellings
 * de camera, rastros e projeteis guiados.
 */
public final class SplinePath {

    private static final int SAMPLES_PER_SEGMENT = 16;

    private final double[] px;
    private final double[] py;
    private final double[] pz;
    private final float[] arc;
    private final float length;

    public SplinePath(double... xyz) {
        if (xyz.length < 6 || xyz.length % 3 != 0) {
            throw new IllegalArgumentException("esperado ao menos dois pontos (x, y, z)");
        }
        int n = xyz.length / 3;
        px = new double[n];
        py = new double[n];
        pz = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = xyz[i * 3];
            py[i] = xyz[i * 3 + 1];
            pz[i] = xyz[i * 3 + 2];
        }
        int samples = (n - 1) * SAMPLES_PER_SEGMENT + 1;
        arc = new float[samples];
        Vector3d prev = raw(0.0f, new Vector3d());
        Vector3d cur = new Vector3d();
        float total = 0.0f;
        for (int i = 1; i < samples; i++) {
            raw(i / (float) (samples - 1), cur);
            total += (float) cur.distance(prev);
            arc[i] = total;
            prev.set(cur);
        }
        length = total;
    }

    /** Ponto de uma Bezier cubica (p0..p3) no parametro {@code t}. */
    public static Vector3d bezier(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, float t, Vector3d dest) {
        double u = 1.0 - t;
        double b0 = u * u * u;
        double b1 = 3.0 * u * u * t;
        double b2 = 3.0 * u * t * t;
        double b3 = (double) t * t * t;
        return dest.set(
                b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x,
                b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y,
                b0 * p0.z + b1 * p1.z + b2 * p2.z + b3 * p3.z);
    }

    private static double catmull(double a, double b, double c, double d, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * b) + (-a + c) * t + (2.0 * a - 5.0 * b + 4.0 * c - d) * t2 + (-a + 3.0 * b - 3.0 * c + d) * t3);
    }

    /** Posicao pelo parametro bruto (uniforme por ponto de controle). */
    private Vector3d raw(float t, Vector3d dest) {
        int n = px.length;
        double scaled = MathUtil.clamp(t, 0.0f, 1.0f) * (n - 1);
        int i = Math.min((int) scaled, n - 2);
        double local = scaled - i;
        int i0 = Math.max(i - 1, 0);
        int i3 = Math.min(i + 2, n - 1);
        return dest.set(
                catmull(px[i0], px[i], px[i + 1], px[i3], local),
                catmull(py[i0], py[i], py[i + 1], py[i3], local),
                catmull(pz[i0], pz[i], pz[i + 1], pz[i3], local));
    }

    private float arcToRaw(float t) {
        float target = MathUtil.clamp(t, 0.0f, 1.0f) * length;
        int lo = 0;
        int hi = arc.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (arc[mid] <= target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float span = arc[hi] - arc[lo];
        float frac = span <= 0.0f ? 0.0f : (target - arc[lo]) / span;
        return (lo + frac) / (arc.length - 1);
    }

    /** Posicao a uma fracao {@code t} em [0, 1] do comprimento total. */
    public Vector3d position(float t, Vector3d dest) {
        return raw(arcToRaw(t), dest);
    }

    /** Direcao de avanco (normalizada) na fracao {@code t} do percurso. */
    public Vector3d tangent(float t, Vector3d dest) {
        float e = 0.5f / (arc.length - 1);
        Vector3d a = position(Math.max(0.0f, t - e), new Vector3d());
        position(Math.min(1.0f, t + e), dest).sub(a);
        double len = dest.length();
        return len < 1.0e-9 ? dest.set(0.0, 0.0, 1.0) : dest.div(len);
    }

    public float length() {
        return length;
    }
}
