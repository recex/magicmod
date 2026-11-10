package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.render.PostState;
import com.arcanemod.magicmod.engine.render.RenderEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * Peca de um efeito visual: algo com posicao no mundo, um atraso para
 * comecar e um tempo de vida. Um feitico e uma colecao destas pecas
 * (volumes, fitas, luzes, pulsos de pos-processamento, emissores).
 *
 * @param <T> o proprio tipo concreto, para encadear chamadas
 */
public abstract class VfxElement<T extends VfxElement<T>> {

    public double x;
    public double y;
    public double z;
    /** Segundos que faltam para comecar. */
    public float delay;
    /** Duracao em segundos. */
    public float life = 1.0f;
    /** Segundos desde que comecou. */
    public float age;
    public float seed;
    /** Ordem de desenho: 0 = marcas no chao, 1 = normal, 2 = por cima. */
    public int layer = 1;
    boolean dead;
    boolean visible;
    float sortDistance;
    private Entity follow;
    private double followX;
    private double followY;
    private double followZ;
    private boolean dieWithEntity;
    private BiConsumer<T, Float> animator;

    protected abstract T self();

    public T life(float seconds) {
        this.life = Math.max(0.01f, seconds);
        return self();
    }

    public T layer(int layer) {
        this.layer = layer;
        return self();
    }

    public T moveTo(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return self();
    }

    public T offset(double dx, double dy, double dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return self();
    }

    /** Acompanha uma entidade (com o deslocamento dado) enquanto ela existir. */
    public T follow(Entity entity, double dx, double dy, double dz) {
        this.follow = entity;
        this.followX = dx;
        this.followY = dy;
        this.followZ = dz;
        return self();
    }

    /** O elemento termina quando a entidade seguida deixa de existir (projeteis). */
    public T dieWithEntity() {
        this.dieWithEntity = true;
        return self();
    }

    /** Funcao chamada a cada quadro com o progresso (0..1): anima qualquer campo do elemento. */
    public T animate(BiConsumer<T, Float> animator) {
        this.animator = animator;
        return self();
    }

    /** Progresso da vida em [0, 1]. */
    public final float progress() {
        return life <= 0.0f ? 1.0f : MathUtil.saturate(age / life);
    }

    public final boolean started() {
        return delay <= 0.0f;
    }

    public final boolean isDead() {
        return dead;
    }

    public final void kill() {
        dead = true;
    }

    final void advance(FrameContext ctx) {
        float dt = ctx.dt;
        if (delay > 0.0f) {
            delay -= dt;
            if (delay > 0.0f) {
                return;
            }
            dt = -delay;
            delay = 0.0f;
        }
        age += dt;
        if (age >= life) {
            dead = true;
        }
        if (follow != null) {
            if (follow.isRemoved()) {
                follow = null;
                if (dieWithEntity) {
                    dead = true;
                }
            } else {
                Vec3 p = follow.getPosition(ctx.partialTick);
                x = p.x + followX;
                y = p.y + followY;
                z = p.z + followZ;
            }
        }
        if (animator != null) {
            animator.accept(self(), progress());
        }
        onUpdate(ctx, dt);
    }

    protected void onUpdate(FrameContext ctx, float dt) {
    }

    /** Raio (blocos) da esfera que contem o elemento; usado no descarte por visibilidade. */
    public float boundRadius() {
        return 1.0f;
    }

    /** Contribuicoes ao quadro que nao sao geometria: luzes, pos-processamento, camera. */
    public void prepare(FrameContext ctx, PostState post) {
    }

    /** Desenha no passe de luz ({@code distortPass == false}) ou no passe de distorcao. */
    public void render(FrameContext ctx, RenderEngine renderer, boolean distortPass) {
    }

    /** Verdadeiro se o elemento desenha algo no passe de distorcao. */
    public boolean distorts() {
        return false;
    }
}
