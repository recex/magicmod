package com.arcanemod.magicmod.engine.anim;

import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.math.Noise;
import org.joml.Vector3f;

/**
 * Movimentos procedurais prontos (orbita, espiral, flutuacao, tremor): tudo
 * funcao pura do tempo, sem estado, para animar objetos e emissores.
 */
public final class Motion {

    private Motion() {
    }

    /** Ponto em orbita circular no plano XZ, opcionalmente inclinada em torno de X. */
    public static Vector3f orbit(float time, float radius, float turnsPerSecond, float phase, float tiltRadians, Vector3f dest) {
        float a = (time * turnsPerSecond + phase) * MathUtil.TAU;
        float x = (float) Math.cos(a) * radius;
        float z = (float) Math.sin(a) * radius;
        float cosT = (float) Math.cos(tiltRadians);
        float sinT = (float) Math.sin(tiltRadians);
        return dest.set(x, -z * sinT, z * cosT);
    }

    /** Espiral que sobe {@code rise} blocos por segundo enquanto o raio muda de {@code r0} para {@code r1}. */
    public static Vector3f spiral(float time, float duration, float r0, float r1, float turns, float rise, float phase, Vector3f dest) {
        float t = duration <= 0.0f ? 1.0f : MathUtil.saturate(time / duration);
        float a = (t * turns + phase) * MathUtil.TAU;
        float r = MathUtil.lerp(r0, r1, t);
        return dest.set((float) Math.cos(a) * r, rise * time, (float) Math.sin(a) * r);
    }

    /** Flutuacao vertical senoidal. */
    public static float bob(float time, float amplitude, float frequency) {
        return (float) Math.sin(time * frequency * MathUtil.TAU) * amplitude;
    }

    /** Deslocamento irregular e continuo (ruido) nos tres eixos. */
    public static Vector3f wobble(float time, float amplitude, float frequency, int seed, Vector3f dest) {
        float t = time * frequency;
        return dest.set(
                Noise.noise1(t, seed) * amplitude,
                Noise.noise1(t, seed + 17) * amplitude,
                Noise.noise1(t, seed + 43) * amplitude);
    }

    /** Pulso de escala: 1 em repouso, com picos de ate {@code 1 + amount} a cada batida. */
    public static float heartbeat(float time, float beatsPerSecond, float amount) {
        float phase = (time * beatsPerSecond) % 1.0f;
        float beat = (float) Math.exp(-phase * 6.0f);
        return 1.0f + beat * amount;
    }
}
