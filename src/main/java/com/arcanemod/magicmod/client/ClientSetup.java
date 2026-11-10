package com.arcanemod.magicmod.client;

import com.arcanemod.magicmod.client.command.VfxCommand;
import com.arcanemod.magicmod.client.config.VfxConfig;
import com.arcanemod.magicmod.client.particle.ArcaneSparkleParticle;
import com.arcanemod.magicmod.client.selftest.SelfTestRunner;
import com.arcanemod.magicmod.client.selftest.VfxSelfTest;
import com.arcanemod.magicmod.engine.core.ArcaneEngine;
import com.arcanemod.magicmod.registry.ModParticles;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {

    public static void init(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VfxConfig.SPEC);
        modEventBus.addListener(ClientSetup::clientSetup);
        modEventBus.addListener(ClientSetup::registerParticles);
        modEventBus.addListener(ClientSetup::registerReloadListeners);
        modEventBus.addListener(ClientSetup::onConfig);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new SpellCastInputHandler());
        MinecraftForge.EVENT_BUS.addListener(VfxCommand::register);
        VfxConfig.bake();
        ArcaneEngine.create();
        if (SelfTestRunner.isRequested()) {
            VfxSelfTest.register();
            event.enqueueWork(() -> MinecraftForge.EVENT_BUS.register(SelfTestRunner.create()));
        }
    }

    private static void onConfig(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == VfxConfig.SPEC) {
            VfxConfig.bake();
        }
    }

    /** Ao recarregar recursos (F3+T ou troca de resource pack) os shaders do motor sao recompilados. */
    private static void registerReloadListeners(final RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            ArcaneEngine engine = ArcaneEngine.get();
            if (engine != null) {
                engine.requestShaderReload();
            }
        });
    }

    private static void registerParticles(final RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ARCANE_SPARKLE.get(), ArcaneSparkleParticle.SparkleProvider::new);
        event.registerSpriteSet(ModParticles.IMPACT_SHOCKWAVE.get(), ArcaneSparkleParticle.SparkleProvider::new);
        event.registerSpriteSet(ModParticles.MANA_TRAIL.get(), ArcaneSparkleParticle.SparkleProvider::new);
        event.registerSpriteSet(ModParticles.RUNE_GLYPH.get(), ArcaneSparkleParticle.SparkleProvider::new);
    }
}
