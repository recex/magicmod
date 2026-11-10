package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.render.RenderEngine;
import org.joml.Vector3f;

/**
 * Efeito volumetrico: um quadrilatero que cobre o efeito na tela e um
 * fragment shader ({@code shaders/vfx/volume/<nome>.fsh}) que faz o trabalho
 * (marcha de raios, campos de distancia, projecao no terreno).
 */
public final class ProxyElement extends VfxElement<ProxyElement> {

    public final String shader;
    /** Raio em blocos ao longo da vida (dominio 0..1). */
    public Curve radius = Curve.constant(1.0f);
    /** Intensidade/opacidade geral ao longo da vida (dominio 0..1). */
    public Curve intensity = Curve.constant(1.0f);
    /** Folga do quadrilatero em relacao ao raio (coroas e brilhos que vazam). */
    public float bound = 1.05f;
    /** Maior que zero: o volume e uma coluna deste comprimento ao longo do eixo. */
    public float axisLength;
    public final Vector3f axis = new Vector3f(0.0f, 1.0f, 0.0f);
    public final float[] colorA = {1.0f, 1.0f, 1.0f, 1.0f};
    public final float[] colorB = {0.0f, 0.0f, 0.0f, 1.0f};
    public final float[] params0 = new float[4];
    public final float[] params1 = new float[4];
    public boolean distort;
    /** Deslocamento animado em relacao a ancora (ex.: bola de fogo que sobe). */
    public double offX;
    public double offY;
    public double offZ;
    public float riseSpeed;
    public float currentRadius = 1.0f;
    public float currentIntensity = 1.0f;

    public ProxyElement(String shader) {
        this.shader = shader;
        if ("decal".equals(shader)) {
            // A marca cobre degraus e relevo ate a altura de projecao: precisa de folga vertical.
            this.bound = 1.35f;
            this.layer = 0;
        }
    }

    @Override
    protected ProxyElement self() {
        return this;
    }

    public ProxyElement radius(Curve curve) {
        this.radius = curve;
        return this;
    }

    public ProxyElement radius(float constant) {
        this.radius = Curve.constant(constant);
        return this;
    }

    public ProxyElement intensity(Curve curve) {
        this.intensity = curve;
        return this;
    }

    public ProxyElement bound(float factor) {
        this.bound = factor;
        return this;
    }

    /** Volume em coluna: eixo e comprimento (pilares, tornados). */
    public ProxyElement column(float ax, float ay, float az, float length) {
        axis.set(ax, ay, az).normalize();
        this.axisLength = length;
        // O brilho da coluna se estende ate 2,2 raios do eixo (ver pillar.fsh).
        this.bound = 2.3f;
        return this;
    }

    public ProxyElement axis(float ax, float ay, float az) {
        axis.set(ax, ay, az);
        if (axis.lengthSquared() < 1.0e-8f) {
            axis.set(0.0f, 1.0f, 0.0f);
        }
        axis.normalize();
        return this;
    }

    public ProxyElement colorA(float r, float g, float b) {
        colorA[0] = r;
        colorA[1] = g;
        colorA[2] = b;
        return this;
    }

    public ProxyElement colorB(float r, float g, float b, float a) {
        colorB[0] = r;
        colorB[1] = g;
        colorB[2] = b;
        colorB[3] = a;
        return this;
    }

    public ProxyElement params0(float a, float b, float c, float d) {
        params0[0] = a;
        params0[1] = b;
        params0[2] = c;
        params0[3] = d;
        return this;
    }

    public ProxyElement params1(float a, float b, float c, float d) {
        params1[0] = a;
        params1[1] = b;
        params1[2] = c;
        params1[3] = d;
        return this;
    }

    /** Tambem desenha no passe de distorcao (o shader precisa ter a variante DISTORT_PASS). */
    public ProxyElement distort() {
        this.distort = true;
        return this;
    }

    public ProxyElement rise(float blocksPerSecond) {
        this.riseSpeed = blocksPerSecond;
        return this;
    }

    @Override
    protected void onUpdate(FrameContext ctx, float dt) {
        float t = progress();
        currentRadius = Math.max(0.001f, radius.sample(t));
        currentIntensity = Math.max(0.0f, intensity.sample(t));
        offY += riseSpeed * dt;
    }

    @Override
    public float boundRadius() {
        float off = (float) Math.sqrt(offX * offX + offY * offY + offZ * offZ);
        return (axisLength > 0.0f ? axisLength + currentRadius * bound : currentRadius * bound) + off;
    }

    @Override
    public boolean distorts() {
        return distort;
    }

    @Override
    public void render(FrameContext ctx, RenderEngine renderer, boolean distortPass) {
        if (currentIntensity <= 0.0005f || (distortPass && !distort)) {
            return;
        }
        renderer.drawProxy(ctx, this, distortPass);
    }
}
