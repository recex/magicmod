package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.render.PostState;
import org.joml.Vector4f;

/**
 * Pulso de pos-processamento e de camera ligado a um ponto do mundo: clarao,
 * aberracao cromatica, desfoque radial, gradacao de cor, quadro de impacto,
 * tremor, zoom e neblina. Cada canal e uma curva ao longo da vida do pulso; a
 * forca cai com a distancia ate o ponto ({@link #reach}).
 */
public final class PostElement extends VfxElement<PostElement> {

    /** Distancia (blocos) em que o efeito some; 0 = vale em qualquer distancia. */
    public float reach;
    private Curve flash;
    private Curve chroma;
    private Curve radial;
    private Curve exposure;
    private Curve saturation;
    private Curve contrast;
    private Curve vignette;
    private Curve tint;
    private Curve impact;
    private Curve shake;
    private Curve fov;
    private Curve letterbox;
    private Curve fogTint;
    private Curve fogDarken;
    private Curve sceneBloom;
    private final float[] flashColor = {1.0f, 1.0f, 1.0f};
    private final float[] tintColor = {1.0f, 1.0f, 1.0f};
    private final float[] impactColor = {1.0f, 1.0f, 1.0f};
    private final float[] fogColor = {0.0f, 0.0f, 0.0f};
    private final Vector4f scratch = new Vector4f();

    @Override
    protected PostElement self() {
        return this;
    }

    public PostElement reach(float blocks) {
        this.reach = blocks;
        return this;
    }

    public PostElement flash(float r, float g, float b, Curve amount) {
        flashColor[0] = r;
        flashColor[1] = g;
        flashColor[2] = b;
        this.flash = amount;
        return this;
    }

    public PostElement chroma(Curve amount) {
        this.chroma = amount;
        return this;
    }

    /** Desfoque radial em direcao ao ponto do efeito na tela. */
    public PostElement radialBlur(Curve amount) {
        this.radial = amount;
        return this;
    }

    public PostElement exposure(Curve factor) {
        this.exposure = factor;
        return this;
    }

    public PostElement saturation(Curve factor) {
        this.saturation = factor;
        return this;
    }

    public PostElement contrast(Curve factor) {
        this.contrast = factor;
        return this;
    }

    public PostElement vignette(Curve amount) {
        this.vignette = amount;
        return this;
    }

    public PostElement tint(float r, float g, float b, Curve amount) {
        tintColor[0] = r;
        tintColor[1] = g;
        tintColor[2] = b;
        this.tint = amount;
        return this;
    }

    public PostElement impactFrame(float r, float g, float b, Curve amount) {
        impactColor[0] = r;
        impactColor[1] = g;
        impactColor[2] = b;
        this.impact = amount;
        return this;
    }

    public PostElement shake(Curve amount) {
        this.shake = amount;
        return this;
    }

    /** Variacao relativa do campo de visao (0.1 = 10% mais aberto; negativo fecha). */
    public PostElement fov(Curve amount) {
        this.fov = amount;
        return this;
    }

    public PostElement letterbox(Curve amount) {
        this.letterbox = amount;
        return this;
    }

    public PostElement fog(float r, float g, float b, Curve tint, Curve darken) {
        fogColor[0] = r;
        fogColor[1] = g;
        fogColor[2] = b;
        this.fogTint = tint;
        this.fogDarken = darken;
        return this;
    }

    public PostElement sceneBloom(Curve amount) {
        this.sceneBloom = amount;
        return this;
    }

    @Override
    public float boundRadius() {
        return 1.0e6f;
    }

    @Override
    public void prepare(FrameContext ctx, PostState post) {
        float t = progress();
        float k = 1.0f;
        if (reach > 0.0f) {
            k = MathUtil.saturate(1.0f - ctx.distanceTo(x, y, z) / reach);
            k *= k;
            if (k <= 0.0f) {
                return;
            }
        }
        if (flash != null) {
            post.flash(flashColor[0], flashColor[1], flashColor[2], flash.sample(t) * k);
        }
        if (chroma != null) {
            post.chroma = Math.max(post.chroma, chroma.sample(t) * k);
        }
        if (radial != null) {
            float amount = radial.sample(t) * k;
            if (amount > 0.001f) {
                scratch.set((float) (x - ctx.camX), (float) (y - ctx.camY), (float) (z - ctx.camZ), 1.0f).mul(ctx.viewProj);
                float sx = 0.5f;
                float sy = 0.5f;
                if (scratch.w > 0.05f) {
                    sx = MathUtil.clamp(scratch.x / scratch.w * 0.5f + 0.5f, -0.5f, 1.5f);
                    sy = MathUtil.clamp(scratch.y / scratch.w * 0.5f + 0.5f, -0.5f, 1.5f);
                }
                post.radialBlur(sx, sy, amount);
            }
        }
        if (exposure != null) {
            post.exposure *= MathUtil.lerp(1.0f, exposure.sample(t), k);
        }
        if (saturation != null) {
            post.saturation *= MathUtil.lerp(1.0f, saturation.sample(t), k);
        }
        if (contrast != null) {
            post.contrast *= MathUtil.lerp(1.0f, contrast.sample(t), k);
        }
        if (vignette != null) {
            post.vignette = Math.max(post.vignette, vignette.sample(t) * k);
        }
        if (tint != null) {
            post.tint(tintColor[0], tintColor[1], tintColor[2], tint.sample(t) * k);
        }
        if (impact != null) {
            post.impactFrame(impactColor[0], impactColor[1], impactColor[2], impact.sample(t) * k);
        }
        if (shake != null) {
            post.shake = Math.max(post.shake, shake.sample(t) * k);
        }
        if (fov != null) {
            post.fovKick += fov.sample(t) * k;
        }
        if (letterbox != null) {
            post.letterbox = Math.max(post.letterbox, letterbox.sample(t));
        }
        if (fogTint != null || fogDarken != null) {
            post.fog(fogColor[0], fogColor[1], fogColor[2], fogTint == null ? 0.0f : fogTint.sample(t) * k,
                    fogDarken == null ? 0.0f : fogDarken.sample(t) * k);
        }
        if (sceneBloom != null) {
            post.sceneBloom = Math.max(post.sceneBloom, sceneBloom.sample(t) * k);
        }
    }
}
