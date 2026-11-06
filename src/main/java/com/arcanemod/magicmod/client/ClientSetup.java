package com.arcanemod.magicmod.client;

import com.arcanemod.magicmod.client.particle.ArcaneSparkleParticle;
import com.arcanemod.magicmod.client.shader.SpellShaderTickHandler;
import com.arcanemod.magicmod.registry.ModParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::clientSetup);
        modEventBus.addListener(ClientSetup::registerParticles);
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new SpellShaderTickHandler());
        MinecraftForge.EVENT_BUS.register(new SpellCastInputHandler());
    }

    private static void registerParticles(final RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ARCANE_SPARKLE.get(), ArcaneSparkleParticle.SparkleProvider::new);
        event.registerSpriteSet(ModParticles.IMPACT_SHOCKWAVE.get(), ArcaneSparkleParticle.SparkleProvider::new);
        event.registerSpriteSet(ModParticles.MANA_TRAIL.get(), ArcaneSparkleParticle.SparkleProvider::new);
        event.registerSpriteSet(ModParticles.RUNE_GLYPH.get(), ArcaneSparkleParticle.SparkleProvider::new);
    }
}
