package com.arcanemod.magicmod.registry;

import com.arcanemod.magicmod.MagicMod;
import com.arcanemod.magicmod.client.particle.ArcaneSparkleParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Tipos de particula customizados usados pelas 30 magias. Cada magia
 * reusa esses tipos base variando cor/escala/velocidade via
 * ArcaneSparkleParticleOption, em vez de precisar de uma classe de
 * particula nova por magia.
 */
public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MagicMod.MOD_ID);

    public static final RegistryObject<ParticleType<ArcaneSparkleParticleOption>> ARCANE_SPARKLE =
            PARTICLE_TYPES.register("arcane_sparkle", () -> new ParticleType<>(false, ArcaneSparkleParticleOption.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<ArcaneSparkleParticleOption> codec() {
                    return ArcaneSparkleParticleOption.codec(this);
                }
            });

    public static final RegistryObject<ParticleType<ArcaneSparkleParticleOption>> IMPACT_SHOCKWAVE =
            PARTICLE_TYPES.register("impact_shockwave", () -> new ParticleType<>(false, ArcaneSparkleParticleOption.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<ArcaneSparkleParticleOption> codec() {
                    return ArcaneSparkleParticleOption.codec(this);
                }
            });

    public static final RegistryObject<ParticleType<ArcaneSparkleParticleOption>> MANA_TRAIL =
            PARTICLE_TYPES.register("mana_trail", () -> new ParticleType<>(false, ArcaneSparkleParticleOption.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<ArcaneSparkleParticleOption> codec() {
                    return ArcaneSparkleParticleOption.codec(this);
                }
            });

    public static final RegistryObject<ParticleType<ArcaneSparkleParticleOption>> RUNE_GLYPH =
            PARTICLE_TYPES.register("rune_glyph", () -> new ParticleType<>(false, ArcaneSparkleParticleOption.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<ArcaneSparkleParticleOption> codec() {
                    return ArcaneSparkleParticleOption.codec(this);
                }
            });
}
