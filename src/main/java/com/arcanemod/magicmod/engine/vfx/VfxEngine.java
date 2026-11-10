package com.arcanemod.magicmod.engine.vfx;

import com.arcanemod.magicmod.engine.anim.Timeline;
import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.render.PostState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gerencia os elementos de efeito vivos: avanca o tempo de cada um, dispara
 * os eventos agendados (rajadas, sons), descarta o que nao esta na tela e
 * entrega a lista ordenada para o renderizador.
 */
public final class VfxEngine {

    /** Teto de elementos simultaneos: protege contra rajadas de feiticos (ou de pacotes). */
    public static final int MAX_ELEMENTS = 768;
    public static final int MAX_TIMELINES = 256;

    private final List<VfxElement<?>> elements = new ArrayList<>();
    private final List<VfxElement<?>> incoming = new ArrayList<>();
    private final List<VfxElement<?>> drawList = new ArrayList<>();
    private final List<Timeline> timelines = new ArrayList<>();
    private final List<Timeline> incomingTimelines = new ArrayList<>();
    private static final Comparator<VfxElement<?>> ORDER = (a, b) -> {
        if (a.layer != b.layer) {
            return Integer.compare(a.layer, b.layer);
        }
        return Float.compare(b.sortDistance, a.sortDistance);
    };
    private float maxDistance = 128.0f;
    private long dropped;
    private boolean anyDistortion;

    /** Adiciona um elemento; devolve falso se o teto foi atingido (o elemento e ignorado). */
    public boolean add(VfxElement<?> element) {
        if (elements.size() + incoming.size() >= MAX_ELEMENTS) {
            dropped++;
            return false;
        }
        incoming.add(element);
        return true;
    }

    /** Agenda uma linha do tempo de eventos (rajadas de particulas, sons, tremores). */
    public boolean schedule(Timeline timeline) {
        if (timelines.size() + incomingTimelines.size() >= MAX_TIMELINES) {
            dropped++;
            return false;
        }
        incomingTimelines.add(timeline);
        return true;
    }

    public void setMaxDistance(float blocks) {
        this.maxDistance = blocks;
    }

    /** Avanca o tempo: eventos agendados primeiro (eles podem criar elementos), depois os elementos. */
    public void update(FrameContext ctx) {
        timelines.addAll(incomingTimelines);
        incomingTimelines.clear();
        for (int i = timelines.size() - 1; i >= 0; i--) {
            Timeline t = timelines.get(i);
            try {
                t.advance(ctx.dt);
            } catch (RuntimeException e) {
                t.stop();
            }
            if (t.isFinished()) {
                timelines.remove(i);
            }
        }
        elements.addAll(incoming);
        incoming.clear();
        for (int i = elements.size() - 1; i >= 0; i--) {
            VfxElement<?> e = elements.get(i);
            e.advance(ctx);
            if (e.dead) {
                elements.remove(i);
            }
        }
        // Elementos criados por animadores/emissores durante este quadro entram no proximo.
    }

    /** Calcula visibilidade, recolhe luzes e pos-processamento e monta a lista de desenho. */
    public List<VfxElement<?>> prepare(FrameContext ctx, PostState post) {
        drawList.clear();
        anyDistortion = false;
        for (int i = 0; i < elements.size(); i++) {
            VfxElement<?> e = elements.get(i);
            e.visible = false;
            if (!e.started() || e.dead) {
                continue;
            }
            float dist = ctx.distanceTo(e.x, e.y, e.z);
            float bound = e.boundRadius();
            if (dist - bound > maxDistance) {
                continue;
            }
            e.prepare(ctx, post);
            if (bound < 1.0e5f && !ctx.sphereVisible(e.x, e.y, e.z, bound)) {
                continue;
            }
            e.visible = true;
            e.sortDistance = dist;
            anyDistortion |= e.distorts();
            drawList.add(e);
        }
        drawList.sort(ORDER);
        return drawList;
    }

    public boolean hasDistortion() {
        return anyDistortion;
    }

    public boolean isIdle() {
        return elements.isEmpty() && incoming.isEmpty() && timelines.isEmpty() && incomingTimelines.isEmpty();
    }

    public int elementCount() {
        return elements.size() + incoming.size();
    }

    public int timelineCount() {
        return timelines.size() + incomingTimelines.size();
    }

    public long droppedCount() {
        return dropped;
    }

    public void clear() {
        elements.clear();
        incoming.clear();
        drawList.clear();
        timelines.clear();
        incomingTimelines.clear();
    }
}
