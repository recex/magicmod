package com.arcanemod.magicmod.engine.perf;

/**
 * Qualidade adaptativa: mede o tempo de quadro enquanto ha efeitos na tela e
 * baixa o nivel quando o jogo fica lento, voltando a subir quando sobra folga.
 * A histerese (tempos diferentes para descer e subir) evita ficar oscilando.
 * Classe pura, coberta por testes unitarios.
 */
public final class PerfMonitor {

    /** Acima disto (ms por quadro) o motor esta pesando: cerca de 36 fps. */
    public static final float SLOW_MS = 28.0f;
    /** Abaixo disto (ms por quadro) ha folga para subir: cerca de 80 fps. */
    public static final float FAST_MS = 12.5f;
    private static final float DOWN_AFTER_SECONDS = 0.75f;
    private static final float UP_AFTER_SECONDS = 5.0f;

    private float emaMs = 16.0f;
    private float slowFor;
    private float fastFor;
    private Quality current;
    private final Quality ceiling;

    public PerfMonitor(Quality start, Quality ceiling) {
        this.ceiling = ceiling;
        this.current = start.ordinal() > ceiling.ordinal() ? ceiling : start;
    }

    /**
     * Registra um quadro. {@code frameSeconds} e o tempo real do quadro e
     * {@code active} diz se o motor desenhou algo nele (quadros ociosos nao
     * contam: a lentidao nao seria culpa dos efeitos).
     */
    public Quality frame(float frameSeconds, boolean active) {
        if (!active || frameSeconds <= 0.0f) {
            slowFor = 0.0f;
            return current;
        }
        float ms = Math.min(frameSeconds * 1000.0f, 250.0f);
        emaMs += (ms - emaMs) * 0.15f;
        if (emaMs > SLOW_MS) {
            slowFor += frameSeconds;
            fastFor = 0.0f;
            if (slowFor >= DOWN_AFTER_SECONDS && current != Quality.LOW) {
                current = current.lower();
                slowFor = 0.0f;
                emaMs = (SLOW_MS + FAST_MS) * 0.5f;
            }
        } else if (emaMs < FAST_MS) {
            fastFor += frameSeconds;
            slowFor = 0.0f;
            if (fastFor >= UP_AFTER_SECONDS && current.ordinal() < ceiling.ordinal()) {
                current = current.higher();
                fastFor = 0.0f;
                emaMs = (SLOW_MS + FAST_MS) * 0.5f;
            }
        } else {
            slowFor = 0.0f;
            fastFor = 0.0f;
        }
        return current;
    }

    public Quality current() {
        return current;
    }

    public float averageMs() {
        return emaMs;
    }
}
