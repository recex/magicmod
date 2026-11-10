package com.arcanemod.magicmod.engine.core;

import com.arcanemod.magicmod.client.config.VfxConfig;
import com.arcanemod.magicmod.engine.audio.AudioEngine;
import com.arcanemod.magicmod.engine.cinematic.CameraDirector;
import com.arcanemod.magicmod.engine.particle.GpuParticleSystem;
import com.arcanemod.magicmod.engine.perf.PerfMonitor;
import com.arcanemod.magicmod.engine.perf.Quality;
import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.render.PostState;
import com.arcanemod.magicmod.engine.render.RenderEngine;
import com.arcanemod.magicmod.engine.shader.ShaderLibrary;
import com.arcanemod.magicmod.engine.vfx.Fx;
import com.arcanemod.magicmod.engine.vfx.VfxElement;
import com.arcanemod.magicmod.engine.vfx.VfxEngine;
import com.arcanemod.magicmod.engine.world.WorldFx;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

/**
 * Nucleo do motor de efeitos: reune os modulos (shaders, render, particulas,
 * efeitos, camera, audio, ambiente, desempenho), mantem o relogio e conduz o
 * quadro. Integra-se ao jogo so por eventos do Forge - sem mixins, sem
 * reflexao em classes do Minecraft.
 *
 * <p>Quando nao ha efeito ativo o motor nao faz nada: nenhum framebuffer e
 * tocado e nenhum estado do OpenGL muda.
 */
public final class ArcaneEngine {

    private static final Logger LOGGER = LogManager.getLogger("MagicMod/Engine");
    private static ArcaneEngine instance;

    public final ShaderLibrary shaders = new ShaderLibrary();
    public final RenderEngine renderer = new RenderEngine(shaders);
    public final VfxEngine vfx = new VfxEngine();
    public final GpuParticleSystem particles = new GpuParticleSystem(Quality.HIGH.particleCapacity);
    public final PostState post = new PostState();
    public final FrameContext frame = new FrameContext();
    public final CameraDirector camera = new CameraDirector();
    public final AudioEngine audio = new AudioEngine();
    public final WorldFx world = new WorldFx();
    public final Random random = new Random();

    private PerfMonitor perf = new PerfMonitor(Quality.HIGH, Quality.HIGH);
    private int ticks;
    private float time;
    private float lastPartial;
    private long lastFrameNanos;
    private float maxStep = 0.25f;
    private boolean reloadRequested;
    private boolean releaseRequested;
    private boolean particlesSupported = true;
    private boolean capabilitiesChecked;
    private int failures;
    private boolean failed;
    private String failureReason = "";
    private Quality forcedQuality;
    private Quality lastQuality = Quality.HIGH;
    private boolean shaderPack;
    private Method irisInUse;
    private Object irisApi;
    private boolean irisLooked;
    private boolean wasActive;

    private ArcaneEngine() {
    }

    /** O motor, ou {@code null} antes da inicializacao do cliente. */
    public static ArcaneEngine get() {
        return instance;
    }

    /** Cria o motor e registra os seus ouvintes de eventos. Chamar uma vez, no setup do cliente. */
    public static ArcaneEngine create() {
        if (instance == null) {
            instance = new ArcaneEngine();
            MinecraftForge.EVENT_BUS.register(instance);
            MinecraftForge.EVENT_BUS.register(instance.camera);
            MinecraftForge.EVENT_BUS.register(instance.world);
        }
        return instance;
    }

    /** Verdadeiro se o motor pode desenhar agora; se nao, as magias usam as particulas simples. */
    public boolean isUsable() {
        return VfxConfig.enabled && !failed && !shaderPack;
    }

    /** Por que o motor esta inativo (texto vazio se esta ativo). */
    public String inactiveReason() {
        if (!VfxConfig.enabled) {
            return "desligado na configuracao";
        }
        if (failed) {
            return "desativado apos erro: " + failureReason;
        }
        if (shaderPack) {
            return "pacote de shaders (Oculus/Iris) em uso";
        }
        return "";
    }

    /** Segundos de jogo desde a criacao do motor. */
    public float time() {
        return time;
    }

    public float timeAt(float partialTick) {
        return (ticks + partialTick) / 20.0f;
    }

    /** Multiplicador de quantidade de particulas do nivel de qualidade atual. */
    public float particleScale() {
        return particlesSupported ? lastQuality.particleScale : 0.0f;
    }

    public Quality quality() {
        return lastQuality;
    }

    public float averageFrameMs() {
        return perf.averageMs();
    }

    /** Fixa a qualidade e torna o relogio insensivel a quadros lentos (autoteste). */
    public void forceQuality(Quality quality) {
        this.forcedQuality = quality;
        this.maxStep = quality == null ? 0.25f : 30.0f;
        renderer.setCheckErrors(quality != null);
    }

    /** Comeca a montar um efeito no ponto dado. */
    public Fx fx(double x, double y, double z) {
        return new Fx(this, x, y, z);
    }

