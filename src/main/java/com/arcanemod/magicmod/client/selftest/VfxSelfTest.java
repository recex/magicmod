package com.arcanemod.magicmod.client.selftest;

import com.arcanemod.magicmod.MagicMod;
import com.arcanemod.magicmod.client.SpellVfxDispatcher;
import com.arcanemod.magicmod.client.config.VfxConfig;
import com.arcanemod.magicmod.client.selftest.SelfTestRunner.Scene;
import com.arcanemod.magicmod.client.vfx.SpellEffects;
import com.arcanemod.magicmod.engine.core.ArcaneEngine;
import com.arcanemod.magicmod.engine.perf.Quality;
import com.arcanemod.magicmod.network.ModNetwork;
import com.arcanemod.magicmod.network.RequestCastSpellPacket;
import com.arcanemod.magicmod.network.SpellVfxPacket;
import com.arcanemod.magicmod.registry.ModSpells;
import com.arcanemod.magicmod.spell.Spell;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cenas do autoteste do motor de efeitos: cada efeito e disparado no mundo de
 * teste, com quadros capturados em instantes escolhidos, e ao fim de cada cena
 * conferem-se shaders compilados, erros de OpenGL e quadros desenhados. So e
 * registrado quando o jogo sobe com {@code -Dmagicmod.selftest}.
 */
public final class VfxSelfTest {

    private static float sceneStart;
    private static long framesAtStart;
    private static int errorsAtStart;
    private static int shaderFailuresReported;
    private static int dispatchedAtStart;

    private VfxSelfTest() {
    }

