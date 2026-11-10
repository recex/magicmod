package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.anim.Timeline;
import com.arcanemod.magicmod.engine.core.ArcaneEngine;
import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.particle.Burst;
import com.arcanemod.magicmod.engine.vfx.StripElement.StripPath;
import net.minecraft.sounds.SoundEvent;

import java.util.Random;

/**
 * Construtor de um efeito: monta, em torno de um ponto do mundo, a sequencia
 * de volumes, fitas, luzes, rajadas de particulas, sons e pulsos de camera.
 * {@link #at(float)} move o "cursor" do tempo; tudo o que e criado depois
 * comeca naquele instante, e e isso que mantem imagem, som e camera
 * sincronizados.
 */
public final class Fx {

    private final ArcaneEngine engine;
    public final double x;
    public final double y;
    public final double z;
    public final Random random;
    private float cursor;
    private Timeline timeline;

    public Fx(ArcaneEngine engine, double x, double y, double z) {
        this.engine = engine;
        this.x = x;
        this.y = y;
        this.z = z;
        this.random = new Random(engine.random.nextLong());
    }

    /** Move o cursor: o que vier depois comeca {@code seconds} apos o inicio do efeito. */
    public Fx at(float seconds) {
        this.cursor = Math.max(0.0f, seconds);
        return this;
    }

    public float cursor() {
        return cursor;
    }

    public ArcaneEngine engine() {
        return engine;
    }

    /** Registra um elemento no ponto do efeito, comecando no cursor atual. */
    public <E extends VfxElement<E>> E add(E element) {
        element.moveTo(x, y, z);
        element.delay = cursor;
        element.seed = random.nextFloat();
        engine.vfx.add(element);
        return element;
    }

    /** Efeito volumetrico ({@code shaders/vfx/volume/<shader>.fsh}). */
    public ProxyElement volume(String shader, float life) {
        return add(new ProxyElement(shader)).life(life);
    }

    public StripElement strip(int style, StripPath path, float life) {
        return add(new StripElement(style, path)).life(life);
    }

    public LightElement light(float r, float g, float b, float radius, float life) {
        return add(new LightElement()).color(r, g, b).radius(radius).life(life);
    }

    public PostElement post(float life) {
        return add(new PostElement()).life(life);
    }

    public EmitterElement emitter(Burst spec, Curve rate, float life) {
        return add(new EmitterElement(engine.particles, spec, random)).rate(rate).life(life);
    }

    /** Rajada de particulas no ponto do efeito. */
    public Fx burst(Burst spec) {
        return burst(spec, 0.0, 0.0, 0.0);
    }

    /** Rajada de particulas deslocada do ponto do efeito. */
    public Fx burst(Burst spec, double dx, double dy, double dz) {
        return run(() -> spec.emit(engine.particles, engine.time(), x + dx, y + dy, z + dz, random, engine.particleScale()));
    }

    public Fx sound(SoundEvent sound, float volume, float pitch) {
        return run(() -> engine.audio.play(sound, x, y, z, volume, pitch));
    }

    /** Som continuo com envelope (dominio das curvas: 0..1 ao longo de {@code seconds}). */
    public Fx hum(SoundEvent sound, float seconds, Curve volume, Curve pitch) {
        return run(() -> engine.audio.loop(sound, x, y, z, seconds, volume, pitch));
    }

    /** Executa uma acao no instante do cursor (imediatamente, se o cursor esta em zero). */
    public Fx run(Runnable action) {
        if (cursor <= 0.0f) {
            action.run();
        } else {
            if (timeline == null) {
                timeline = new Timeline();
                engine.vfx.schedule(timeline);
            }
            timeline.event(cursor, action);
        }
        return this;
    }
}
