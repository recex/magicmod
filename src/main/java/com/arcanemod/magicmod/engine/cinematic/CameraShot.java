package com.arcanemod.magicmod.engine.cinematic;

import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.math.MathUtil;

/**
 * Um plano de camera: a camera orbita um ponto do mundo, com distancia,
 * angulo, altura, zoom e inclinacao dados por curvas ao longo do plano (em
 * segundos). No comeco e no fim ela se mistura suavemente com a visao do
 * jogador. So descreve o movimento; quem executa e o {@link CameraDirector}.
 */
public final class CameraShot {

    public final double x;
    public final double y;
    public final double z;
    public final float duration;
    public float blendIn = 0.35f;
    public float blendOut = 0.45f;
    /** Distancia da camera ao ponto (blocos). */
    public Curve distance = Curve.constant(6.0f);
    /** Giro em torno do ponto, em graus, a partir da direcao em que o jogador estava. */
    public Curve orbit = Curve.constant(0.0f);
    /** Altura da camera acima do ponto. */
    public Curve height = Curve.constant(1.5f);
    /** Altura do ponto para onde a camera olha, acima do ponto. */
    public Curve lookHeight = Curve.constant(0.5f);
    /** Variacao relativa do campo de visao. */
    public Curve fov = Curve.constant(0.0f);
    /** Inclinacao lateral em graus. */
    public Curve roll = Curve.constant(0.0f);
    public float letterbox = 0.11f;

    public CameraShot(double x, double y, double z, float duration) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.duration = Math.max(0.2f, duration);
    }

    public CameraShot distance(Curve c) {
        this.distance = c;
        return this;
    }

    public CameraShot orbit(Curve c) {
        this.orbit = c;
        return this;
    }

    public CameraShot height(Curve c) {
        this.height = c;
        return this;
    }

    public CameraShot lookHeight(Curve c) {
        this.lookHeight = c;
        return this;
    }

    public CameraShot fov(Curve c) {
        this.fov = c;
        return this;
    }

    public CameraShot roll(Curve c) {
        this.roll = c;
        return this;
    }

    public CameraShot blend(float in, float out) {
        this.blendIn = in;
        this.blendOut = out;
        return this;
    }

    /** Peso do plano em relacao a visao do jogador no instante {@code t}: 0 no inicio e no fim, 1 no meio. */
    public float weight(float t) {
        float in = blendIn <= 0.0f ? 1.0f : MathUtil.smoothstep(0.0f, blendIn, t);
        float out = blendOut <= 0.0f ? 1.0f : MathUtil.smoothstep(0.0f, blendOut, duration - t);
        return MathUtil.saturate(Math.min(in, out));
    }

    /**
     * Posicao da camera no instante {@code t}, dado o angulo inicial
     * {@code baseYawDegrees} (direcao do ponto ate a camera no plano
     * horizontal). Escreve x, y, z em {@code out}.
     */
    public void position(float t, float baseYawDegrees, double[] out) {
        double a = Math.toRadians(baseYawDegrees + orbit.sample(t));
        double d = Math.max(0.5f, distance.sample(t));
        out[0] = x + Math.sin(a) * d;
        out[1] = y + height.sample(t);
        out[2] = z + Math.cos(a) * d;
    }
}
