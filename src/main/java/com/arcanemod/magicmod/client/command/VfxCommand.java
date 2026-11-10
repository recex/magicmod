package com.arcanemod.magicmod.client.command;

import com.arcanemod.magicmod.client.config.VfxConfig;
import com.arcanemod.magicmod.client.vfx.SpellEffects;
import com.arcanemod.magicmod.engine.core.ArcaneEngine;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

import java.util.List;
import java.util.Locale;

/**
 * Comando de cliente {@code /magicvfx}: estado do motor, recarga dos shaders,
 * qualidade, camera cinematografica e demonstracao de cada efeito sem gastar
 * mana (so imagem e som; nada acontece no servidor).
 */
public final class VfxCommand {

    private static final List<String> QUALITIES = List.of("auto", "low", "medium", "high", "ultra");

    private VfxCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("magicvfx")
                .executes(VfxCommand::status)
                .then(Commands.literal("status").executes(VfxCommand::status))
                .then(Commands.literal("reload").executes(ctx -> {
                    ArcaneEngine engine = ArcaneEngine.get();
                    if (engine != null) {
                        engine.requestShaderReload();
                    }
                    return say(ctx, "Shaders do motor serao recompilados no proximo quadro.");
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    ArcaneEngine engine = ArcaneEngine.get();
                    if (engine != null) {
                        engine.stopAll();
                    }
                    return say(ctx, "Efeitos interrompidos.");
                }))
                .then(Commands.literal("on").executes(ctx -> {
                    VfxConfig.setEnabled(true);
                    return say(ctx, "Motor de efeitos ligado.");
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    VfxConfig.setEnabled(false);
                    return say(ctx, "Motor de efeitos desligado: as magias usam as particulas simples.");
                }))
                .then(Commands.literal("quality").then(Commands.argument("nivel", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(QUALITIES, builder))
                        .executes(ctx -> {
                            String level = StringArgumentType.getString(ctx, "nivel").toLowerCase(Locale.ROOT);
                            if (!QUALITIES.contains(level)) {
                                return say(ctx, "Niveis: " + String.join(", ", QUALITIES));
                            }
                            VfxConfig.setQuality(level);
                            return say(ctx, "Qualidade dos efeitos: " + level);
                        })))
                .then(Commands.literal("cinematic").then(Commands.argument("estado", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("on", "off"), builder))
                        .executes(ctx -> {
                            boolean on = "on".equalsIgnoreCase(StringArgumentType.getString(ctx, "estado"));
                            VfxConfig.setCinematicCamera(on);
                            return say(ctx, "Camera cinematografica: " + (on ? "ligada" : "desligada"));
                        })))
                .then(Commands.literal("demo").then(Commands.argument("efeito", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SpellEffects.demoNames(), builder))
                        .executes(VfxCommand::demo))));
    }

    private static int say(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal("[Arcane VFX] " + text), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ArcaneEngine engine = ArcaneEngine.get();
        if (engine == null) {
            return say(ctx, "motor nao inicializado.");
        }
        String reason = engine.inactiveReason();
        say(ctx, reason.isEmpty() ? "motor ativo" : "motor inativo (" + reason + ")");
        say(ctx, String.format(Locale.ROOT, "qualidade %s (config: %s), %.1f ms/quadro com efeitos, HDR %s",
                engine.quality(), VfxConfig.quality, engine.averageFrameMs(), engine.renderer.isHdr() ? "sim" : "nao"));
        say(ctx, String.format(Locale.ROOT, "elementos %d, particulas em uso %d/%d, shaders %d (falhas %d), descartados %d",
                engine.vfx.elementCount(), engine.particles.used(), engine.particles.capacity(), engine.shaders.programCount(),
                engine.shaders.failureCount(), engine.vfx.droppedCount()));
        for (String failure : engine.shaders.failureMessages()) {
            say(ctx, "shader com erro: " + (failure.length() > 160 ? failure.substring(0, 160) + "..." : failure));
        }
        return 1;
    }

    private static int demo(CommandContext<CommandSourceStack> ctx) {
        ArcaneEngine engine = ArcaneEngine.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (engine == null || player == null || mc.level == null) {
            return 0;
        }
        if (!engine.isUsable()) {
            return say(ctx, "motor inativo (" + engine.inactiveReason() + ")");
        }
        String name = StringArgumentType.getString(ctx, "efeito").toLowerCase(Locale.ROOT);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(24.0));
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 p = hit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(10.0)) : hit.getLocation();
        if (!SpellEffects.playDemo(engine, name, p.x, p.y, p.z, player)) {
            return say(ctx, "Efeitos: " + String.join(", ", SpellEffects.demoNames()));
        }
        return 1;
    }
}
