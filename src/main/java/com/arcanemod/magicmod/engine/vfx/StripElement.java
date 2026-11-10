package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.render.RenderEngine;

/**
 * Fita voltada para a camera ao longo de um caminho: feixes de energia,
 * rastros, raios, cipos, fluxos espectrais. O caminho e recalculado a cada
 * quadro por uma {@link StripPath}, entao pode se mover, tremer ou crescer.
 */
public final class StripElement extends VfxElement<StripElement> {

    public static final int BEAM = 0;
    public static final int TRAIL = 1;
    public static final int LIGHTNING = 2;
    public static final int VINE = 3;
    public static final int WISP = 4;
    public static final int SLASH = 5;

    public static final int MAX_POINTS = 96;

    /** Calcula os pontos do caminho, relativos a ancora do elemento. */
    @FunctionalInterface
    public interface StripPath {
        /** Escreve trincas x, y, z em {@code out} e devolve o numero de pontos (no maximo {@link #MAX_POINTS}). */
        int build(StripElement element, float[] out);
    }

    public final int style;
    public final StripPath path;
    public final float[] color = {1.0f, 1.0f, 1.0f, 0.0f};
    /** Largura em blocos. */
    public float width = 0.3f;
    /** Fator da largura ao longo do comprimento (dominio 0..1, da cauda a ponta). */
    public Curve widthAlong = Curve.constant(1.0f);
    /** Brilho ao longo da vida (dominio 0..1). */
    public Curve intensity = Curve.constant(1.0f);
    /** Valor de "idade" entregue ao shader ao longo da vida (crescimento do cipo, varredura do corte). */
    public Curve shaderAge = Curve.line(0.0f, 1.0f);
    public float cullRadius = 24.0f;
    public float currentIntensity = 1.0f;

    public StripElement(int style, StripPath path) {
        this.style = style;
        this.path = path;
    }

    @Override
    protected StripElement self() {
        return this;
    }

    public StripElement color(float r, float g, float b, float opacity) {
        color[0] = r;
        color[1] = g;
        color[2] = b;
        color[3] = opacity;
        return this;
    }

    public StripElement width(float blocks) {
        this.width = blocks;
        return this;
    }

    public StripElement widthAlong(Curve curve) {
        this.widthAlong = curve;
        return this;
    }

    public StripElement intensity(Curve curve) {
        this.intensity = curve;
        return this;
    }

    public StripElement shaderAge(Curve curve) {
        this.shaderAge = curve;
        return this;
    }

    public StripElement cullRadius(float blocks) {
        this.cullRadius = blocks;
        return this;
    }

    @Override
    protected void onUpdate(FrameContext ctx, float dt) {
        currentIntensity = Math.max(0.0f, intensity.sample(progress()));
    }

    @Override
    public float boundRadius() {
        return cullRadius;
    }

    @Override
    public void render(FrameContext ctx, RenderEngine renderer, boolean distortPass) {
        if (distortPass || currentIntensity <= 0.0005f) {
            return;
        }
        renderer.drawStrip(ctx, this);
    }
}