    public static void register() {
        SelfTestRunner.frameInfo = () -> {
            ArcaneEngine e = ArcaneEngine.get();
            if (e == null) {
                return "motor=ausente";
            }
            return String.format(Locale.ROOT, "t=%.2fs q=%s elementos=%d particulas=%d ms=%.0f", e.time() - sceneStart, e.quality(),
                    e.vfx.elementCount(), e.particles.used(), e.averageFrameMs());
        };
        SelfTestRunner.addStaticCheck(() -> {
            ArcaneEngine e = ArcaneEngine.get();
            if (e == null) {
                SelfTestRunner.fail("motor de efeitos nao foi criado");
            } else if (!e.isUsable()) {
                SelfTestRunner.fail("motor de efeitos inativo: " + e.inactiveReason());
            } else {
                SelfTestRunner.note("VFX: motor criado; efeitos de demonstracao: " + SpellEffects.demoNames().size()
                        + "; magias registradas: " + ModSpells.all().size());
            }
        });

        effect("fire", 10.0, false, 40, 3, 9, 22);
        effect("frost", 10.0, false, 60, 4, 30, 48);
        effect("arcane", 10.0, false, 45, 3, 8, 20);
        effect("nature", 9.0, false, 60, 6, 24, 50);
        effect("shadow", 10.0, false, 40, 5, 14, 24);
        effect("light", 11.0, false, 40, 3, 10, 20);
        effect("storm", 12.0, false, 30, 2, 5, 12);
        effect("blood", 9.0, false, 35, 3, 9, 20);
        effect("beam", 12.0, false, 25, 2, 6);
        effect("aura", 0.0, true, 45, 5, 18);
        effect("heal", 0.0, true, 50, 8, 24);
        effect("blink", 5.0, true, 20, 2, 6);
        effect("orb", 0.0, true, 25, 2, 6);
        effect("supernova", 17.0, false, 80, 12, 24, 28, 40, 60);
        effect("glacial_prison", 11.0, false, 90, 6, 30, 66, 72);
        effect("starfall", 15.0, false, 90, 14, 28, 44, 64);
        effect("soul_harvest", 9.0, false, 60, 8, 20, 30);
        effect("tempest_call", 16.0, false, 100, 14, 30, 50, 80);
        effect("vine_prison", 10.0, false, 85, 8, 30, 68);
        effect("singularity", 14.0, false, 120, 10, 40, 80, 96, 100);

        // Os mesmos efeitos nos outros niveis de qualidade (variantes de shader e resolucoes de buffer).
        for (Quality q : new Quality[] {Quality.LOW, Quality.MEDIUM, Quality.ULTRA}) {
            SelfTestRunner.addScene(new Scene("qualidade_" + q.name().toLowerCase(Locale.ROOT), () -> {
                ArcaneEngine e = begin(q);
                LocalPlayer p = Minecraft.getInstance().player;
                if (e == null || p == null) {
                    return;
                }
                SpellEffects.playDemo(e, "fire", p.getX() - 4.0, p.getY(), p.getZ() + 11.0, null);
                SpellEffects.playDemo(e, "arcane", p.getX() + 4.0, p.getY(), p.getZ() + 11.0, null);
                SpellEffects.playDemo(e, "light", p.getX(), p.getY(), p.getZ() + 14.0, null);
            }, 30, 8).onFinish(() -> end("qualidade_" + q.name().toLowerCase(Locale.ROOT), true)));
        }

        // Todas as magias registradas, pelo mesmo caminho dos pacotes vindos do servidor.
        for (int batch = 0; batch < 4; batch++) {
            final int index = batch;
            SelfTestRunner.addScene(new Scene("todas_" + (batch + 1), () -> {
                ArcaneEngine e = begin(Quality.LOW);
                LocalPlayer p = Minecraft.getInstance().player;
                if (e == null || p == null) {
                    return;
                }
                VfxConfig.cinematicCamera = false;
                List<Spell> all = new ArrayList<>(ModSpells.all());
                all.sort((a, b) -> a.getId().compareTo(b.getId()));
                int per = (all.size() + 3) / 4;
                int from = index * per;
                int to = Math.min(all.size(), from + per);
                StringBuilder names = new StringBuilder();
                for (int i = from; i < to; i++) {
                    int k = i - from;
                    double x = p.getX() + (k % 5 - 2) * 6.0;
                    double z = p.getZ() + 14.0 + (k / 5) * 9.0;
                    ResourceLocation id = all.get(i).getId();
                    SpellVfxDispatcher.dispatch(new SpellVfxPacket(id, p.getId(), x, p.getY(), z, 0));
                    names.append(id.getPath()).append(' ');
                }
                SelfTestRunner.note("VFX: lote " + (index + 1) + ": " + names.toString().trim());
            }, 70, 10, 40).onFinish(() -> {
                VfxConfig.bake();
                end("todas_" + (index + 1), true);
            }));
        }

        // Caminho completo: pedido ao servidor integrado -> logica da magia -> pacote de efeito -> motor.
        SelfTestRunner.addScene(new Scene("rede", () -> {
            begin(Quality.HIGH);
            dispatchedAtStart = SpellVfxDispatcher.dispatchedCount();
            ModNetwork.CHANNEL.sendToServer(new RequestCastSpellPacket(new ResourceLocation(MagicMod.MOD_ID, "storm_orb"), 0));
            ModNetwork.CHANNEL.sendToServer(new RequestCastSpellPacket(new ResourceLocation(MagicMod.MOD_ID, "frozen_ward"), 0));
        }, 40, 6, 16).onFinish(() -> {
            int got = SpellVfxDispatcher.dispatchedCount() - dispatchedAtStart;
            if (got < 2) {
                SelfTestRunner.fail("rede: esperava 2 pacotes de efeito vindos do servidor, chegaram " + got);
            } else {
                SelfTestRunner.note("VFX: rede ok, pacotes de efeito recebidos do servidor: " + got);
            }
            end("rede", true);
        }));

        // Motor desligado: as magias devem cair nas particulas simples, sem erro.
        SelfTestRunner.addScene(new Scene("legado", () -> {
            ArcaneEngine e = begin(Quality.HIGH);
            LocalPlayer p = Minecraft.getInstance().player;
            if (e == null || p == null) {
                return;
            }
            VfxConfig.enabled = false;
            SpellVfxDispatcher.dispatch(new SpellVfxPacket(new ResourceLocation(MagicMod.MOD_ID, "arcane_explosion"), p.getId(),
                    p.getX(), p.getY() + 1.0, p.getZ() + 7.0, 0));
        }, 20, 5).onFinish(() -> {
            ArcaneEngine e = ArcaneEngine.get();
            if (e != null && e.renderer.framesRendered() != framesAtStart) {
                SelfTestRunner.fail("legado: o motor desenhou mesmo desligado");
            }
            VfxConfig.bake();
            end("legado", false);
        }));

        // Depois de tudo, o jogo tem de voltar a desenhar normalmente (estado do OpenGL restaurado).
        SelfTestRunner.addScene(new Scene("limpo", () -> {
            ArcaneEngine e = begin(null);
            if (e != null && !e.isUsable()) {
                SelfTestRunner.fail("motor inativo ao final: " + e.inactiveReason());
            }
        }, 30, 25).onFinish(() -> {
            ArcaneEngine e = ArcaneEngine.get();
            if (e != null) {
                SelfTestRunner.note(String.format(Locale.ROOT, "VFX: total quadros=%d programas=%d falhas_shader=%d erros_gl=%d descartados=%d hdr=%s",
                        e.renderer.framesRendered(), e.shaders.programCount(), e.shaders.failureCount(), e.renderer.glErrorCount(),
                        e.vfx.droppedCount(), e.renderer.isHdr()));
            }
        }));
    }

