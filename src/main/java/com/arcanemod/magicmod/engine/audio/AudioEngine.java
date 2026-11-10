package com.arcanemod.magicmod.engine.audio;

import com.arcanemod.magicmod.client.config.VfxConfig;
import com.arcanemod.magicmod.engine.math.Curve;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio dos efeitos: sons em camadas sincronizados com a animacao, atraso
 * pela distancia (o estrondo de uma explosao longe chega depois do clarao) e
 * sons continuos com envelope de volume e tom (cargas, zumbidos, vortices).
 * Usa apenas sons que ja existem no jogo, combinados e afinados.
 */
public final class AudioEngine {

    /** Velocidade "de cinema" do som, em blocos por tick: da atraso perceptivel sem parecer erro. */
    private static final float BLOCKS_PER_TICK = 6.0f;
    private static final int MAX_LOOPS = 12;

    private final RandomSource random = SoundInstance.createUnseededRandom();
    private final List<EnvelopeSound> loops = new ArrayList<>();

    /** Toca um som no ponto dado. {@code volume} acima de 1 aumenta o alcance. */
    public void play(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        float gain = VfxConfig.soundVolume;
        if (sound == null || mc.level == null || gain <= 0.0f || volume <= 0.0f) {
            return;
        }
        float jitter = 0.96f + random.nextFloat() * 0.08f;
        SimpleSoundInstance instance = new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume * gain,
                clampPitch(pitch * jitter), random, x, y, z);
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        double dist = Math.sqrt(ear.distanceToSqr(x, y, z));
        int delay = dist > 20.0 ? Math.min(24, (int) (dist / BLOCKS_PER_TICK)) : 0;
        if (delay > 0) {
            mc.getSoundManager().playDelayed(instance, delay);
        } else {
            mc.getSoundManager().play(instance);
        }
    }

    /**
     * Som continuo por {@code seconds}, com volume e tom seguindo curvas
     * (dominio 0..1). Devolve um controle para interromper antes.
     */
    public Runnable loop(SoundEvent sound, double x, double y, double z, float seconds, Curve volume, Curve pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (sound == null || mc.level == null || VfxConfig.soundVolume <= 0.0f) {
            return () -> { };
        }
        loops.removeIf(EnvelopeSound::isStopped);
        if (loops.size() >= MAX_LOOPS) {
            return () -> { };
        }
        EnvelopeSound s = new EnvelopeSound(sound, x, y, z, Math.max(2, Math.round(seconds * 20.0f)), volume, pitch);
        loops.add(s);
        mc.getSoundManager().play(s);
        return s::end;
    }

    public void stopAll() {
        for (EnvelopeSound s : loops) {
            s.end();
        }
        loops.clear();
    }

    private static float clampPitch(float pitch) {
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    private static final class EnvelopeSound extends AbstractTickableSoundInstance {
        private final int lifeTicks;
        private final Curve volumeCurve;
        private final Curve pitchCurve;
        private int age;

        EnvelopeSound(SoundEvent sound, double x, double y, double z, int lifeTicks, Curve volume, Curve pitch) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.lifeTicks = lifeTicks;
            this.volumeCurve = volume;
            this.pitchCurve = pitch;
            this.x = x;
            this.y = y;
            this.z = z;
            this.looping = true;
            this.delay = 0;
            this.volume = Math.max(0.01f, volume.sample(0.0f) * VfxConfig.soundVolume);
            this.pitch = clampPitch(pitch.sample(0.0f));
        }

        @Override
        public void tick() {
            age++;
            if (age >= lifeTicks) {
                stop();
                return;
            }
            float t = age / (float) lifeTicks;
            this.volume = Math.max(0.0f, volumeCurve.sample(t) * VfxConfig.soundVolume);
            this.pitch = clampPitch(pitchCurve.sample(t));
        }

        void end() {
            stop();
        }
    }
}
