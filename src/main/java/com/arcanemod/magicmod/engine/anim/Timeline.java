package com.arcanemod.magicmod.engine.anim;

import com.arcanemod.magicmod.engine.math.Curve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Linha do tempo: trilhas de curvas nomeadas mais eventos com hora marcada.
 * Avancar o tempo dispara, em ordem e exatamente uma vez, todo evento cujo
 * instante foi cruzado - e isso que sincroniza animacao, particulas, audio e
 * camera sem que ninguem precise programar quadro a quadro.
 *
 * <p>Nao depende do Minecraft; o tempo e em segundos.
 */
public final class Timeline {

    private static final class Event {
        final float time;
        final String name;
        final Runnable action;

        Event(float time, String name, Runnable action) {
            this.time = time;
            this.name = name;
            this.action = action;
        }
    }

    private final Map<String, Curve> tracks = new HashMap<>();
    private final List<Event> events = new ArrayList<>();
    private float duration;
    private boolean explicitDuration;
    private boolean looping;
    private float speed = 1.0f;
    private float time;
    private int nextEvent;
    private boolean started;
    private boolean finished;
    private Runnable onFinish;

    /** Adiciona (ou troca) uma trilha; o dominio da curva e em segundos. */
    public Timeline track(String name, Curve curve) {
        tracks.put(name, curve);
        if (!explicitDuration) {
            duration = Math.max(duration, curve.duration());
        }
        return this;
    }

    /** Agenda uma acao para o instante {@code time} (segundos). */
    public Timeline event(float time, String name, Runnable action) {
        events.add(new Event(Math.max(0.0f, time), name, action));
        events.sort(Comparator.comparingDouble(e -> e.time));
        if (!explicitDuration) {
            duration = Math.max(duration, time);
        }
        return this;
    }

    public Timeline event(float time, Runnable action) {
        return event(time, "", action);
    }

    public Timeline duration(float seconds) {
        this.duration = Math.max(0.0f, seconds);
        this.explicitDuration = true;
        return this;
    }

    public Timeline looping(boolean loop) {
        this.looping = loop;
        return this;
    }

    public Timeline speed(float factor) {
        this.speed = Math.max(0.0f, factor);
        return this;
    }

    public Timeline onFinish(Runnable action) {
        this.onFinish = action;
        return this;
    }

    /**
     * Avanca {@code dt} segundos e dispara os eventos cruzados. Eventos em
     * t=0 disparam na primeira chamada. Excecoes de um evento nao interrompem
     * os demais: sao repassadas depois, uma so vez.
     */
    public void advance(float dt) {
        if (finished) {
            return;
        }
        started = true;
        float remaining = Math.max(0.0f, dt) * speed;
        RuntimeException failure = null;
        int guard = 0;
        while (true) {
            float target = Math.min(time + remaining, duration);
            remaining -= target - time;
            time = target;
            while (nextEvent < events.size() && events.get(nextEvent).time <= time) {
                Event e = events.get(nextEvent++);
                try {
                    e.action.run();
                } catch (RuntimeException ex) {
                    if (failure == null) {
                        failure = ex;
                    }
                }
            }
            if (time < duration) {
                break;
            }
            if (looping && duration > 0.0f && guard++ < 64) {
                time = 0.0f;
                nextEvent = 0;
                if (remaining <= 0.0f) {
                    break;
                }
                continue;
            }
            finished = true;
            if (onFinish != null) {
                onFinish.run();
            }
            break;
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Valor da trilha no instante atual, ou {@code fallback} se ela nao existe. */
    public float value(String track, float fallback) {
        Curve c = tracks.get(track);
        return c == null ? fallback : c.sample(time);
    }

    public boolean hasTrack(String track) {
        return tracks.containsKey(track);
    }

    /** Pula para {@code seconds} sem disparar os eventos intermediarios. */
    public void seek(float seconds) {
        time = Math.max(0.0f, Math.min(seconds, duration));
        nextEvent = 0;
        while (nextEvent < events.size() && events.get(nextEvent).time < time) {
            nextEvent++;
        }
        finished = false;
    }

    public void restart() {
        time = 0.0f;
        nextEvent = 0;
        finished = false;
        started = false;
    }

    public void stop() {
        finished = true;
    }

    public float time() {
        return time;
    }

    public float duration() {
        return duration;
    }

    /** Progresso em [0, 1]. */
    public float progress() {
        return duration <= 0.0f ? 1.0f : time / duration;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isFinished() {
        return finished;
    }
}