    private static void effect(String name, double distance, boolean thirdPerson, int length, int... captures) {
        SelfTestRunner.addScene(new Scene(name, () -> {
            ArcaneEngine e = begin(Quality.HIGH);
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (e == null || p == null) {
                return;
            }
            mc.options.setCameraType(thirdPerson ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            if (!SpellEffects.playDemo(e, name, p.getX(), p.getY(), p.getZ() + distance, p)) {
                SelfTestRunner.fail("efeito desconhecido: " + name);
            }
        }, length, captures).onFinish(() -> {
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            end(name, true);
        }));
    }

    /** Limpa o que sobrou da cena anterior e fixa a qualidade. */
    private static ArcaneEngine begin(Quality quality) {
        ArcaneEngine e = ArcaneEngine.get();
        if (e == null) {
            SelfTestRunner.fail("motor ausente");
            return null;
        }
        e.stopAll();
        e.forceQuality(quality);
        sceneStart = e.time();
        framesAtStart = e.renderer.framesRendered();
        errorsAtStart = e.renderer.glErrorCount();
        return e;
    }

    private static void end(String name, boolean mustRender) {
        ArcaneEngine e = ArcaneEngine.get();
        if (e == null) {
            return;
        }
        long drawn = e.renderer.framesRendered() - framesAtStart;
        if (mustRender && drawn <= 0) {
            SelfTestRunner.fail(name + ": o motor nao desenhou nenhum quadro");
        }
        if (e.renderer.glErrorCount() != errorsAtStart) {
            SelfTestRunner.fail(name + ": erro do OpenGL 0x" + Integer.toHexString(e.renderer.lastGlError()) + " ("
                    + (e.renderer.glErrorCount() - errorsAtStart) + " quadros)");
        }
        List<String> failures = e.shaders.failureMessages();
        for (int i = shaderFailuresReported; i < failures.size(); i++) {
            String message = failures.get(i);
            SelfTestRunner.fail(name + ": shader: " + (message.length() > 600 ? message.substring(0, 600) : message));
        }
        shaderFailuresReported = failures.size();
        if (!e.isUsable()) {
            SelfTestRunner.fail(name + ": motor inativo: " + e.inactiveReason());
            e.clearFailure();
        }
        SelfTestRunner.note(String.format(Locale.ROOT, "VFX: %s quadros_motor=%d programas=%d ms_medio=%.0f", name, drawn,
                e.shaders.programCount(), e.averageFrameMs()));
    }
}
