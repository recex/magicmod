package com.arcanemod.magicmod.engine.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Estado do pos-processamento de um quadro. E zerado no inicio de cada quadro
 * e os efeitos ativos somam aqui as suas contribuicoes (clarao, aberracao,
 * desfoque, gradacao, luzes, tremor de camera, neblina).
 */
public final class PostState {

    public static final int MAX_LIGHTS = 8;

    public float flashR;
    public float flashG;
    public float flashB;
    public float flashA;
    public float chroma;
    public float radialX = 0.5f;
    public float radialY = 0.5f;
    public float radialStrength;
    public float exposure = 1.0f;
    public float saturation = 1.0f;
    public float contrast = 1.0f;
    public float vignette;
    public float tintR = 1.0f;
    public float tintG = 1.0f;
    public float tintB = 1.0f;
    public float tintA;
    public float impact;
    public float impactR = 1.0f;
    public float impactG = 1.0f;
    public float impactB = 1.0f;
    public float letterbox;
    public float motionBlur;
    public float sceneBloom;

    /** Pedidos para a camera (consumidos pelo diretor de camera no quadro seguinte). */
    public float shake;
    public float fovKick;
    /** Pedidos para o ambiente (neblina), consumidos no quadro seguinte. */
    public float fogDarken;
    public float fogR;
    public float fogG;
    public float fogB;
    public float fogTint;

    public final FloatBuffer lightPos = directFloats(MAX_LIGHTS * 4);
    public final FloatBuffer lightColor = directFloats(MAX_LIGHTS * 4);
    private final float[] lightScore = new float[MAX_LIGHTS];
    public int lightCount;
    private int lightLimit = MAX_LIGHTS;

    private static FloatBuffer directFloats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public void reset(int maxLights) {
        flashR = flashG = flashB = flashA = 0.0f;
        chroma = 0.0f;
        radialX = radialY = 0.5f;
        radialStrength = 0.0f;
        exposure = saturation = contrast = 1.0f;
        vignette = 0.0f;
        tintR = tintG = tintB = 1.0f;
        tintA = 0.0f;
        impact = 0.0f;
        impactR = impactG = impactB = 1.0f;
        letterbox = 0.0f;
        motionBlur = 0.0f;
        sceneBloom = 0.0f;
        shake = 0.0f;
        fovKick = 0.0f;
        fogDarken = 0.0f;
        fogR = fogG = fogB = 0.0f;
        fogTint = 0.0f;
        lightCount = 0;
        lightLimit = Math.max(0, Math.min(MAX_LIGHTS, maxLights));
    }

    /** Clarao de tela: fica o mais forte; a cor acompanha. */
    public void flash(float r, float g, float b, float amount) {
        if (amount > flashA) {
            flashR = r;
            flashG = g;
            flashB = b;
            flashA = amount;
        }
    }

    public void radialBlur(float screenX, float screenY, float strength) {
        if (strength > radialStrength) {
            radialX = screenX;
            radialY = screenY;
            radialStrength = strength;
        }
    }

    public void tint(float r, float g, float b, float amount) {
        if (amount > tintA) {
            tintR = r;
            tintG = g;
            tintB = b;
            tintA = amount;
        }
    }

    public void impactFrame(float r, float g, float b, float amount) {
        if (amount > impact) {
            impactR = r;
            impactG = g;
            impactB = b;
            impact = amount;
        }
    }

    public void fog(float r, float g, float b, float tint, float darken) {
        if (tint > fogTint) {
            fogR = r;
            fogG = g;
            fogB = b;
            fogTint = tint;
        }
        fogDarken = Math.max(fogDarken, darken);
    }

    /**
     * Luz dinamica. A posicao e relativa a camera. Se o limite do nivel de
     * qualidade estourar, fica a luz mais relevante (forte e proxima).
     */
    public void light(float x, float y, float z, float radius, float r, float g, float b) {
        float power = Math.max(r, Math.max(g, b));
        if (power <= 0.003f || radius <= 0.01f || lightLimit == 0) {
            return;
        }
        float dist = (float) Math.sqrt(x * x + y * y + z * z);
        float score = power * radius / (1.0f + dist * 0.1f);
        int slot;
        if (lightCount < lightLimit) {
            slot = lightCount++;
        } else {
            slot = 0;
            for (int i = 1; i < lightCount; i++) {
                if (lightScore[i] < lightScore[slot]) {
                    slot = i;
                }
            }
            if (lightScore[slot] >= score) {
                return;
            }
        }
        lightScore[slot] = score;
        int o = slot * 4;
        lightPos.put(o, x).put(o + 1, y).put(o + 2, z).put(o + 3, radius);
        lightColor.put(o, r).put(o + 1, g).put(o + 2, b).put(o + 3, 1.0f);
    }

    /** Verdadeiro se nada altera a imagem (o motor pode pular o quadro inteiro). */
    public boolean isNeutral() {
        return flashA <= 0.001f && chroma <= 0.0005f && radialStrength <= 0.001f && Math.abs(exposure - 1.0f) < 0.002f
                && Math.abs(saturation - 1.0f) < 0.002f && Math.abs(contrast - 1.0f) < 0.002f && vignette <= 0.002f
                && tintA <= 0.002f && impact <= 0.002f && letterbox <= 0.002f && motionBlur <= 0.002f && lightCount == 0;
    }
}
