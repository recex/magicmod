package com.arcanemod.magicmod.client.vfx;

import com.arcanemod.magicmod.client.VisualProfile;
import com.arcanemod.magicmod.engine.cinematic.CameraShot;
import com.arcanemod.magicmod.engine.core.ArcaneEngine;
import com.arcanemod.magicmod.engine.math.Curve;
import com.arcanemod.magicmod.engine.math.Easing;
import com.arcanemod.magicmod.engine.math.MathUtil;
import com.arcanemod.magicmod.engine.particle.Burst;
import com.arcanemod.magicmod.engine.vfx.Fx;
import com.arcanemod.magicmod.engine.vfx.Paths;
import com.arcanemod.magicmod.engine.vfx.ProxyElement;
import com.arcanemod.magicmod.engine.vfx.StripElement;
import com.arcanemod.magicmod.network.SpellVfxPacket;
import com.arcanemod.magicmod.registry.ModSpells;
import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellSchool;
import com.arcanemod.magicmod.spell.impl.AreaHealSpell;
import com.arcanemod.magicmod.spell.impl.BlinkSpell;
import com.arcanemod.magicmod.spell.impl.SelfBuffSpell;
import com.arcanemod.magicmod.spell.impl.SummonProjectileSpell;
import com.arcanemod.magicmod.client.config.VfxConfig;
import com.arcanemod.magicmod.spell.impl.GravityWellSpell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Random;

/**
 * Biblioteca de efeitos das magias: traduz cada magia (escola, tipo, nivel e
 * o {@link VisualProfile} dela) numa sequencia do motor - volumes, fitas,
 * particulas, luzes, pos-processamento, som e camera, todos na mesma linha do
 * tempo. As sete magias supremas tem sequencias proprias.
 */
public final class SpellEffects {

    private static final Curve FADE = Curve.ease(1.0f, 0.0f, Easing.OUT_QUAD);
    private static final Curve FADE_SLOW = Curve.line(1.0f, 0.0f);
    private static final Curve HOLD_FADE = Curve.of(0.0f, 1.0f, 0.7f, 1.0f, 1.0f, 0.0f);
    private static final Curve SWELL = Curve.pulse(0.12f, 0.35f);
    private static final Curve DRAW_IN = Curve.of(0.0f, 0.0f, 0.3f, 1.0f, 1.0f, 1.0f);
    private static final List<String> DEMOS = List.of("fire", "frost", "arcane", "nature", "shadow", "light", "storm", "blood",
            "aura", "heal", "blink", "orb", "beam", "supernova", "glacial_prison", "starfall", "soul_harvest", "tempest_call",
            "vine_prison", "singularity");

    /** Magias com sequencia propria (o nome do efeito e o proprio id da magia). */
    private static final List<String> SET_PIECES = List.of("supernova", "glacial_prison", "starfall", "soul_harvest", "tempest_call",
            "vine_prison", "singularity");

    private SpellEffects() {
    }

    public static List<String> demoNames() {
        return DEMOS;
    }

