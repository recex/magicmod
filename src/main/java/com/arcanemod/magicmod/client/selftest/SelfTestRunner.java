package com.arcanemod.magicmod.client.selftest;

import com.arcanemod.magicmod.MagicMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Autoteste do cliente, usado pela integracao continua para validar o motor
 * grafico dentro do jogo de verdade (janela virtual + OpenGL por software).
 *
 * So e registrado quando o jogo sobe com {@code -Dmagicmod.selftest=...}; em
 * uso normal esta classe nunca e carregada. O roteiro e:
 * <ol>
 *   <li>esperar o carregamento de recursos e anotar a versao do OpenGL;</li>
 *   <li>criar um mundo plano temporario em modo criativo;</li>
 *   <li>rodar cada cena registrada, capturando quadros em instantes fixos;</li>
 *   <li>gravar {@code selftest/report.txt} e encerrar o jogo.</li>
 * </ol>
 * Tudo que toca OpenGL roda nos eventos de tick/render, ou seja, na thread
 * de render do Minecraft.
 */
public final class SelfTestRunner {

    public static final String PROPERTY = "magicmod.selftest";
    private static final int LARGURA_QUADRO = 480;
    private static final int LIMITE_SEGUNDOS = 900;

    /** Uma cena do roteiro: prepara algo e captura quadros em ticks fixos. */
    public static final class Scene {
        final String name;
        final Runnable start;
        final int[] captureTicks;
        final int lengthTicks;

        public Scene(String name, Runnable start, int lengthTicks, int... captureTicks) {
            this.name = name;
            this.start = start;
            this.captureTicks = captureTicks;
            this.lengthTicks = lengthTicks;
        }
    }

    private enum Stage { BOOT, CREATE_WORLD, WAIT_WORLD, SETTLE, SCENES, FINISH, DONE }

    private static final List<Scene> SCENES = new ArrayList<>();
    private static final List<Runnable> STATIC_CHECKS = new ArrayList<>();
    private static SelfTestRunner instance;

    private final Path dir;
    private final Path report;
    private final String mode;
    private Stage stage = Stage.BOOT;
    private int stageTicks;
    private int sceneIndex = -1;
    private int sceneTicks;
    private int failures;
    private int frames;
    private String pendingCapture;
    private long frameCounter;
    private long sceneStartNanos;
    private long sceneStartFrame;

    private SelfTestRunner(String mode) {
        this.mode = mode;
        this.dir = Minecraft.getInstance().gameDirectory.toPath().resolve("selftest");
        this.report = dir.resolve("report.txt");
    }

    public static boolean isRequested() {
        String value = System.getProperty(PROPERTY);
        return value != null && !value.isEmpty() && !"false".equalsIgnoreCase(value);
    }

    /** Cria o autoteste (uma unica vez) para ser registrado no barramento do Forge. */
    public static SelfTestRunner create() {
        if (instance == null) {
            instance = new SelfTestRunner(System.getProperty(PROPERTY, "full"));
            instance.begin();
        }
        return instance;
    }

    /** Registra uma cena a ser executada dentro do mundo de teste. */
    public static void addScene(Scene scene) {
        SCENES.add(scene);
    }

    /** Registra uma verificacao executada uma vez, ainda na tela inicial. */
    public static void addStaticCheck(Runnable check) {
        STATIC_CHECKS.add(check);
    }

    /** Anota uma linha no relatorio (visivel para a integracao continua). */
    public static void note(String line) {
        if (instance != null) {
            instance.write(line);
        }
    }

    /** Anota uma falha: o autoteste termina com RESULT: FAIL. */
    public static void fail(String line) {
        if (instance != null) {
            instance.failures++;
            instance.write("FAIL: " + line);
        }
    }

    public static boolean isActive() {
        return instance != null;
    }

