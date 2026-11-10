package com.arcanemod.magicmod.engine.world;

import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.render.PostState;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Efeitos de ambiente: tinge e adensa a neblina do mundo enquanto efeitos
 * grandes estao ativos (o ceu escurece em volta de uma singularidade, a
 * neblina pega a cor de uma supernova). Nao altera blocos nem o clima.
 */
public final class WorldFx {

    private float targetTint;
    private float targetDarken;
    private float tint;
    private float darken;
    private float r;
    private float g;
    private float b;
    private long lastNanos;

    /** Recebe os pedidos do quadro (valem a partir do quadro seguinte). */
    public void accept(PostState post) {
        targetTint = MathUtil.saturate(post.fogTint);
        targetDarken = MathUtil.saturate(post.fogDarken);
        if (post.fogTint > 0.001f) {
            r = post.fogR;
            g = post.fogG;
            b = post.fogB;
        }
    }

    public void reset() {
        targetTint = targetDarken = tint = darken = 0.0f;
    }

    public boolean isActive() {
        return tint > 0.003f || darken > 0.003f;
    }

    @SubscribeEvent
    public void onFogColor(ViewportEvent.ComputeFogColor event) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        tint = MathUtil.damp(tint, targetTint, 0.12f, dt);
        darken = MathUtil.damp(darken, targetDarken, 0.12f, dt);
        if (!isActive()) {
            return;
        }
        float k = 1.0f - darken * 0.85f;
        event.setRed(MathUtil.lerp(event.getRed(), r, tint) * k);
        event.setGreen(MathUtil.lerp(event.getGreen(), g, tint) * k);
        event.setBlue(MathUtil.lerp(event.getBlue(), b, tint) * k);
    }

    @SubscribeEvent
    public void onRenderFog(ViewportEvent.RenderFog event) {
        if (darken <= 0.01f) {
            return;
        }
        // Puxa a neblina para perto: o mundo "fecha" em volta do efeito.
        event.setNearPlaneDistance(event.getNearPlaneDistance() * (1.0f - 0.7f * darken));
        event.setFarPlaneDistance(event.getFarPlaneDistance() * (1.0f - 0.45f * darken));
        event.setCanceled(true);
    }
}