    /** Efeito completo de uma magia recebida do servidor. */
    public static void play(ArcaneEngine engine, SpellVfxPacket msg, VisualProfile profile) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !Double.isFinite(msg.x) || !Double.isFinite(msg.y) || !Double.isFinite(msg.z)) {
            return;
        }
        Vec3 view = mc.gameRenderer.getMainCamera().getPosition();
        double limit = VfxConfig.maxDistance + 48.0;
        if (view.distanceToSqr(msg.x, msg.y, msg.z) > limit * limit) {
            return;
        }
        Spell spell = ModSpells.get(msg.spellId);
        Entity caster = level.getEntity(msg.casterEntityId);
        SpellSchool school = spell != null ? spell.getSchool() : SpellSchool.ARCANE;
        int tier = spell != null ? spell.getTier() : 1;
        float power = tier >= 3 ? 1.6f : tier == 2 ? 1.2f : 0.85f;
        String kind;
        if (spell instanceof SelfBuffSpell) {
            kind = "aura";
        } else if (spell instanceof AreaHealSpell) {
            kind = "heal";
        } else if (spell instanceof BlinkSpell) {
            kind = "blink";
        } else if (spell instanceof SummonProjectileSpell) {
            kind = "orb";
        } else {
            kind = school.name().toLowerCase(Locale.ROOT);
        }
        String path = msg.spellId.getPath();
        if (SET_PIECES.contains(path)) {
            kind = path;
        }
        double x = msg.x;
        double y = msg.y;
        double z = msg.z;
        if (caster != null && kind.equals("heal")) {
            // A cura age em volta de quem lanca, nao no ponto para onde ele olha.
            x = caster.getX();
            y = caster.getY();
            z = caster.getZ();
        } else if (!kind.equals("aura") && !kind.equals("blink") && !kind.equals("orb")) {
            if (caster != null && caster.getEyePosition().distanceToSqr(x, y, z) < 0.36) {
                // Sem alvo, o servidor manda a posicao dos olhos de quem lancou, mas a magia
                // age alguns blocos a frente: o efeito vai para onde a magia realmente acontece.
                double reach = spell instanceof GravityWellSpell ? 10.0 : 8.0;
                Vec3 eye = caster.getEyePosition();
                Vec3 end = eye.add(caster.getLookAngle().scale(reach));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                Vec3 p = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
                x = p.x;
                y = p.y;
                z = p.z;
            }
            // Ponto no ar com chao logo abaixo: desce ate o chao (marcas e ondas precisam de superficie).
            Vec3 from = new Vec3(x, y + 0.05, z);
            BlockHitResult ground = level.clip(new ClipContext(from, from.add(0.0, -5.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (ground.getType() == HitResult.Type.BLOCK && ground.getLocation().y < y - 0.3) {
                y = ground.getLocation().y;
            }
        }
        run(engine, kind, x, y, z, caster, profile.r, profile.g, profile.b, power, profile, tier);
        SoundEvent custom = profile.soundEvent == null ? null : soundByName(profile.soundEvent);
        if (custom != null) {
            engine.audio.play(custom, x, y, z, 1.0f, 1.0f);
        }
    }

    /** Toca um efeito pelo nome (comando de demonstracao e autoteste). Devolve falso se o nome nao existe. */
    public static boolean playDemo(ArcaneEngine engine, String name, double x, double y, double z, Entity caster) {
        if (!DEMOS.contains(name)) {
            return false;
        }
        float[] c = demoColor(name);
        float power = isUltimate(name) ? 1.6f : 1.2f;
        run(engine, name, x, y, z, caster, c[0], c[1], c[2], power, null, isUltimate(name) ? 3 : 2);
        return true;
    }

    private static boolean isUltimate(String name) {
        return name.equals("supernova") || name.equals("starfall") || name.equals("tempest_call") || name.equals("singularity");
    }

    private static float[] demoColor(String name) {
        switch (name) {
            case "fire": case "supernova": return new float[] {1.0f, 0.42f, 0.10f};
            case "frost": case "glacial_prison": return new float[] {0.55f, 0.85f, 1.0f};
            case "nature": case "vine_prison": case "heal": return new float[] {0.35f, 0.85f, 0.35f};
            case "shadow": return new float[] {0.55f, 0.2f, 0.85f};
            case "soul_harvest": return new float[] {0.4f, 1.0f, 0.75f};
            case "light": case "starfall": return new float[] {1.0f, 0.85f, 0.45f};
            case "storm": case "tempest_call": case "orb": return new float[] {0.45f, 0.65f, 1.0f};
            case "blood": return new float[] {0.9f, 0.08f, 0.12f};
            case "singularity": return new float[] {1.0f, 0.55f, 0.2f};
            default: return new float[] {0.7f, 0.4f, 1.0f};
        }
    }

    private static SoundEvent soundByName(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        return id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
    }

    private static void run(ArcaneEngine engine, String kind, double x, double y, double z, Entity caster,
                            float r, float g, float b, float power, VisualProfile profile, int tier) {
        Minecraft mc = Minecraft.getInstance();
        boolean own = caster != null && caster == mc.player;
        Fx fx = engine.fx(x, y, z);
        float delay = 0.0f;
        boolean damage = false;
        switch (kind) {
            case "aura":
                aura(engine, caster, x, y, z, r, g, b, power);
                return;
            case "heal":
                heal(fx, r, g, b, power);
                return;
            case "blink":
                blink(engine, fx, caster, r, g, b);
                return;
            case "orb":
                launch(engine, fx, caster, r, g, b);
                return;
            case "beam":
                delay = castLink(engine, caster, x, y, z, r, g, b, true);
                impact(fx.at(delay), "arcane", r, g, b, power);
                return;
            case "supernova":
                supernova(engine, fx, r, g, b, own);
                break;
            case "glacial_prison":
                glacialPrison(fx, r, g, b);
                break;
            case "starfall":
                starfall(engine, fx, r, g, b, own);
                break;
            case "soul_harvest":
                soulHarvest(engine, fx, caster, r, g, b);
                break;
            case "tempest_call":
                tempest(engine, fx, r, g, b, own);
                break;
            case "vine_prison":
                vinePrison(fx, r, g, b);
                break;
            case "singularity":
                singularity(engine, fx, r, g, b, own, profile == null ? 100 : profile.shaderDurationTicks);
                break;
            default:
                damage = true;
                break;
        }
        if (caster != null) {
            castFlourish(engine, caster, r, g, b);
        }
        if (!damage) {
            return;
        }
        boolean beam = profile != null && profile.beamEffect;
        boolean link = beam || tier <= 1 || (profile != null && profile.castTrail);
        if (link && caster != null) {
            delay = castLink(engine, caster, x, y, z, r, g, b, beam);
        }
        fx.at(delay);
        impact(fx, kind, r, g, b, power);
        if (profile != null) {
            if (profile.ringShockwave) {
                ring(fx.at(delay + 0.08f), r, g, b, 4.5f * power, 0.6f, 0.13f, 1.0f);
            }
            if (profile.groundCrater) {
                fx.at(delay).volume("decal", 8.0f).radius(1.7f * power).layer(0).colorA(r, g, b)
                        .params0(3.0f, 0.0f, 1.0f, 1.5f).params1(1.0f, 0.0f, 0.0f, 0.0f).intensity(HOLD_FADE);
            }
            if (profile.starfallEffect) {
                meteors(fx, delay + 0.1f, 3, 3.0f, r, g, b, 0.7f);
            }
            if (profile.fullscreenShader) {
                float seconds = MathUtil.clamp(profile.shaderDurationTicks / 20.0f, 0.4f, 6.0f);
                fx.at(delay).post(seconds).reach(20.0f * power).chroma(Curve.pulse(0.1f, 0.6f).scaled(0.55f))
                        .tint(r, g, b, Curve.pulse(0.1f, 0.6f).scaled(0.22f)).vignette(Curve.pulse(0.1f, 0.6f).scaled(0.3f));
            }
        }
    }

    // ------------------------------------------------------------------ pecas comuns

    /** Anel de choque rente ao chao, com distorcao. */
    private static void ring(Fx fx, float r, float g, float b, float radius, float life, float thickness, float distort) {
        fx.volume("shockwave", life).radius(Curve.ease(radius * 0.12f, radius, Easing.OUT_CUBIC)).layer(0)
                .colorA(r, g, b).colorB(1.0f, 1.0f, 1.0f, 1.0f).params0(0.12f, thickness, 1.2f, distort).distort().offset(0.0, 0.25, 0.0);
    }

    /** Onda de choque esferica, com distorcao. */
    private static void dome(Fx fx, float r, float g, float b, float radius, float life, float distort) {
        fx.volume("shockwave", life).radius(Curve.ease(radius * 0.1f, radius, Easing.OUT_CUBIC))
                .colorA(r, g, b).colorB(1.0f, 1.0f, 1.0f, 1.0f).params0(1.0f, 0.08f, 1.0f, distort).distort();
    }

    private static Burst sparks(int count, float r, float g, float b, float speed) {
        return Burst.of(count).style(Burst.SPARK).speed(speed * 0.3f, speed).life(0.45f, 1.2f).size(0.035f, 0.08f, 0.2f)
                .physics(2.2f, 9.0f).color(r * 5.0f + 1.0f, g * 5.0f + 0.6f, b * 5.0f + 0.4f, 0.0f).colorEnd(r * 2.0f, g * 0.8f, b * 0.8f, 0.0f)
                .stretch(0.09f).upBias(0.5f).spawnSphere(0.4f).colorJitter(0.5f);
    }

    private static Burst smoke(int count, float radius, float shade, float opacity) {
        return Burst.of(count).style(Burst.SMOKE).speed(0.8f, 3.5f).life(1.2f, 2.4f).size(0.6f, 1.2f, 2.2f).physics(1.5f, -1.0f)
                .color(shade, shade * 0.9f, shade * 0.9f, opacity).colorEnd(shade * 0.5f, shade * 0.5f, shade * 0.5f, 0.0f)
                .spawnSphere(radius).fade(0.15f, 0.4f).turbulence(0.2f);
    }

    private static Burst motes(int count, float r, float g, float b, int style) {
        return Burst.of(count).style(style).speed(0.4f, 2.2f).life(1.0f, 2.4f).size(0.05f, 0.11f, 0.4f).physics(1.2f, -0.7f)
                .color(r * 3.0f + 0.3f, g * 3.0f + 0.3f, b * 3.0f + 0.3f, 0.0f).colorEnd(r, g, b, 0.0f).turbulence(0.45f)
                .fade(0.15f, 0.5f).flicker(0.6f).spawnSphere(0.8f);
    }

    /** Circulo runico no chao que se desenha e gira. */
    private static ProxyElement rune(Fx fx, float r, float g, float b, float radius, float life) {
        return fx.volume("decal", life).radius(radius).layer(0).colorA(r, g, b).intensity(SWELL)
                .params0(0.0f, 0.0f, 1.0f, 1.5f).animate((e, t) -> {
                    e.params0[1] = e.age * 0.6f;
                    e.params1[0] = DRAW_IN.sample(t);
                });
    }

    /** Gesto de conjuracao no lancador: runa aos pes, brilho e fagulhas na mao. */
    private static void castFlourish(ArcaneEngine engine, Entity caster, float r, float g, float b) {
        Fx fx = engine.fx(caster.getX(), caster.getY() + 0.05, caster.getZ());
        rune(fx, r, g, b, 1.3f, 0.9f);
        fx.light(r * 2.0f, g * 2.0f, b * 2.0f, 5.0f, 0.5f).intensity(FADE).offset(0.0, 1.2, 0.0);
        fx.burst(motes(10, r, g, b, Burst.STAR), 0.0, 1.0, 0.0);
    }

    /**
     * Ligacao visual do lancador ate o alvo: um projetil de energia com rastro
     * (ou um feixe continuo). Devolve o atraso, em segundos, ate o impacto.
     */
    private static float castLink(ArcaneEngine engine, Entity caster, double x, double y, double z, float r, float g, float b, boolean beam) {
        if (caster == null) {
            return 0.0f;
        }
        double sx = caster.getX();
        double sy = caster.getEyeY() - 0.25;
        double sz = caster.getZ();
        float dx = (float) (x - sx);
        float dy = (float) (y - sy);
        float dz = (float) (z - sz);
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.5f || dist > 96.0f) {
            return 0.0f;
        }
        Fx fx = engine.fx(sx, sy, sz);
        if (beam) {
            fx.strip(StripElement.BEAM, Paths.line(dx, dy, dz, 12), 0.55f).width(0.55f).color(r, g, b, 0.0f)
                    .intensity(Curve.pulse(0.12f, 0.5f)).cullRadius(dist + 2.0f);
            fx.light(r * 2.5f, g * 2.5f, b * 2.5f, 6.0f, 0.5f).intensity(SWELL);
            return 0.05f;
        }
        float travel = MathUtil.clamp(dist / 55.0f, 0.08f, 0.45f);
        fx.strip(StripElement.TRAIL, Paths.arcSweep(dx, dy, dz, 0.0f, dist * 0.04f, 0.0f, 0.45f, 16), travel * 1.45f)
                .width(0.4f).widthAlong(Curve.line(0.2f, 1.0f)).color(r, g, b, 0.0f).cullRadius(dist + 2.0f);
        fx.burst(sparks(8, r, g, b, 4.0f));
        return travel;
    }

    // ------------------------------------------------------------------ impactos por escola

    private static void impact(Fx fx, String school, float r, float g, float b, float p) {
        float t0 = fx.cursor();
        float reach = 22.0f * p;
        switch (school) {
            case "fire": {
                float radius = 2.1f * p;
                fx.volume("fireball", 1.5f).radius(Curve.ease(radius * 0.3f, radius, Easing.OUT_CUBIC)).colorA(r, g, b)
                        .colorB(0.05f, 0.04f, 0.04f, 0.85f).params0(1.0f, 0.6f, 1.0f, 1.0f).rise(0.8f).offset(0.0, radius * 0.4, 0.0);
                ring(fx, r, g, b, radius * 2.3f, 0.55f, 0.14f, 1.0f);
                fx.volume("decal", 9.0f).radius(radius * 0.95f).layer(0).colorA(r, g, b).params0(1.0f, 0.0f, 1.0f, 1.5f);
                fx.light(r * 5.0f, g * 5.0f, b * 5.0f, radius * 5.0f, 0.9f).intensity(FADE).offset(0.0, 1.0, 0.0);
                fx.burst(sparks(Math.round(80 * p), r, g, b, 16.0f), 0.0, 0.5, 0.0);
                fx.burst(smoke(Math.round(12 * p), radius * 0.4f, 0.07f, 0.5f), 0.0, radius * 0.4, 0.0);
                fx.at(t0 + 0.25f).burst(motes(Math.round(18 * p), r, g, b, Burst.GLOW), 0.0, 0.6, 0.0);
                fx.at(t0).post(0.6f).reach(reach).flash(1.0f, 0.75f, 0.5f, FADE.scaled(0.22f * p)).shake(FADE.scaled(0.45f * p));
                fx.sound(SoundEvents.GENERIC_EXPLODE, 1.6f * p, 0.95f).sound(SoundEvents.BLAZE_SHOOT, 0.8f, 0.7f)
                        .sound(SoundEvents.FIRECHARGE_USE, 0.9f, 0.8f);
                break;
            }
            case "frost": {
                float radius = 1.7f * p;
                fx.volume("crystal", 2.6f).radius(radius).colorA(0.55f + r * 0.3f, 0.8f, 1.0f).colorB(r * 0.5f, g * 0.6f, b, 1.0f)
                        .params0(0.0f, 7.0f, 1.0f, 0.9f).intensity(Curve.of(0.0f, 1.0f, 0.85f, 1.0f, 1.0f, 0.0f))
                        .animate((e, t) -> e.params0[0] = MathUtil.saturate(t / 0.12f));
                fx.volume("decal", 6.0f).radius(radius * 1.5f).layer(0).colorA(0.7f, 0.9f, 1.0f).params0(2.0f, 0.0f, 1.0f, 1.5f)
                        .animate((e, t) -> e.params1[0] = MathUtil.saturate(t / 0.06f));
                ring(fx, 0.6f, 0.85f, 1.0f, radius * 2.6f, 0.6f, 0.12f, 0.8f);
                fx.light(r * 3.0f, g * 3.0f, b * 3.5f, radius * 4.5f, 1.4f).intensity(FADE_SLOW).offset(0.0, 1.0, 0.0);
                fx.burst(Burst.of(Math.round(34 * p)).style(Burst.SHARD).speed(2.5f, 9.0f).life(0.6f, 1.3f).size(0.06f, 0.14f, 0.6f)
                        .physics(1.0f, 11.0f).color(0.75f, 0.9f, 1.0f, 0.9f).colorEnd(0.6f, 0.8f, 1.0f, 0.0f).upBias(0.6f), 0.0, 0.4, 0.0);
                fx.burst(smoke(Math.round(8 * p), radius * 0.5f, 0.75f, 0.22f), 0.0, 0.4, 0.0);
                fx.burst(motes(Math.round(24 * p), 0.6f, 0.85f, 1.0f, Burst.STAR), 0.0, 0.8, 0.0);
                fx.post(0.5f).reach(reach).flash(0.7f, 0.9f, 1.0f, FADE.scaled(0.14f * p)).shake(FADE.scaled(0.3f * p));
                fx.at(t0 + 2.2f).burst(Burst.of(Math.round(26 * p)).style(Burst.SHARD).speed(1.5f, 6.0f).life(0.5f, 1.1f)
                        .size(0.05f, 0.12f, 0.5f).physics(1.0f, 11.0f).color(0.75f, 0.9f, 1.0f, 0.9f).colorEnd(0.6f, 0.8f, 1.0f, 0.0f)
                        .spawnSphere(radius * 0.5f), 0.0, 0.6, 0.0).sound(SoundEvents.GLASS_BREAK, 0.9f, 1.3f);
                fx.at(t0).sound(SoundEvents.GLASS_BREAK, 1.2f * p, 0.8f).sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.4f)
                        .sound(SoundEvents.POWDER_SNOW_BREAK, 1.0f, 0.7f);
                break;
            }
            case "nature": {
                int vines = Math.round(4 * p) + 1;
                for (int i = 0; i < vines; i++) {
                    float phase = i / (float) vines + fx.random.nextFloat() * 0.1f;
                    float r0 = (0.7f + fx.random.nextFloat() * 0.5f) * p;
                    fx.strip(StripElement.VINE, Paths.helix(r0, 0.15f, (1.8f + fx.random.nextFloat()) * p, 0.8f + fx.random.nextFloat() * 0.6f, phase, 36), 2.8f)
                            .width(0.3f).widthAlong(Curve.line(1.1f, 0.15f)).color(0.16f + r * 0.1f, 0.42f, 0.10f + b * 0.1f, 1.0f)
                            .shaderAge(Curve.of(0.0f, 0.0f, 0.3f, 1.0f, 1.0f, 1.0f)).intensity(Curve.of(0.0f, 1.0f, 0.8f, 1.0f, 1.0f, 0.0f));
                }
                rune(fx, r, g, b, 2.0f * p, 2.6f);
                fx.light(r * 2.5f, g * 2.5f, b * 2.5f, 6.0f * p, 1.6f).intensity(SWELL).offset(0.0, 1.0, 0.0);
                Burst leaves = Burst.of(Math.round(26 * p)).style(Burst.LEAF).speed(1.0f, 4.5f).life(1.2f, 2.4f).size(0.08f, 0.14f, 1.0f)
                        .physics(1.4f, 2.2f).color(0.2f, 0.55f, 0.15f, 1.0f).colorEnd(0.4f, 0.4f, 0.1f, 0.0f).turbulence(0.6f).upBias(0.7f);
                fx.burst(leaves, 0.0, 0.5, 0.0);
                fx.burst(motes(Math.round(22 * p), 0.7f, 1.0f, 0.4f, Burst.GLOW), 0.0, 0.8, 0.0);
                fx.post(0.4f).reach(reach).shake(FADE.scaled(0.2f * p));
                fx.sound(SoundEvents.GRASS_BREAK, 1.3f, 0.6f).sound(SoundEvents.BONE_MEAL_USE, 1.2f, 0.8f)
                        .sound(SoundEvents.AZALEA_LEAVES_BREAK, 1.0f, 0.7f);
                break;
            }
            case "shadow": {
                float radius = 1.2f * p;
                fx.volume("orb", 1.3f).radius(Curve.of(0.0f, 0.25f, 0.2f, radius, 0.8f, radius * 0.9f, 1.0f, 0.02f)).bound(1.8f)
                        .colorA(r, g, b).colorB(r * 0.35f, g * 0.2f, b * 0.45f, 1.0f).params0(3.0f, 0.6f, 1.0f, 0.6f)
                        .params1(1.0f, 0.0f, 1.0f, 0.0f).offset(0.0, radius, 0.0);
                fx.volume("decal", 3.2f).radius(2.3f * p).layer(0).colorA(r, g, b).colorB(r * 0.4f, 0.0f, b * 0.4f, 1.0f)
                        .params0(4.0f, 0.0f, 1.0f, 1.5f).animate((e, t) -> e.params1[0] = SWELL.sample(t));
                for (int i = 0; i < 5; i++) {
                    float a = fx.random.nextFloat() * MathUtil.TAU;
                    float d = 3.0f * p;
                    fx.at(t0 + i * 0.05f).strip(StripElement.WISP, Paths.arcSweep((float) -Math.cos(a) * d, radius - 0.3f, (float) -Math.sin(a) * d,
                                    0.0f, 1.0f, 0.0f, 0.6f, 20), 0.75f).width(0.7f).color(r, g, b, 0.5f)
                            .offset(Math.cos(a) * d, 0.3, Math.sin(a) * d);
                }
                fx.at(t0).burst(Burst.of(Math.round(60 * p)).style(Burst.GLOW).speed(0.0f, 0.5f).life(0.45f, 0.9f).size(0.05f, 0.1f, 0.3f)
                        .color(r * 3.0f, g * 2.0f, b * 4.0f, 0.0f).colorEnd(r, g, b, 0.0f).spawnShell(3.2f * p).attractTo(0.0, 0.0, 0.0, 1.6f)
                        .swirl(0.6f).stretch(0.05f).fade(0.2f, 0.8f), 0.0, radius, 0.0);
                fx.burst(smoke(Math.round(10 * p), radius, 0.035f, 0.6f), 0.0, radius, 0.0);
                fx.light(r * 1.6f, g * 1.2f, b * 2.2f, 7.0f * p, 1.2f).intensity(SWELL).offset(0.0, 1.0, 0.0);
                fx.post(1.2f).reach(reach * 0.8f).saturation(Curve.of(0.0f, 1.0f, 0.2f, 0.45f, 1.0f, 1.0f))
                        .vignette(Curve.pulse(0.2f, 0.6f).scaled(0.35f)).shake(FADE.scaled(0.25f * p));
                fx.sound(SoundEvents.WITHER_SHOOT, 0.7f, 0.6f).sound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.5f)
                        .sound(SoundEvents.SOUL_ESCAPE, 1.6f, 0.8f);
                break;
            }
            case "light": {
                float h = 11.0f * p;
                fx.volume("pillar", 1.1f).radius(0.75f * p).column(0.0f, 1.0f, 0.0f, h).colorA(r, g, b).colorB(r * 0.3f, g * 0.25f, b * 0.1f, 1.0f)
                        .params0(h, 3.0f, 0.5f, 1.0f).params1(0.0f, 0.22f, 0.0f, 0.0f).intensity(Curve.pulse(0.1f, 0.6f));
                rune(fx, r, g, b, 2.1f * p, 2.2f);
                ring(fx, r, g, b, 4.2f * p, 0.6f, 0.1f, 0.5f);
                fx.burst(motes(Math.round(40 * p), r, g, b, Burst.STAR), 0.0, 0.8, 0.0);
                fx.burst(sparks(Math.round(24 * p), r, g, b, 9.0f), 0.0, 0.4, 0.0);
                fx.light(r * 4.5f, g * 4.5f, b * 4.5f, 9.0f * p, 1.1f).intensity(FADE).offset(0.0, 1.5, 0.0);
                fx.post(0.6f).reach(reach).flash(1.0f, 0.95f, 0.8f, FADE.scaled(0.2f * p)).sceneBloom(FADE.scaled(0.6f)).shake(FADE.scaled(0.25f * p));
                fx.sound(SoundEvents.BEACON_ACTIVATE, 1.4f, 1.5f).sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.0f)
                        .sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
                break;
            }
            case "storm": {
                strike(fx, 0.0f, 0.0f, r, g, b, p, true);
                dome(fx, r, g, b, 3.2f * p, 0.45f, 1.2f);
                break;
            }
            case "blood": {
                float radius = 0.9f * p;
                fx.volume("orb", 0.55f).radius(Curve.of(0.0f, 0.2f, 0.3f, radius, 1.0f, radius * 1.4f)).bound(2.2f).colorA(r, g, b)
                        .colorB(r * 0.3f, 0.0f, 0.0f, 1.0f).params0(0.0f, 1.2f, 0.8f, 1.0f).params1(1.4f, 0.0f, 0.3f, 0.0f).intensity(FADE)
                        .offset(0.0, 0.9, 0.0);
                fx.volume("decal", 4.0f).radius(1.7f * p).layer(0).colorA(r, g * 0.5f, b * 0.5f).colorB(r * 0.5f, 0.0f, 0.0f, 1.0f)
                        .params0(4.0f, 0.0f, 0.7f, 1.5f).animate((e, t) -> e.params1[0] = SWELL.sample(t));
                fx.burst(Burst.of(Math.round(46 * p)).style(Burst.GLOW).speed(2.0f, 8.0f).life(0.5f, 1.1f).size(0.05f, 0.11f, 0.5f)
                        .physics(1.0f, 12.0f).color(r * 2.2f, g * 0.6f, b * 0.6f, 0.75f).colorEnd(r * 0.6f, 0.0f, 0.0f, 0.5f).upBias(0.6f)
                        .stretch(0.06f), 0.0, 0.8, 0.0);
                ring(fx, r, g * 0.4f, b * 0.4f, 3.0f * p, 0.5f, 0.12f, 0.6f);
                fx.light(r * 3.0f, g, b, 6.0f * p, 0.8f).intensity(FADE).offset(0.0, 1.0, 0.0);
                fx.post(0.7f).reach(reach * 0.7f).tint(1.0f, 0.55f, 0.55f, FADE.scaled(0.3f)).vignette(FADE.scaled(0.3f)).shake(FADE.scaled(0.3f * p));
                fx.sound(SoundEvents.HONEY_BLOCK_BREAK, 1.4f, 0.6f).sound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f)
                        .sound(SoundEvents.WITHER_HURT, 0.35f, 1.6f);
                break;
            }
            default: {
                float radius = 1.3f * p;
                fx.burst(Burst.of(Math.round(50 * p)).style(Burst.GLOW).speed(0.0f, 0.4f).life(0.2f, 0.32f).size(0.05f, 0.09f, 0.3f)
                        .color(r * 4.0f, g * 4.0f, b * 4.0f, 0.0f).spawnShell(radius * 2.4f).attractTo(0.0, 0.0, 0.0, 1.8f).swirl(0.4f)
                        .stretch(0.06f).fade(0.2f, 0.85f), 0.0, radius, 0.0);
                fx.volume("orb", 0.95f).radius(Curve.of(0.0f, 0.15f, 0.25f, radius, 0.75f, radius * 0.92f, 1.0f, 0.02f)).bound(2.4f)
                        .colorA(r, g, b).colorB(r * 0.15f, g * 0.05f, b * 0.3f, 1.0f).params0(0.0f, 0.8f, 1.0f, 1.2f)
                        .params1(1.0f, 0.0f, 0.0f, 0.0f).offset(0.0, radius, 0.0);
                rune(fx, r, g, b, 2.3f * p, 2.2f);
                ring(fx.at(t0 + 0.15f), r, g, b, 4.4f * p, 0.6f, 0.11f, 1.0f);
                fx.at(t0 + 0.2f).burst(motes(Math.round(40 * p), r, g, b, Burst.STAR).speed(2.0f, 7.0f).physics(2.2f, 0.0f).life(0.6f, 1.3f), 0.0, radius, 0.0);
                fx.at(t0).light(r * 3.5f, g * 3.0f, b * 4.0f, 8.0f * p, 1.0f).intensity(SWELL).offset(0.0, radius, 0.0);
                fx.post(0.7f).reach(reach).chroma(Curve.pulse(0.15f, 0.6f).scaled(0.55f * p)).flash(r, g, b, FADE.scaled(0.12f * p))
                        .shake(FADE.scaled(0.3f * p));
                fx.sound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 1.0f).sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 0.7f)
                        .sound(SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
                break;
            }
        }
    }

    /** Um raio caindo em (dx, dz) em relacao ao efeito: descarga, clarao, faiscas, rachadura e trovao. */
    private static void strike(Fx fx, float dx, float dz, float r, float g, float b, float p, boolean thunder) {
        float t0 = fx.cursor();
        Random rnd = fx.random;
        float top = 16.0f + rnd.nextFloat() * 6.0f;
        float lean = (rnd.nextFloat() - 0.5f) * 5.0f;
        Curve flicker = Curve.of(0.0f, 1.0f, 0.25f, 0.45f, 0.4f, 1.0f, 1.0f, 0.0f);
        fx.strip(StripElement.LIGHTNING, Paths.bolt(lean, top, lean * 0.6f, 18, 1.5f, 22.0f), 0.34f).width(0.5f * p)
                .color(r, g, b, 0.0f).intensity(flicker).cullRadius(top + 4.0f).offset(dx, 0.0, dz);
        for (int i = 0; i < 2; i++) {
            float h = 4.0f + rnd.nextFloat() * 6.0f;
            fx.strip(StripElement.LIGHTNING, Paths.bolt((rnd.nextFloat() - 0.5f) * 6.0f, -h * 0.6f, (rnd.nextFloat() - 0.5f) * 6.0f, 9, 0.8f, 22.0f), 0.26f)
                    .width(0.26f * p).color(r, g, b, 0.0f).intensity(flicker).offset(dx + lean * h / top, h, dz + lean * 0.6f * h / top);
        }
        fx.volume("decal", 5.0f).radius(1.9f * p).layer(0).colorA(r, g, b).params0(3.0f, 0.0f, 1.0f, 1.5f).offset(dx, 0.0, dz)
                .animate((e, t) -> e.params1[0] = MathUtil.saturate(t / 0.04f));
        fx.burst(sparks(Math.round(55 * p), r * 0.6f + 0.3f, g * 0.6f + 0.35f, b, 17.0f), dx, 0.3, dz);
        fx.light(r * 5.0f + 2.0f, g * 5.0f + 2.0f, b * 6.0f + 2.0f, 14.0f * p, 0.45f).intensity(flicker).offset(dx, 2.0, dz);
        fx.post(0.45f).reach(30.0f * p).flash(0.85f, 0.9f, 1.0f, flicker.scaled(0.26f * p)).shake(FADE.scaled(0.55f * p))
                .chroma(FADE.scaled(0.3f)).offset(dx, 0.0, dz);
        if (thunder) {
            fx.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.2f * p, 0.9f + rnd.nextFloat() * 0.3f)
                    .sound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.3f, 1.0f);
        }
        fx.at(t0);
    }

    // ------------------------------------------------------------------ tipos especiais

    private static void aura(ArcaneEngine engine, Entity caster, double x, double y, double z, float r, float g, float b, float p) {
        double cx = caster != null ? caster.getX() : x;
        double cy = caster != null ? caster.getY() : y;
        double cz = caster != null ? caster.getZ() : z;
        float half = caster != null ? caster.getBbHeight() * 0.5f : 0.9f;
        Fx fx = engine.fx(cx, cy, cz);
        ProxyElement shield = fx.volume("orb", 1.8f).radius(Curve.ease(0.4f, 1.35f, Easing.OUT_CUBIC)).bound(1.1f).colorA(r, g, b)
                .params0(2.0f, 0.15f, 1.0f, 0.0f).params1(3.0f, 0.0f, 1.0f, 0.0f).intensity(Curve.pulse(0.12f, 0.5f))
                .axis(0.0f, 1.0f, 0.0f).animate((e, t) -> e.params1[1] = MathUtil.saturate(t * 1.6f));
        ProxyElement glow = fx.volume("pillar", 1.4f).radius(1.0f).column(0.0f, 1.0f, 0.0f, 3.5f).colorA(r, g, b)
                .params0(3.5f, 2.5f, 1.2f, 0.45f).params1(0.0f, 0.5f, 0.0f, 0.0f).intensity(Curve.pulse(0.15f, 0.5f));
        if (caster != null) {
            shield.follow(caster, 0.0, half, 0.0);
            glow.follow(caster, 0.0, 0.0, 0.0);
            fx.emitter(motes(1, r, g, b, Burst.STAR).physics(1.0f, -1.6f).spawnRing(0.8f), Curve.line(60.0f, 0.0f), 1.2f)
                    .follow(caster, 0.0, 0.2, 0.0);
            fx.light(r * 2.5f, g * 2.5f, b * 2.5f, 6.0f, 1.6f).intensity(SWELL).follow(caster, 0.0, half, 0.0);
        } else {
            shield.offset(0.0, half, 0.0);
        }
        rune(fx, r, g, b, 1.7f, 1.8f);
        ring(fx, r, g, b, 2.6f, 0.5f, 0.12f, 0.4f);
        fx.sound(SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.3f).sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.9f)
                .sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.1f);
    }

    private static void heal(Fx fx, float r, float g, float b, float p) {
        float radius = 4.5f;
        rune(fx, r, g, b, radius, 2.8f);
        fx.volume("pillar", 2.0f).radius(2.0f).column(0.0f, 1.0f, 0.0f, 6.0f).colorA(r, g, b).colorB(r * 0.2f, g * 0.2f, b * 0.1f, 1.0f)
                .params0(6.0f, 1.6f, 0.8f, 0.4f).params1(0.0f, 0.6f, 0.0f, 0.0f).intensity(Curve.pulse(0.2f, 0.5f));
        fx.emitter(motes(1, r, g, b, Burst.STAR).physics(0.8f, -1.8f).spawnRing(radius * 0.6f), Curve.line(90.0f, 10.0f), 1.8f);
        fx.burst(Burst.of(20).style(Burst.LEAF).speed(0.5f, 2.5f).life(1.4f, 2.6f).size(0.08f, 0.13f, 1.0f).physics(1.4f, -0.6f)
                .color(0.3f, 0.7f, 0.25f, 1.0f).colorEnd(0.5f, 0.7f, 0.2f, 0.0f).turbulence(0.7f).spawnRing(radius * 0.5f), 0.0, 0.3, 0.0);
        ring(fx, r, g, b, radius, 0.9f, 0.1f, 0.3f);
        fx.light(r * 2.5f, g * 2.5f, b * 2.5f, radius * 2.2f, 2.2f).intensity(SWELL).offset(0.0, 1.5, 0.0);
        fx.post(1.4f).reach(16.0f).sceneBloom(Curve.pulse(0.2f, 0.5f).scaled(0.5f));
        fx.sound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 0.5f, 1.6f).sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.2f)
                .sound(SoundEvents.BEACON_ACTIVATE, 1.0f, 1.7f);
    }

    private static void blink(ArcaneEngine engine, Fx fx, Entity caster, float r, float g, float b) {
        blinkFlash(fx, r, g, b);
        if (caster != null && caster.distanceToSqr(fx.x, fx.y, fx.z) > 4.0) {
            Fx other = engine.fx(caster.getX(), caster.getY(), caster.getZ());
            blinkFlash(other, r, g, b);
            float dx = (float) (fx.x - other.x);
            float dy = (float) (fx.y - other.y);
            float dz = (float) (fx.z - other.z);
            other.strip(StripElement.WISP, Paths.line(dx, dy, dz, 14), 0.5f).width(0.9f).color(r, g, b, 0.3f).intensity(FADE)
                    .offset(0.0, 0.9, 0.0).cullRadius(40.0f);
        }
        fx.sound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.3f).sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
    }

    private static void blinkFlash(Fx fx, float r, float g, float b) {
        fx.volume("orb", 0.4f).radius(Curve.of(0.0f, 1.1f, 1.0f, 0.05f)).bound(2.4f).colorA(r, g, b).params0(0.0f, 2.0f, 1.2f, 1.2f)
                .params1(1.2f, 0.0f, 0.0f, 0.0f).offset(0.0, 0.9, 0.0);
        ring(fx, r, g, b, 2.4f, 0.4f, 0.14f, 1.2f);
        fx.burst(motes(22, r, g, b, Burst.STAR).speed(1.5f, 5.0f).life(0.4f, 0.9f), 0.0, 0.9, 0.0);
        fx.light(r * 3.0f, g * 3.0f, b * 3.0f, 6.0f, 0.4f).intensity(FADE).offset(0.0, 0.9, 0.0);
        fx.post(0.35f).reach(10.0f).chroma(FADE.scaled(0.5f));
    }

    /**
     * Disparo de projetil: clarao e descarga na mao de quem lanca e, em
     * seguida, um orbe de energia preso ao projetil enquanto ele voa.
     */
    private static void launch(ArcaneEngine engine, Fx at, Entity caster, float r, float g, float b) {
        if (caster == null) {
            impact(at, "arcane", r, g, b, 0.85f);
            return;
        }
        Vec3 look = caster.getLookAngle();
        Vec3 hand = caster.getEyePosition().add(look.scale(0.9)).add(0.0, -0.2, 0.0);
        Fx fx = engine.fx(hand.x, hand.y, hand.z);
        fx.volume("orb", 0.35f).radius(Curve.of(0.0f, 0.15f, 0.3f, 0.55f, 1.0f, 0.1f)).bound(2.5f).colorA(r, g, b)
                .params0(0.0f, 1.6f, 1.2f, 1.3f).params1(1.3f, 0.0f, 0.0f, 0.0f);
        fx.volume("shockwave", 0.35f).radius(Curve.ease(0.2f, 1.6f, Easing.OUT_CUBIC)).colorA(r, g, b).params0(1.0f, 0.1f, 1.0f, 0.8f).distort();
        for (int i = 0; i < 3; i++) {
            Random rnd = fx.random;
            float len = 2.0f + rnd.nextFloat() * 2.0f;
            fx.strip(StripElement.LIGHTNING, Paths.bolt((float) look.x * len + (rnd.nextFloat() - 0.5f), (float) look.y * len + (rnd.nextFloat() - 0.5f),
                    (float) look.z * len + (rnd.nextFloat() - 0.5f), 8, 0.35f, 24.0f), 0.18f).width(0.2f).color(r, g, b, 0.0f);
        }
        fx.burst(sparks(26, r, g, b, 9.0f).cone((float) look.x, (float) look.y, (float) look.z, 0.5f).physics(2.5f, 3.0f));
        fx.light(r * 3.5f, g * 3.5f, b * 4.0f, 7.0f, 0.4f).intensity(FADE);
        fx.post(0.3f).reach(8.0f).shake(FADE.scaled(0.2f));
        fx.sound(SoundEvents.FIRECHARGE_USE, 1.0f, 1.4f).sound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.7f)
                .sound(SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
        // O projetil nasce no servidor; alguns instantes depois ele ja existe aqui.
        fx.at(0.12f).run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            SmallFireball shot = null;
            for (SmallFireball e : level.getEntitiesOfClass(SmallFireball.class, new AABB(hand, hand).inflate(12.0))) {
                if (e.getOwner() == caster && e.tickCount < 12 && (shot == null || e.tickCount < shot.tickCount)) {
                    shot = e;
                }
            }
            if (shot == null) {
                return;
            }
            Fx tail = engine.fx(shot.getX(), shot.getY(), shot.getZ());
            tail.volume("orb", 6.0f).radius(0.42f).bound(2.5f).colorA(r, g, b).params0(0.0f, 1.8f, 1.2f, 1.3f).params1(1.4f, 0.0f, 0.0f, 0.0f)
                    .follow(shot, 0.0, 0.15, 0.0).dieWithEntity();
            tail.emitter(Burst.of(1).style(Burst.SPARK).speed(0.3f, 2.0f).life(0.25f, 0.6f).size(0.03f, 0.07f, 0.2f).physics(2.0f, 2.0f)
                            .color(r * 5.0f + 0.6f, g * 5.0f + 0.6f, b * 5.0f + 0.6f, 0.0f).stretch(0.06f), Curve.constant(70.0f), 6.0f)
                    .follow(shot, 0.0, 0.15, 0.0).dieWithEntity();
            tail.light(r * 2.5f, g * 2.5f, b * 3.0f, 6.0f, 6.0f).intensity(Curve.constant(1.0f)).follow(shot, 0.0, 0.15, 0.0).dieWithEntity();
        });
    }

    /** Chuva de {@code count} meteoros em volta do ponto, um a cada 0,26 s a partir de {@code start}. */
    private static void meteors(Fx fx, float start, int count, float spread, float r, float g, float b, float scale) {
        Random rnd = fx.random;
        for (int i = 0; i < count; i++) {
            float t = start + i * 0.26f;
            float a = rnd.nextFloat() * MathUtil.TAU;
            float d = (float) Math.sqrt(rnd.nextFloat()) * spread;
            float ox = (float) Math.cos(a) * d;
            float oz = (float) Math.sin(a) * d;
            float fall = 0.55f;
            float sx = 5.0f + rnd.nextFloat() * 4.0f;
            float sy = 20.0f + rnd.nextFloat() * 5.0f;
            float sz = (rnd.nextFloat() - 0.5f) * 6.0f;
            Paths.Motion motion = (time, out) -> {
                float k = 1.0f - MathUtil.saturate(time / fall);
                k *= k;
                out[0] = sx * k;
                out[1] = sy * k + 0.3f;
                out[2] = sz * k;
            };
            float[] pos = new float[3];
            fx.at(Math.max(0.0f, t - 0.25f)).volume("decal", fall + 0.25f).radius(1.5f * scale).layer(0).colorA(r, g, b)
                    .params0(5.0f, 0.0f, 0.9f, 1.5f).offset(ox, 0.0, oz);
            fx.at(t).volume("orb", fall).radius(0.5f * scale).bound(2.8f).colorA(r, g, b).params0(1.0f, 1.0f, 1.3f, 1.6f)
                    .params1(1.2f, 0.0f, 1.0f, 0.0f).offset(ox, 0.0, oz).animate((e, p) -> {
                        motion.at(e.age, pos);
                        e.offX = pos[0];
                        e.offY = pos[1];
                        e.offZ = pos[2];
                    });
            fx.strip(StripElement.TRAIL, Paths.trail(motion, 0.22f, 14), fall + 0.12f).width(0.9f * scale).widthAlong(Curve.line(0.1f, 1.0f))
                    .color(r, g, b, 0.0f).cullRadius(32.0f).offset(ox, 0.0, oz);
            fx.at(t + fall);
            fx.volume("fireball", 1.0f).radius(Curve.ease(0.5f, 1.9f * scale, Easing.OUT_CUBIC)).colorA(r, g, b).colorB(0.06f, 0.05f, 0.04f, 0.6f)
                    .params0(1.0f, 0.6f, 1.2f, 0.6f).rise(0.6f).offset(ox, 0.7, oz);
            fx.volume("pillar", 0.4f).radius(0.5f * scale).column(0.0f, 1.0f, 0.0f, 9.0f).colorA(r, g, b).params0(9.0f, 4.0f, 0.3f, 1.0f)
                    .params1(0.0f, 0.2f, 0.0f, 0.0f).intensity(FADE).offset(ox, 0.0, oz);
            fx.volume("shockwave", 0.5f).radius(Curve.ease(0.4f, 4.5f * scale, Easing.OUT_CUBIC)).layer(0).colorA(r, g, b)
                    .params0(0.12f, 0.13f, 1.2f, 0.9f).distort().offset(ox, 0.25, oz);
            fx.volume("decal", 8.0f).radius(1.6f * scale).layer(0).colorA(r, g, b).params0(1.0f, 0.0f, 1.0f, 1.5f).offset(ox, 0.0, oz);
            fx.burst(sparks(Math.round(45 * scale), r, g, b, 15.0f), ox, 0.4, oz);
            fx.burst(motes(Math.round(14 * scale), r, g, b, Burst.STAR), ox, 0.8, oz);
            fx.light(r * 5.0f, g * 5.0f, b * 5.0f, 11.0f * scale, 0.7f).intensity(FADE).offset(ox, 1.0, oz);
            fx.post(0.45f).reach(26.0f).flash(1.0f, 0.9f, 0.7f, FADE.scaled(0.16f * scale)).shake(FADE.scaled(0.45f * scale)).offset(ox, 0.0, oz);
            fx.sound(SoundEvents.GENERIC_EXPLODE, 1.6f * scale, 1.2f + rnd.nextFloat() * 0.3f).sound(SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.7f);
        }
    }

    // ------------------------------------------------------------------ magias supremas

    private static void supernova(ArcaneEngine engine, Fx fx, float r, float g, float b, boolean own) {
        float charge = 1.15f;
        double cy = 2.2;
        // Carga: uma estrela nasce e suga a luz em volta.
        fx.volume("orb", charge + 0.1f).radius(Curve.ease(0.2f, 2.0f, Easing.IN_QUAD)).bound(2.8f).colorA(r, g, b)
                .params0(1.0f, 0.5f, 1.2f, 1.7f).params1(1.0f, 0.0f, 1.0f, 0.0f).offset(0.0, cy, 0.0)
                .intensity(Curve.of(0.0f, 0.4f, 0.9f, 1.0f, 1.0f, 2.0f));
        fx.burst(Burst.of(240).style(Burst.SPARK).speed(0.0f, 0.6f).life(0.7f, charge).size(0.05f, 0.1f, 0.3f)
                .color(r * 5.0f + 1.0f, g * 5.0f + 0.5f, b * 4.0f + 0.3f, 0.0f).spawnShell(10.0f).attractTo(0.0, 0.0, 0.0, 2.2f).swirl(0.35f)
                .stretch(0.07f).fade(0.2f, 0.9f).over(0.35f), 0.0, cy, 0.0);
        fx.light(r * 3.0f, g * 3.0f, b * 3.0f, 18.0f, charge).intensity(Curve.ease(0.1f, 1.0f, Easing.IN_QUAD)).offset(0.0, cy, 0.0);
        fx.post(charge).reach(60.0f).fov(Curve.ease(0.0f, -0.07f, Easing.IN_QUAD)).shake(Curve.ease(0.0f, 0.35f, Easing.IN_QUAD))
                .fog(r * 0.5f, g * 0.3f, b * 0.2f, Curve.ease(0.0f, 0.5f, Easing.IN_QUAD), Curve.ease(0.0f, 0.5f, Easing.IN_QUAD))
                .exposure(Curve.ease(1.0f, 0.7f, Easing.IN_QUAD));
        fx.hum(SoundEvents.BEACON_AMBIENT, charge, Curve.line(0.6f, 3.0f), Curve.ease(0.6f, 2.0f, Easing.IN_QUAD))
                .sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0f, 0.6f);
        // Detonacao.
        fx.at(charge);
        fx.volume("fireball", 2.4f).radius(Curve.ease(2.5f, 8.5f, Easing.OUT_CUBIC)).colorA(r, g, b).colorB(0.06f, 0.05f, 0.05f, 0.9f)
                .params0(1.1f, 0.7f, 1.3f, 1.0f).rise(1.2f).offset(0.0, cy + 1.0, 0.0);
        ring(fx, r, g, b, 22.0f, 1.0f, 0.1f, 2.2f);
        dome(fx, r, g, b, 13.0f, 0.75f, 1.8f);
        ring(fx.at(charge + 0.18f), 1.0f, 0.9f, 0.8f, 15.0f, 0.9f, 0.07f, 1.0f);
        fx.at(charge);
        fx.volume("decal", 12.0f).radius(7.0f).layer(0).colorA(r, g, b).params0(1.0f, 0.0f, 1.0f, 2.0f);
        fx.burst(sparks(420, r, g, b, 30.0f).life(0.7f, 2.0f), 0.0, cy, 0.0);
        fx.burst(smoke(40, 3.0f, 0.07f, 0.55f).life(2.0f, 3.6f).size(1.2f, 2.4f, 2.4f), 0.0, cy, 0.0);
        fx.at(charge + 0.4f).burst(motes(90, r, g, b, Burst.GLOW).life(2.0f, 4.0f).spawnSphere(5.0f), 0.0, cy, 0.0);
        fx.at(charge);
        fx.light(r * 9.0f + 2.0f, g * 9.0f + 1.5f, b * 9.0f + 1.0f, 42.0f, 1.8f).intensity(FADE).offset(0.0, cy, 0.0);
        fx.post(0.14f).reach(70.0f).impactFrame(1.0f, 0.75f, 0.45f, Curve.of(0.0f, 1.0f, 0.7f, 1.0f, 1.0f, 0.0f));
        fx.post(1.4f).reach(70.0f).flash(1.0f, 0.95f, 0.85f, FADE.scaled(0.9f)).shake(FADE_SLOW.scaled(1.0f)).chroma(FADE.scaled(1.0f))
                .radialBlur(FADE.scaled(0.5f)).fov(Curve.of(0.0f, 0.2f, 1.0f, 0.0f)).sceneBloom(FADE.scaled(0.8f))
                .fog(r, g * 0.7f, b * 0.5f, FADE_SLOW.scaled(0.6f), null);
        fx.sound(SoundEvents.GENERIC_EXPLODE, 4.0f, 0.5f).sound(SoundEvents.GENERIC_EXPLODE, 4.0f, 0.85f)
                .sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0f, 0.6f).sound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 3.0f, 0.6f);
        if (own) {
            engine.camera.play(new CameraShot(fx.x, fx.y + cy, fx.z, 2.6f).distance(Curve.of(0.0f, 9.0f, 1.15f, 7.5f, 1.4f, 13.0f, 2.6f, 15.0f))
                    .orbit(Curve.of(0.0f, -14.0f, 2.6f, 22.0f)).height(Curve.of(0.0f, 0.5f, 2.6f, 3.5f))
                    .lookHeight(Curve.constant(0.0f)).roll(Curve.of(0.0f, 0.0f, 1.15f, -3.0f, 1.3f, 4.0f, 2.6f, 0.0f)), engine.time());
        }
    }

    private static void glacialPrison(Fx fx, float r, float g, float b) {
        float hold = 3.2f;
        fx.volume("decal", hold + 3.0f).radius(4.0f).layer(0).colorA(0.7f, 0.9f, 1.0f).params0(2.0f, 0.0f, 1.0f, 1.5f)
                .animate((e, t) -> e.params1[0] = MathUtil.saturate(e.age / 0.45f));
        fx.volume("crystal", hold + 0.15f).radius(3.3f).colorA(0.55f + r * 0.2f, 0.8f, 1.0f).colorB(r * 0.4f, g * 0.6f, b, 1.0f)
                .params0(0.0f, 9.0f, 1.2f, 0.45f).intensity(Curve.of(0.0f, 1.0f, 0.955f, 1.0f, 1.0f, 0.0f))
                .animate((e, t) -> e.params0[0] = Easing.OUT_CUBIC.apply(MathUtil.saturate(e.age / 0.5f)));
        ring(fx, 0.6f, 0.85f, 1.0f, 6.5f, 0.7f, 0.1f, 0.9f);
        fx.burst(smoke(16, 2.0f, 0.8f, 0.2f), 0.0, 0.5, 0.0);
        fx.emitter(motes(1, 0.6f, 0.85f, 1.0f, Burst.STAR).spawnSphere(2.6f).physics(0.6f, 0.4f), Curve.constant(26.0f), hold).offset(0.0, 1.5, 0.0);
        fx.light(r * 2.5f, g * 3.0f, b * 3.5f, 12.0f, hold + 0.5f).intensity(Curve.pulse(0.1f, 0.15f)).offset(0.0, 1.5, 0.0);
        fx.post(0.5f).reach(26.0f).shake(FADE.scaled(0.4f)).flash(0.7f, 0.9f, 1.0f, FADE.scaled(0.16f));
        fx.post(hold).reach(18.0f).tint(0.8f, 0.92f, 1.0f, Curve.pulse(0.1f, 0.2f).scaled(0.2f));
        fx.sound(SoundEvents.GLASS_BREAK, 1.6f, 0.6f).sound(SoundEvents.AMETHYST_CLUSTER_PLACE, 2.0f, 0.5f)
                .sound(SoundEvents.POWDER_SNOW_BREAK, 1.6f, 0.6f);
        // Estilhaca.
        fx.at(hold);
        fx.burst(Burst.of(170).style(Burst.SHARD).speed(3.0f, 14.0f).life(0.6f, 1.5f).size(0.07f, 0.18f, 0.6f).physics(0.9f, 11.0f)
                .color(0.75f, 0.9f, 1.0f, 0.9f).colorEnd(0.6f, 0.8f, 1.0f, 0.0f).spawnSphere(1.8f).upBias(0.3f), 0.0, 1.4, 0.0);
        fx.burst(motes(50, 0.6f, 0.85f, 1.0f, Burst.STAR).speed(2.0f, 8.0f), 0.0, 1.4, 0.0);
        dome(fx, 0.6f, 0.85f, 1.0f, 7.5f, 0.6f, 1.4f);
        fx.post(0.5f).reach(28.0f).flash(0.8f, 0.92f, 1.0f, FADE.scaled(0.3f)).shake(FADE.scaled(0.55f)).chroma(FADE.scaled(0.4f));
        fx.sound(SoundEvents.GLASS_BREAK, 2.2f, 0.7f).sound(SoundEvents.GLASS_BREAK, 2.0f, 1.2f).sound(SoundEvents.AMETHYST_BLOCK_BREAK, 2.0f, 0.6f);
    }

    private static void starfall(ArcaneEngine engine, Fx fx, float r, float g, float b, boolean own) {
        rune(fx, r, g, b, 6.0f, 3.4f);
        fx.post(3.0f).reach(50.0f).fog(r * 0.3f, g * 0.25f, b * 0.35f, Curve.pulse(0.15f, 0.3f).scaled(0.35f), Curve.pulse(0.15f, 0.3f).scaled(0.45f))
                .exposure(Curve.of(0.0f, 1.0f, 0.15f, 0.8f, 0.8f, 0.8f, 1.0f, 1.0f));
        fx.sound(SoundEvents.BEACON_ACTIVATE, 2.0f, 0.7f).sound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.6f);
        meteors(fx, 0.35f, 8, 4.8f, r, g, b, 1.0f);
        fx.at(2.95f);
        ring(fx, r, g, b, 13.0f, 0.9f, 0.08f, 1.2f);
        fx.post(0.6f).reach(50.0f).flash(1.0f, 0.95f, 0.8f, FADE.scaled(0.3f)).sceneBloom(FADE.scaled(0.8f));
        if (own) {
            engine.camera.play(new CameraShot(fx.x, fx.y + 1.0, fx.z, 2.8f).distance(Curve.of(0.0f, 12.0f, 2.8f, 15.0f))
                    .orbit(Curve.of(0.0f, -10.0f, 2.8f, 18.0f)).height(Curve.of(0.0f, 1.2f, 2.8f, 2.6f))
                    .lookHeight(Curve.of(0.0f, 7.0f, 0.9f, 3.0f, 2.8f, 1.0f)), engine.time());
        }
    }

    private static void soulHarvest(ArcaneEngine engine, Fx fx, Entity caster, float r, float g, float b) {
        fx.volume("decal", 3.4f).radius(4.0f).layer(0).colorA(r, g, b).colorB(r * 0.2f, g * 0.3f, b * 0.3f, 1.0f)
                .params0(4.0f, 0.0f, 1.0f, 1.5f).animate((e, t) -> e.params1[0] = SWELL.sample(t));
        fx.post(2.2f).reach(18.0f).saturation(Curve.of(0.0f, 1.0f, 0.15f, 0.35f, 0.8f, 0.35f, 1.0f, 1.0f))
                .vignette(Curve.pulse(0.15f, 0.3f).scaled(0.4f)).tint(r, g, b, Curve.pulse(0.15f, 0.3f).scaled(0.15f));
        fx.light(r * 2.0f, g * 2.0f, b * 2.0f, 9.0f, 2.0f).intensity(SWELL).offset(0.0, 1.0, 0.0);
        fx.sound(SoundEvents.SOUL_ESCAPE, 2.5f, 0.6f).sound(SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.8f).sound(SoundEvents.SCULK_CATALYST_BLOOM, 1.5f, 0.6f);
        double tx = caster != null ? caster.getX() : fx.x;
        double ty = caster != null ? caster.getY() + caster.getBbHeight() * 0.6 : fx.y + 3.0;
        double tz = caster != null ? caster.getZ() : fx.z;
        Random rnd = fx.random;
        for (int i = 0; i < 9; i++) {
            float a = rnd.nextFloat() * MathUtil.TAU;
            float d = 1.0f + rnd.nextFloat() * 3.0f;
            double sx = Math.cos(a) * d;
            double sy = 0.3 + rnd.nextFloat() * 1.4;
            double sz = Math.sin(a) * d;
            float dx = (float) (tx - (fx.x + sx));
            float dy = (float) (ty - (fx.y + sy));
            float dz = (float) (tz - (fx.z + sz));
            fx.at(0.15f + i * 0.11f).strip(StripElement.WISP, Paths.arcSweep(dx, dy, dz, (rnd.nextFloat() - 0.5f) * 3.0f, 1.5f + rnd.nextFloat() * 1.5f,
                            (rnd.nextFloat() - 0.5f) * 3.0f, 0.5f, 26), 0.95f).width(0.8f).color(r, g, b, 0.45f).cullRadius(40.0f).offset(sx, sy, sz);
            if (i % 3 == 0) {
                fx.sound(SoundEvents.SOUL_ESCAPE, 1.6f, 0.9f + rnd.nextFloat() * 0.5f);
            }
        }
        fx.at(0.1f).burst(Burst.of(110).style(Burst.GLOW).speed(0.2f, 1.6f).life(0.8f, 1.5f).size(0.06f, 0.13f, 0.4f)
                .color(r * 3.0f, g * 3.0f, b * 3.0f, 0.0f).colorEnd(r, g, b, 0.0f).spawnSphere(3.0f).attractToWorld(tx, ty, tz, 2.4f)
                .turbulence(0.5f).fade(0.2f, 0.85f).over(0.8f), 0.0, 0.8, 0.0);
        Fx end = engine.fx(tx, ty, tz).at(1.25f);
        end.volume("orb", 0.6f).radius(Curve.of(0.0f, 0.2f, 0.4f, 1.0f, 1.0f, 0.1f)).bound(2.4f).colorA(r, g, b).params0(0.0f, 1.5f, 1.0f, 1.2f)
                .params1(1.2f, 0.0f, 0.0f, 0.0f);
        end.light(r * 3.0f, g * 3.0f, b * 3.0f, 7.0f, 0.6f).intensity(FADE);
        end.burst(motes(26, r, g, b, Burst.STAR));
        end.sound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.2f, 1.4f);
    }

    private static void tempest(ArcaneEngine engine, Fx fx, float r, float g, float b, boolean own) {
        float life = 4.4f;
        fx.volume("pillar", life).radius(3.2f).column(0.0f, 1.0f, 0.0f, 12.0f).colorA(r, g, b).colorB(0.16f, 0.17f, 0.2f, 1.0f)
                .params0(12.0f, 1.5f, 1.2f, 0.8f).params1(1.0f, 0.2f, 1.3f, 0.0f).intensity(Curve.pulse(0.15f, 0.2f));
        fx.post(life).reach(60.0f).fog(0.12f, 0.14f, 0.2f, Curve.pulse(0.15f, 0.2f).scaled(0.7f), Curve.pulse(0.15f, 0.2f).scaled(0.6f))
                .exposure(Curve.of(0.0f, 1.0f, 0.15f, 0.72f, 0.8f, 0.72f, 1.0f, 1.0f)).shake(Curve.pulse(0.15f, 0.2f).scaled(0.22f))
                .saturation(Curve.of(0.0f, 1.0f, 0.15f, 0.7f, 0.8f, 0.7f, 1.0f, 1.0f));
        fx.emitter(Burst.of(1).style(Burst.GLOW).speed(0.5f, 2.0f).life(0.8f, 1.6f).size(0.04f, 0.08f, 0.5f).physics(0.2f, -3.0f)
                .color(r * 2.0f + 0.4f, g * 2.0f + 0.4f, b * 2.5f + 0.4f, 0.0f).spawnRing(4.5f).swirl(1.6f).stretch(0.12f).fade(0.2f, 0.7f),
                Curve.pulse(0.15f, 0.2f).scaled(150.0f), life).offset(0.0, 0.3, 0.0);
        fx.emitter(smoke(1, 3.0f, 0.2f, 0.25f).physics(0.5f, -2.0f), Curve.pulse(0.15f, 0.2f).scaled(14.0f), life).offset(0.0, 1.0, 0.0);
        fx.hum(SoundEvents.ELYTRA_FLYING, life, Curve.pulse(0.15f, 0.2f).scaled(1.6f), Curve.of(0.0f, 0.6f, 0.5f, 0.9f, 1.0f, 0.6f))
                .sound(SoundEvents.TRIDENT_THUNDER, 2.5f, 0.6f);
        Random rnd = fx.random;
        for (int i = 0; i < 9; i++) {
            float a = rnd.nextFloat() * MathUtil.TAU;
            float d = i == 0 ? 0.0f : 2.0f + rnd.nextFloat() * 5.0f;
            fx.at(0.55f + i * 0.4f + rnd.nextFloat() * 0.12f);
            strike(fx, (float) Math.cos(a) * d, (float) Math.sin(a) * d, r, g, b, i == 0 || i == 8 ? 1.5f : 1.0f, true);
        }
        fx.at(life - 0.35f);
        dome(fx, r, g, b, 14.0f, 0.7f, 1.5f);
        if (own) {
            engine.camera.play(new CameraShot(fx.x, fx.y + 2.0, fx.z, 2.8f).distance(Curve.of(0.0f, 11.0f, 2.8f, 14.0f))
                    .orbit(Curve.of(0.0f, -8.0f, 2.8f, 26.0f)).height(Curve.of(0.0f, 0.8f, 2.8f, 2.4f))
                    .lookHeight(Curve.of(0.0f, 2.0f, 2.8f, 5.0f)).roll(Curve.of(0.0f, 0.0f, 1.4f, 3.5f, 2.8f, 0.0f)), engine.time());
        }
    }

    private static void vinePrison(Fx fx, float r, float g, float b) {
        float life = 3.6f;
        rune(fx, r, g, b, 3.1f, life);
        Curve grow = Curve.of(0.0f, 0.0f, 0.2f, 1.0f, 1.0f, 1.0f);
        Curve alive = Curve.of(0.0f, 1.0f, 0.86f, 1.0f, 1.0f, 0.0f);
        for (int i = 0; i < 9; i++) {
            fx.at(i * 0.03f).strip(StripElement.VINE, Paths.helix(2.4f, 0.45f, 4.2f, 0.9f, i / 9.0f, 48), life).width(0.42f)
                    .widthAlong(Curve.line(1.15f, 0.2f)).color(0.16f + r * 0.08f, 0.42f, 0.10f + b * 0.08f, 1.0f).shaderAge(grow).intensity(alive);
        }
        for (int i = 0; i < 5; i++) {
            fx.at(0.15f + i * 0.04f).strip(StripElement.VINE, Paths.helix(1.3f, 0.2f, 3.0f, -1.3f, i / 5.0f + 0.1f, 40), life - 0.2f).width(0.24f)
                    .widthAlong(Curve.line(1.1f, 0.15f)).color(0.2f, 0.5f, 0.14f, 1.0f).shaderAge(grow).intensity(alive);
        }
        Burst leaves = Burst.of(60).style(Burst.LEAF).speed(1.0f, 5.0f).life(1.2f, 2.6f).size(0.08f, 0.15f, 1.0f).physics(1.4f, 2.0f)
                .color(0.2f, 0.55f, 0.15f, 1.0f).colorEnd(0.4f, 0.4f, 0.1f, 0.0f).turbulence(0.6f).upBias(0.7f).spawnRing(2.2f);
        fx.at(0.0f).burst(leaves, 0.0, 0.4, 0.0);
        fx.emitter(motes(1, 0.7f, 1.0f, 0.4f, Burst.GLOW).spawnSphere(2.4f), Curve.constant(24.0f), life).offset(0.0, 1.6, 0.0);
        fx.light(r * 2.2f, g * 2.6f, b * 2.0f, 10.0f, life).intensity(Curve.pulse(0.1f, 0.2f)).offset(0.0, 1.5, 0.0);
        fx.post(0.5f).reach(20.0f).shake(FADE.scaled(0.35f));
        fx.sound(SoundEvents.GRASS_BREAK, 2.0f, 0.5f).sound(SoundEvents.ROOTED_DIRT_BREAK, 2.0f, 0.6f).sound(SoundEvents.BONE_MEAL_USE, 1.6f, 0.7f);
        fx.at(life - 0.5f).burst(leaves, 0.0, 1.5, 0.0).sound(SoundEvents.AZALEA_LEAVES_BREAK, 1.6f, 0.7f);
    }

    private static void singularity(ArcaneEngine engine, Fx fx, float r, float g, float b, boolean own, int durationTicks) {
        float life = MathUtil.clamp(durationTicks / 20.0f, 3.0f, 7.0f);
        float end = life - 0.3f;
        double cy = 2.6;
        float in = 0.6f / life;
        float out = 1.0f - 0.3f / life;
        fx.volume("blackhole", life).radius(Curve.builder().key(0.0f, 0.05f).key(in, 1.1f, Easing.OUT_BACK).key(out, 1.25f).key(1.0f, 0.02f, Easing.IN_CUBIC).build())
                .bound(7.0f).axis(0.18f, 1.0f, 0.26f).colorA(r, g, b).colorB(0.3f, 0.1f, 0.5f, 1.0f).params0(4.2f, 1.0f, 1.0f, 1.0f).distort()
                .offset(0.0, cy, 0.0);
        fx.emitter(Burst.of(1).style(Burst.SPARK).speed(0.0f, 0.8f).life(0.9f, 1.6f).size(0.05f, 0.1f, 0.25f)
                .color(r * 4.0f + 0.5f, g * 4.0f + 0.3f, b * 4.0f + 0.3f, 0.0f).colorEnd(0.6f, 0.2f, 1.2f, 0.0f).spawnShell(9.0f)
                .attractTo(0.0, 0.0, 0.0, 2.0f).swirl(0.8f).stretch(0.08f).fade(0.2f, 0.9f), Curve.of(0.0f, 60.0f, in, 200.0f, out, 200.0f, 1.0f, 0.0f), end)
                .offset(0.0, cy, 0.0);
        fx.light(r * 2.5f, g * 2.0f, b * 2.5f, 16.0f, life).intensity(Curve.pulse(0.1f, 0.1f)).offset(0.0, cy, 0.0);
        fx.post(life).reach(60.0f).fog(0.08f, 0.02f, 0.14f, Curve.pulse(0.12f, 0.12f).scaled(0.75f), Curve.pulse(0.12f, 0.12f).scaled(0.7f))
                .exposure(Curve.of(0.0f, 1.0f, 0.12f, 0.78f, 0.9f, 0.78f, 1.0f, 1.0f)).saturation(Curve.of(0.0f, 1.0f, 0.12f, 0.7f, 0.9f, 0.7f, 1.0f, 1.0f))
                .vignette(Curve.pulse(0.12f, 0.12f).scaled(0.4f)).chroma(Curve.pulse(0.12f, 0.12f).scaled(0.45f))
                .shake(Curve.pulse(0.12f, 0.12f).scaled(0.2f)).fov(Curve.of(0.0f, 0.0f, 0.12f, 0.06f, 0.9f, 0.08f, 1.0f, 0.0f));
        fx.hum(SoundEvents.BEACON_AMBIENT, end, Curve.pulse(0.1f, 0.1f).scaled(3.0f), Curve.line(0.5f, 0.75f))
                .hum(SoundEvents.PORTAL_AMBIENT, end, Curve.pulse(0.1f, 0.1f).scaled(0.6f), Curve.constant(0.5f))
                .sound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0f, 0.5f).sound(SoundEvents.ENDERMAN_TELEPORT, 2.0f, 0.5f);
        // Colapso.
        fx.at(end);
        dome(fx.at(end), 0.8f, 0.5f, 1.0f, 16.0f, 0.8f, 2.4f);
        fx.volume("shockwave", 0.9f).radius(Curve.ease(1.0f, 20.0f, Easing.OUT_CUBIC)).colorA(r, g, b).params0(0.12f, 0.1f, 1.2f, 1.6f)
                .distort().offset(0.0, cy, 0.0);
        fx.burst(sparks(320, 0.8f, 0.5f, 1.0f, 26.0f).physics(1.6f, 2.0f).upBias(0.0f), 0.0, cy, 0.0);
        fx.burst(motes(70, 0.8f, 0.5f, 1.0f, Burst.STAR).speed(3.0f, 10.0f).spawnSphere(1.0f), 0.0, cy, 0.0);
        fx.light(6.0f, 4.0f, 8.0f, 36.0f, 1.2f).intensity(FADE).offset(0.0, cy, 0.0);
        fx.post(0.12f).reach(70.0f).impactFrame(0.75f, 0.45f, 1.0f, Curve.of(0.0f, 1.0f, 0.7f, 1.0f, 1.0f, 0.0f));
        fx.post(1.1f).reach(70.0f).flash(0.9f, 0.8f, 1.0f, FADE.scaled(0.7f)).shake(FADE_SLOW.scaled(0.9f)).radialBlur(FADE.scaled(0.45f))
                .chroma(FADE.scaled(0.9f));
        fx.sound(SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 0.7f).sound(SoundEvents.GENERIC_EXPLODE, 4.0f, 0.5f);
        if (own) {
            engine.camera.play(new CameraShot(fx.x, fx.y + cy, fx.z, 3.0f).distance(Curve.of(0.0f, 8.0f, 3.0f, 11.5f))
                    .orbit(Curve.of(0.0f, -10.0f, 3.0f, 48.0f)).height(Curve.of(0.0f, -0.6f, 3.0f, 1.6f)).lookHeight(Curve.constant(0.0f))
                    .roll(Curve.of(0.0f, 0.0f, 1.5f, 5.0f, 3.0f, 0.0f)).fov(Curve.of(0.0f, 0.0f, 3.0f, -0.08f)), engine.time());
        }
    }
}