    private void begin() {
        try {
            Files.createDirectories(dir);
            Files.writeString(report, "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            MagicMod.LOGGER.error("Autoteste: nao consegui preparar {}", dir, e);
        }
        write("SELFTEST: mode=" + mode);
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(LIMITE_SEGUNDOS * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            write("FAIL: tempo limite de " + LIMITE_SEGUNDOS + "s estourado no estagio " + stage);
            write("RESULT: FAIL");
            Runtime.getRuntime().halt(3);
        }, "magicmod-selftest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private synchronized void write(String line) {
        MagicMod.LOGGER.info("[autoteste] {}", line);
        try {
            Files.writeString(report, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            MagicMod.LOGGER.error("Autoteste: falha ao gravar relatorio", e);
        }
    }

    private void writeError(String where, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        failures++;
        write("FAIL: excecao em " + where + ": " + sw);
    }

    private void go(Stage next) {
        stage = next;
        stageTicks = 0;
        write("STAGE: " + next);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) {
            return;
        }
        try {
            step(Minecraft.getInstance());
        } catch (Throwable t) {
            writeError("estagio " + stage, t);
            go(Stage.FINISH);
        }
    }

    private void step(Minecraft mc) {
        stageTicks++;
        switch (stage) {
            case BOOT -> {
                if (mc.getOverlay() == null && stageTicks > 20) {
                    write("GL_VERSION: " + GL11.glGetString(GL11.GL_VERSION));
                    write("GL_RENDERER: " + GL11.glGetString(GL11.GL_RENDERER));
                    write("GL_VENDOR: " + GL11.glGetString(GL11.GL_VENDOR));
                    write("GLSL_VERSION: " + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
                    write("WINDOW: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
                    for (Runnable check : STATIC_CHECKS) {
                        try {
                            check.run();
                        } catch (Throwable t) {
                            writeError("verificacao estatica", t);
                        }
                    }
                    go("title".equalsIgnoreCase(mode) ? Stage.FINISH : Stage.CREATE_WORLD);
                }
            }
            case CREATE_WORLD -> {
                LevelSettings settings = new LevelSettings("magicmod_selftest", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                Function<RegistryAccess, WorldDimensions> dims = access -> access
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
                go(Stage.WAIT_WORLD);
                mc.createWorldOpenFlows().createFreshLevel("magicmod_selftest_" + System.currentTimeMillis(),
                        settings, new WorldOptions(20240229L, false, false), dims);
            }
            case WAIT_WORLD -> {
                if (mc.level != null && mc.player != null && mc.getOverlay() == null) {
                    prepareWorld(mc);
                    go(Stage.SETTLE);
                } else if (stageTicks > 20 * 240) {
                    fail("o mundo de teste nao carregou em 240s");
                    go(Stage.FINISH);
                }
            }
            case SETTLE -> {
                if (mc.screen != null) {
                    mc.setScreen(null);
                }
                if (stageTicks == 100) {
                    pendingCapture = "00_mundo";
                }
                if (stageTicks > 110) {
                    go(Stage.SCENES);
                }
            }
            case SCENES -> stepScenes(mc);
            case FINISH -> {
                write("FRAMES: " + frames);
                write("FAILURES: " + failures);
                write("RESULT: " + (failures == 0 ? "PASS" : "FAIL"));
                stage = Stage.DONE;
                mc.stop();
            }
            default -> { }
        }
    }

    private void prepareWorld(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            fail("sem servidor integrado");
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setDayTime(13800L);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                player.teleportTo(level, 0.5, y, 0.5, 0.0f, 8.0f);
            }
        });
        write("WORLD: pronto");
    }

    private void stepScenes(Minecraft mc) {
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        if (sceneIndex < 0 || sceneTicks > SCENES.get(sceneIndex).lengthTicks) {
            if (sceneIndex >= 0) {
                Scene done = SCENES.get(sceneIndex);
                double seconds = (System.nanoTime() - sceneStartNanos) / 1.0e9;
                long drawn = frameCounter - sceneStartFrame;
                write(String.format(java.util.Locale.ROOT, "SCENE_DONE: %s frames=%d fps=%.1f",
                        done.name, drawn, seconds > 0 ? drawn / seconds : 0.0));
            }
            sceneIndex++;
            sceneTicks = 0;
            if (sceneIndex >= SCENES.size()) {
                go(Stage.FINISH);
                return;
            }
            Scene scene = SCENES.get(sceneIndex);
            write("SCENE: " + scene.name);
            sceneStartNanos = System.nanoTime();
            sceneStartFrame = frameCounter;
            try {
                scene.start.run();
            } catch (Throwable t) {
                writeError("cena " + scene.name, t);
            }
            return;
        }
        sceneTicks++;
        Scene scene = SCENES.get(sceneIndex);
        for (int i = 0; i < scene.captureTicks.length; i++) {
            if (scene.captureTicks[i] == sceneTicks) {
                pendingCapture = String.format(java.util.Locale.ROOT, "%02d_%s_%d", sceneIndex + 1, scene.name, i);
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        frameCounter++;
        if (pendingCapture == null) {
            return;
        }
        String name = pendingCapture;
        pendingCapture = null;
        try {
            capture(Minecraft.getInstance(), name);
        } catch (Throwable t) {
            writeError("captura " + name, t);
        }
    }

    private void capture(Minecraft mc, String name) throws IOException {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            fail("erro OpenGL 0x" + Integer.toHexString(error) + " antes da captura " + name);
        }
        try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = Math.min(LARGURA_QUADRO, full.getWidth());
            int h = Math.max(1, Math.round(w * (full.getHeight() / (float) full.getWidth())));
            try (NativeImage small = new NativeImage(w, h, false)) {
                full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), small);
                small.writeToFile(dir.resolve(name + ".png"));
            }
        }
        frames++;
        write("FRAME: " + name + " fps=" + mc.getFps());
    }
}