    /** Pede a recompilacao dos shaders no proximo quadro. */
    public void requestShaderReload() {
        reloadRequested = true;
    }

    /** Remove todos os efeitos em andamento. */
    public void stopAll() {
        vfx.clear();
        particles.clear();
        audio.stopAll();
        camera.stop();
    }

    /** Tenta de novo depois de uma desativacao por erro. */
    public void clearFailure() {
        failed = false;
        failures = 0;
        failureReason = "";
    }

    private Quality resolveQuality(float realSeconds) {
        if (forcedQuality != null) {
            return forcedQuality;
        }
        Quality fixed = Quality.byName(VfxConfig.quality, null);
        if (fixed != null) {
            return fixed;
        }
        return perf.frame(realSeconds, true);
    }

    private void checkShaderPack() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                irisInUse = null;
            }
        }
        if (irisInUse != null) {
            try {
                shaderPack = Boolean.TRUE.equals(irisInUse.invoke(irisApi));
            } catch (Throwable ignored) {
                shaderPack = false;
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!mc.isPaused()) {
            ticks++;
        }
        if (ticks % 20 == 0) {
            checkShaderPack();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && camera.isShotActive()) {
            camera.updateShot(timeAt(event.renderTickTime), event.renderTickTime);
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
        world.reset();
        camera.accept(0.0f, 0.0f);
        frame.resetHistory();
        releaseRequested = true;
    }

    private void goIdle(float realSeconds) {
        if (wasActive) {
            post.reset(0);
            camera.accept(0.0f, 0.0f);
            world.accept(post);
            frame.resetHistory();
            wasActive = false;
        }
        perf.frame(realSeconds, false);
    }

    /** Roda depois de o mundo ser desenhado e antes da mao e da interface. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long nanos = System.nanoTime();
        float real = lastFrameNanos == 0L ? 0.016f : (nanos - lastFrameNanos) / 1.0e9f;
        lastFrameNanos = nanos;
        float partial = mc.isPaused() ? lastPartial : event.getPartialTick();
        lastPartial = partial;
        float now = timeAt(partial);
        float dt = Math.max(0.0f, Math.min(maxStep, now - time));
        time = now;
        frame.time = now;
        frame.dt = dt;
        frame.partialTick = partial;
        if (releaseRequested) {
            releaseRequested = false;
            renderer.releaseTargets();
        }
        if (reloadRequested) {
            reloadRequested = false;
            shaders.reload();
            clearFailure();
        }
        if (!isUsable() || mc.level == null) {
            if (!vfx.isIdle()) {
                stopAll();
            }
            goIdle(real);
            return;
        }
        if (vfx.isIdle() && particles.isIdle(now) && !camera.isShotActive()) {
            goIdle(real);
            return;
        }
        try {
            if (!capabilitiesChecked) {
                capabilitiesChecked = true;
                particlesSupported = GpuParticleSystem.isSupported();
                if (!particlesSupported) {
                    LOGGER.warn("Placa sem instancias (OpenGL 3.3): particulas do motor desativadas.");
                }
            }
            Quality q = resolveQuality(real);
            lastQuality = q;
            if (particles.capacity() != q.particleCapacity && particles.isIdle(now)) {
                particles.resize(q.particleCapacity);
            }
            RenderTarget main = mc.getMainRenderTarget();
            Vec3 cam = event.getCamera().getPosition();
            frame.quality = q;
            frame.frameId++;
            frame.width = main.width;
            frame.height = main.height;
            frame.setCamera(cam.x, cam.y, cam.z, event.getProjectionMatrix(), RenderSystem.getInverseViewRotationMatrix());
            vfx.setMaxDistance(VfxConfig.maxDistance);
            vfx.update(frame);
            post.reset(VfxConfig.dynamicLights ? q.maxLights : 0);
            List<VfxElement<?>> visible = vfx.prepare(frame, post);
            post.letterbox = Math.max(post.letterbox, camera.letterbox());
            post.flashA *= VfxConfig.flashIntensity;
            post.impact *= VfxConfig.flashIntensity;
            camera.accept(post.shake * VfxConfig.screenShake, post.fovKick);
            world.accept(post);
            wasActive = true;
            if (visible.isEmpty() && particles.isIdle(now) && post.isNeutral()) {
                return;
            }
            renderer.render(frame, visible, vfx.hasDistortion(), particles, post, 0.25f * VfxConfig.bloom, VfxConfig.distortion);
            failures = 0;
        } catch (Throwable t) {
            failures++;
            LOGGER.error("Falha no quadro de efeitos ({}/3)", failures, t);
            stopAll();
            if (failures >= 3) {
                failed = true;
                failureReason = String.valueOf(t);
                LOGGER.error("Motor de efeitos desativado; as magias voltam as particulas simples. Use /magicvfx reload para tentar de novo.");
            }
        }
    }
}
