package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.render.PostState;

/**
 * Luz dinamica pontual: ilumina o terreno em volta do efeito. E calculada em
 * espaco de tela a partir da profundidade, sem tocar no sistema de luz do
 * mundo (nada de atualizar blocos nem reconstruir chunks).
 */
public final class LightElement extends VfxElement<LightElement> {

    public float r = 1.0f;
    public float g = 1.0f;
    public float b = 1.0f;
    public Curve radius = Curve.constant(8.0f);
    public Curve intensity = Curve.pulse(0.1f, 0.6f);

    @Override
    protected LightElement self() {
        return this;
    }

    public LightElement color(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
        return this;
    }

    public LightElement radius(float blocks) {
        this.radius = Curve.constant(blocks);
        return this;
    }

    public LightElement radius(Curve curve) {
        this.radius = curve;
        return this;
    }

    public LightElement intensity(Curve curve) {
        this.intensity = curve;
        return this;
    }

    @Override
    public float boundRadius() {
        return radius.sample(progress());
    }

    @Override
    public void prepare(FrameContext ctx, PostState post) {
        float t = progress();
        float i = intensity.sample(t);
        if (i <= 0.0f) {
            return;
        }
        post.light((float) (x - ctx.camX), (float) (y - ctx.camY), (float) (z - ctx.camZ), radius.sample(t), r * i, g * i, b * i);
    }
}
