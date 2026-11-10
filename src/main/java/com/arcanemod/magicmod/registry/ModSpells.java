package com.arcanemod.magicmod.registry;

import com.arcanemod.magicmod.MagicMod;
import com.arcanemod.magicmod.client.VisualProfile;
import com.arcanemod.magicmod.client.VisualProfiles;
import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellSchool;
import com.arcanemod.magicmod.spell.impl.AreaHealSpell;
import com.arcanemod.magicmod.spell.impl.BlinkSpell;
import com.arcanemod.magicmod.spell.impl.GravityWellSpell;
import com.arcanemod.magicmod.spell.impl.SelfBuffSpell;
import com.arcanemod.magicmod.spell.impl.SimpleDamageSpell;
import com.arcanemod.magicmod.spell.impl.SummonProjectileSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro central das 30 magias do mod. Cada magia e declarada em
 * uma linha (ou poucas), reaproveitando as classes genericas de
 * spell/impl/. O VisualProfile correspondente e registrado junto,
 * definindo a "receita" de efeitos visuais lida pelo
 * SpellVfxDispatcher no cliente.
 *
 * bootstrap() e chamado uma vez no FMLCommonSetupEvent.
 */
public class ModSpells {

    private static final Map<ResourceLocation, Spell> SPELLS = new LinkedHashMap<>();

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MagicMod.MOD_ID, path);
    }

    public static void bootstrap() {
        if (!SPELLS.isEmpty()) return;

        // ---------- FIRE (4) ----------
        register(new SimpleDamageSpell(id("arcane_meteor"), SpellSchool.FIRE, 40, 200, 3,
                        12.0f, 3.0f, null),
                VisualProfile.builder(0xFF5A1E)
                        .impactParticles(90).ringShockwave(true).groundCrater(true)
                        .fullscreenShader(14).particleScale(1.4f).sound("entity.generic.explode")
                        .build());

        register(new SimpleDamageSpell(id("fireball"), SpellSchool.FIRE, 15, 40, 1,
                        6.0f, 0f, () -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0)),
                VisualProfile.builder(0xFF8A3D).impactParticles(35).beamEffect(true).build());

        register(new SimpleDamageSpell(id("flame_nova"), SpellSchool.FIRE, 30, 140, 2,
                        7.0f, 4.0f, () -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1)),
                VisualProfile.builder(0xFF4500).impactParticles(70).ringShockwave(true).build());

        register(new SelfBuffSpell(id("flame_shroud"), SpellSchool.FIRE, 25, 300, 2,
                        List.of(() -> new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0),
                                () -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0)), 0),
                VisualProfile.builder(0xFF6A1E).impactParticles(25).castTrail(true).build());

        // ---------- FROST (4) ----------
        register(new SimpleDamageSpell(id("frost_bolt"), SpellSchool.FROST, 12, 30, 1,
                        5.0f, 0f, () -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2)),
                VisualProfile.builder(0x7FDFFF).impactParticles(30).beamEffect(true).build());

        register(new SimpleDamageSpell(id("blizzard"), SpellSchool.FROST, 38, 220, 3,
                        5.0f, 5.0f, () -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 3)),
                VisualProfile.builder(0xA6E9FF).impactParticles(100).ringShockwave(true)
                        .fullscreenShader(16).build());

        register(new SimpleDamageSpell(id("ice_lance"), SpellSchool.FROST, 18, 50, 1,
                        9.0f, 0f, () -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1)),
                VisualProfile.builder(0x5FC8FF).impactParticles(25).beamEffect(true).particleScale(0.8f).build());

        register(new SelfBuffSpell(id("frozen_ward"), SpellSchool.FROST, 28, 280, 2,
                        List.of(() -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 1)), 0),
                VisualProfile.builder(0x9FE8FF).impactParticles(40).build());

        // ---------- ARCANE (4) ----------
        register(new SimpleDamageSpell(id("arcane_bolt"), SpellSchool.ARCANE, 10, 20, 1,
                        4.0f, 0f, null),
                VisualProfile.builder(0xB266FF).impactParticles(20).beamEffect(true).build());

        register(new SimpleDamageSpell(id("arcane_explosion"), SpellSchool.ARCANE, 35, 180, 3,
                        9.0f, 4.5f, null),
                VisualProfile.builder(0xC48CFF).impactParticles(85).ringShockwave(true)
                        .fullscreenShader(12).build());

        register(new BlinkSpell(id("arcane_blink"), SpellSchool.ARCANE, 16, 60, 1, 10.0),
                VisualProfile.builder(0xD9B3FF).impactParticles(45).castTrail(true).build());

        register(new SelfBuffSpell(id("mana_shield"), SpellSchool.ARCANE, 20, 240, 2,
                        List.of(() -> new MobEffectInstance(MobEffects.ABSORPTION, 400, 1)), 0),
                VisualProfile.builder(0xB266FF).impactParticles(30).build());

        // ---------- NATURE (4) ----------
        register(new AreaHealSpell(id("natures_blessing"), SpellSchool.NATURE, 30, 200, 2, 8.0f, 6.0),
                VisualProfile.builder(0x55C45E).impactParticles(60).ringShockwave(true).build());

        register(new SimpleDamageSpell(id("thorn_whip"), SpellSchool.NATURE, 14, 35, 1,
                        5.0f, 0f, () -> new MobEffectInstance(MobEffects.POISON, 60, 0)),
                VisualProfile.builder(0x3F9B46).impactParticles(25).beamEffect(true).build());

        register(new SimpleDamageSpell(id("entangle"), SpellSchool.NATURE, 18, 90, 2,
                        2.0f, 3.5f, () -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 4)),
                VisualProfile.builder(0x2E7D32).impactParticles(50).groundCrater(false).build());

        register(new SelfBuffSpell(id("wild_growth"), SpellSchool.NATURE, 22, 260, 2,
                        List.of(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1),
                                () -> new MobEffectInstance(MobEffects.JUMP, 300, 1)), 0),
                VisualProfile.builder(0x6FDC6F).impactParticles(35).build());

        // ---------- SHADOW (4) ----------
        register(new SimpleDamageSpell(id("shadow_bolt"), SpellSchool.SHADOW, 14, 35, 1,
                        6.0f, 0f, () -> new MobEffectInstance(MobEffects.DARKNESS, 100, 0)),
                VisualProfile.builder(0x5A2A7A).impactParticles(30).beamEffect(true).build());

        register(new SimpleDamageSpell(id("void_rupture"), SpellSchool.SHADOW, 36, 190, 3,
                        10.0f, 4.0f, () -> new MobEffectInstance(MobEffects.WEAKNESS, 160, 1)),
                VisualProfile.builder(0x3A1A52).impactParticles(80).ringShockwave(true)
                        .fullscreenShader(14).build());

        register(new BlinkSpell(id("shadow_step"), SpellSchool.SHADOW, 14, 50, 1, 8.0),
                VisualProfile.builder(0x6A3A9A).impactParticles(35).castTrail(true).build());

        register(new SelfBuffSpell(id("cloak_of_shadows"), SpellSchool.SHADOW, 24, 280, 2,
                        List.of(() -> new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0)), 0),
                VisualProfile.builder(0x4A2A6A).impactParticles(30).build());

        // ---------- LIGHT (4) ----------
        register(new AreaHealSpell(id("radiant_light"), SpellSchool.LIGHT, 32, 220, 2, 10.0f, 7.0),
                VisualProfile.builder(0xFFE680).impactParticles(70).ringShockwave(true)
                        .fullscreenShader(10).build());

        register(new SimpleDamageSpell(id("holy_smite"), SpellSchool.LIGHT, 22, 80, 2,
                        8.0f, 0f, null),
                VisualProfile.builder(0xFFF0B3).impactParticles(45).beamEffect(true).build());

        register(new SelfBuffSpell(id("blessing"), SpellSchool.LIGHT, 18, 240, 1,
                        List.of(() -> new MobEffectInstance(MobEffects.REGENERATION, 200, 1),
                                () -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0)), 0),
                VisualProfile.builder(0xFFE680).impactParticles(30).build());

        register(new SimpleDamageSpell(id("purify"), SpellSchool.LIGHT, 16, 60, 1,
                        3.0f, 2.5f, () -> new MobEffectInstance(MobEffects.WEAKNESS, 100, 0)),
                VisualProfile.builder(0xFFF8D9).impactParticles(40).ringShockwave(true).build());

        // ---------- STORM (4) ----------
        register(new SimpleDamageSpell(id("chain_lightning"), SpellSchool.STORM, 26, 100, 2,
                        7.0f, 3.0f, null),
                VisualProfile.builder(0x4FA8FF).impactParticles(55).beamEffect(true)
                        .fullscreenShader(8).build());

        register(new SimpleDamageSpell(id("thunder_strike"), SpellSchool.STORM, 34, 170, 3,
                        11.0f, 2.5f, null),
                VisualProfile.builder(0x87CEFF).impactParticles(75).ringShockwave(true)
                        .fullscreenShader(12).build());

        register(new SummonProjectileSpell(id("storm_orb"), SpellSchool.STORM, 20, 70, 1, 1.4f, 2.0f),
                VisualProfile.builder(0x6FC3FF).impactParticles(40).castTrail(true).build());

        register(new SelfBuffSpell(id("static_field"), SpellSchool.STORM, 20, 220, 2,
                        List.of(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 2)), 0),
                VisualProfile.builder(0x4FA8FF).impactParticles(30).build());

        // ---------- BLOOD (2) ----------
        register(new SimpleDamageSpell(id("blood_lance"), SpellSchool.BLOOD, 16, 45, 1,
                        7.0f, 0f, () -> new MobEffectInstance(MobEffects.WITHER, 60, 0)),
                VisualProfile.builder(0xB3001F).impactParticles(35).beamEffect(true).build());

        register(new SelfBuffSpell(id("blood_pact"), SpellSchool.BLOOD, 10, 260, 1,
                        List.of(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 1)), -4.0f),
                VisualProfile.builder(0x8B0000).impactParticles(25).build());

        // ---------- MAGIAS ESPECIAIS (7) ----------

        // Supernova: explosao gigante multi-camada, a mais destrutiva do mod.
        register(new SimpleDamageSpell(id("supernova"), SpellSchool.FIRE, 55, 300, 3,
                        16.0f, 5.5f, () -> new MobEffectInstance(MobEffects.WEAKNESS, 100, 1)),
                VisualProfile.builder(0xFFB300)
                        .impactParticles(140).ringShockwave(true).groundCrater(true)
                        .fullscreenShader(22).particleScale(1.8f).sound("entity.generic.explode")
                        .build());

        // Prisao Glacial: imobiliza e da dano continuo via lentidao extrema.
        register(new SimpleDamageSpell(id("glacial_prison"), SpellSchool.FROST, 24, 120, 2,
                        4.0f, 1.5f, () -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 6)),
                VisualProfile.builder(0xBFF2FF)
                        .impactParticles(55).ringShockwave(true).particleScale(1.1f)
                        .build());

        // Chuva de Estrelas: varias trilhas caindo do ceu em area, com explosao final.
        register(new SimpleDamageSpell(id("starfall"), SpellSchool.LIGHT, 42, 240, 3,
                        9.0f, 4.0f, null),
                VisualProfile.builder(0xFFF3B0)
                        .impactParticles(90).ringShockwave(true).starfallEffect(true)
                        .fullscreenShader(14).particleScale(1.2f)
                        .build());

        // Colheita de Almas: drena vida do alvo e cura o lancador.
        register(new SimpleDamageSpell(id("soul_harvest"), SpellSchool.SHADOW, 26, 130, 2,
                        8.0f, 0f, () -> new MobEffectInstance(MobEffects.WITHER, 80, 1)),
                VisualProfile.builder(0x7A3AAE)
                        .impactParticles(60).beamEffect(true).castTrail(true).particleScale(1.1f)
                        .build());

        // Chamado da Tempestade: multiplos raios em area, visual eletrico intenso.
        register(new SimpleDamageSpell(id("tempest_call"), SpellSchool.STORM, 48, 260, 3,
                        13.0f, 5.0f, null),
                VisualProfile.builder(0x9FE0FF)
                        .impactParticles(120).ringShockwave(true).beamEffect(true)
                        .fullscreenShader(16).particleScale(1.3f)
                        .build());

        // Prisao de Vinhas: controle de area com dano continuo (veneno).
        register(new SimpleDamageSpell(id("vine_prison"), SpellSchool.NATURE, 20, 110, 2,
                        3.0f, 3.0f, () -> new MobEffectInstance(MobEffects.POISON, 160, 1)),
                VisualProfile.builder(0x3F7D3A)
                        .impactParticles(65).ringShockwave(true).particleScale(1.0f)
                        .build());

        // Singularidade: cria um ponto de gravidade que puxa entidades
        // proximas; no cliente, o motor de efeitos desenha o buraco negro
        // com lente gravitacional (ver shaders/vfx/volume/blackhole.fsh).
        register(new GravityWellSpell(id("singularity"), SpellSchool.ARCANE, 60, 400, 3,
                        6.0, 0.9, 10.0f),
                VisualProfile.builder(0x6A1FB3)
                        .impactParticles(100).ringShockwave(true)
                        .singularityShader(40).particleScale(1.5f)
                        .build());

        MagicMod.LOGGER.info("ModSpells: {} magias registradas", SPELLS.size());
    }

    private static void register(Spell spell, VisualProfile profile) {
        SPELLS.put(spell.getId(), spell);
        VisualProfiles.register(spell.getId().getPath(), profile);
    }

    public static Spell get(ResourceLocation id) {
        return SPELLS.get(id);
    }

    public static List<Spell> all() {
        return List.copyOf(SPELLS.values());
    }
}
