package com.arcanemod.magicmod.client.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuracao do motor de efeitos (arquivo {@code magicmod-client.toml}).
 * Os valores sao copiados para campos estaticos quando o arquivo carrega ou
 * muda, para que o codigo de render nao consulte o arquivo a cada quadro.
 */
public final class VfxConfig {

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> QUALITY;
    private static final ForgeConfigSpec.DoubleValue BLOOM;
    private static final ForgeConfigSpec.BooleanValue DISTORTION;
    private static final ForgeConfigSpec.BooleanValue DYNAMIC_LIGHTS;
    private static final ForgeConfigSpec.DoubleValue SCREEN_SHAKE;
    private static final ForgeConfigSpec.DoubleValue FLASH_INTENSITY;
    private static final ForgeConfigSpec.BooleanValue CINEMATIC_CAMERA;
    private static final ForgeConfigSpec.DoubleValue SOUND_VOLUME;
    private static final ForgeConfigSpec.IntValue MAX_DISTANCE;

    public static volatile boolean enabled = true;
    /** "auto", "low", "medium", "high" ou "ultra". */
    public static volatile String quality = "auto";
    public static volatile float bloom = 1.0f;
    public static volatile boolean distortion = true;
    public static volatile boolean dynamicLights = true;
    public static volatile float screenShake = 1.0f;
    public static volatile float flashIntensity = 1.0f;
    public static volatile boolean cinematicCamera = true;
    public static volatile float soundVolume = 1.0f;
    public static volatile int maxDistance = 128;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Motor de efeitos visuais do Arcane Spells").push("vfx");
        ENABLED = b.comment("Liga o motor de efeitos. Desligado, as magias usam as particulas simples originais.")
                .define("enabled", true);
        QUALITY = b.comment("Qualidade: auto (ajusta sozinho pelo desempenho), low, medium, high ou ultra.")
                .define("quality", "auto");
        BLOOM = b.comment("Intensidade do brilho (bloom). 0 desliga.")
                .defineInRange("bloom", 1.0, 0.0, 2.0);
        DISTORTION = b.comment("Distorcao do espaco (ondas de choque, lente da singularidade).")
                .define("distortion", true);
        DYNAMIC_LIGHTS = b.comment("Luzes dinamicas dos efeitos sobre o terreno.")
                .define("dynamicLights", true);
        SCREEN_SHAKE = b.comment("Tremor de camera. 0 desliga (recomendado para quem sente enjoo).")
                .defineInRange("screenShake", 1.0, 0.0, 1.5);
        FLASH_INTENSITY = b.comment("Intensidade dos claroes de tela. Reduza ou zere em caso de fotossensibilidade.")
                .defineInRange("flashIntensity", 1.0, 0.0, 1.0);
        CINEMATIC_CAMERA = b.comment("Camera cinematografica curta ao lancar as magias supremas (so para quem lanca; qualquer tecla de movimento pula).")
                .define("cinematicCamera", true);
        SOUND_VOLUME = b.comment("Volume das camadas de som dos efeitos.")
                .defineInRange("soundVolume", 1.0, 0.0, 1.0);
        MAX_DISTANCE = b.comment("Distancia maxima (blocos) em que os efeitos sao desenhados.")
                .defineInRange("maxDistance", 128, 32, 256);
        b.pop();
        SPEC = b.build();
    }

    private VfxConfig() {
    }

    /** Copia os valores do arquivo para os campos estaticos. */
    public static void bake() {
        try {
            enabled = ENABLED.get();
            quality = QUALITY.get();
            bloom = BLOOM.get().floatValue();
            distortion = DISTORTION.get();
            dynamicLights = DYNAMIC_LIGHTS.get();
            screenShake = SCREEN_SHAKE.get().floatValue();
            flashIntensity = FLASH_INTENSITY.get().floatValue();
            cinematicCamera = CINEMATIC_CAMERA.get();
            soundVolume = SOUND_VOLUME.get().floatValue();
            maxDistance = MAX_DISTANCE.get();
        } catch (IllegalStateException ignored) {
            // Arquivo ainda nao carregado: ficam os padroes.
        }
    }

    public static void setQuality(String value) {
        QUALITY.set(value);
        QUALITY.save();
        quality = value;
    }

    public static void setCinematicCamera(boolean value) {
        CINEMATIC_CAMERA.set(value);
        CINEMATIC_CAMERA.save();
        cinematicCamera = value;
    }

    public static void setEnabled(boolean value) {
        ENABLED.set(value);
        ENABLED.save();
        enabled = value;
    }
}
