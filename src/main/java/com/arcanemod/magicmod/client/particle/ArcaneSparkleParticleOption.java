package com.arcanemod.magicmod.client.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Opcao de particula generica e parametrizavel: cor (RGB), escala e
 * tempo de vida em ticks. Isso permite que todas as 30 magias
 * reutilizem o mesmo conjunto de particulas registradas, apenas
 * variando esses parametros por escola/magia, ao inves de precisar
 * de uma ParticleType dedicada para cada efeito.
 */
public class ArcaneSparkleParticleOption implements ParticleOptions {

    public static final ParticleOptions.Deserializer<ArcaneSparkleParticleOption> DESERIALIZER =
            new ParticleOptions.Deserializer<>() {
                @Override
                public ArcaneSparkleParticleOption fromCommand(ParticleType<ArcaneSparkleParticleOption> type, StringReader reader) throws CommandSyntaxException {
                    reader.expect(' ');
                    float r = reader.readFloat();
                    reader.expect(' ');
                    float g = reader.readFloat();
                    reader.expect(' ');
                    float b = reader.readFloat();
                    reader.expect(' ');
                    float scale = reader.readFloat();
                    return new ArcaneSparkleParticleOption(type, r, g, b, scale, 40);
                }

                @Override
                public ArcaneSparkleParticleOption fromNetwork(ParticleType<ArcaneSparkleParticleOption> type, FriendlyByteBuf buf) {
                    return new ArcaneSparkleParticleOption(type, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
                }
            };

    private final ParticleType<ArcaneSparkleParticleOption> type;
    private final float r, g, b;
    private final float scale;
    private final int lifetimeTicks;

    public ArcaneSparkleParticleOption(ParticleType<ArcaneSparkleParticleOption> type, float r, float g, float b, float scale, int lifetimeTicks) {
        this.type = type;
        this.r = r;
        this.g = g;
        this.b = b;
        this.scale = scale;
        this.lifetimeTicks = lifetimeTicks;
    }

    public static Codec<ArcaneSparkleParticleOption> codec(ParticleType<ArcaneSparkleParticleOption> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("r").forGetter(o -> o.r),
                Codec.FLOAT.fieldOf("g").forGetter(o -> o.g),
                Codec.FLOAT.fieldOf("b").forGetter(o -> o.b),
                Codec.FLOAT.fieldOf("scale").forGetter(o -> o.scale),
                Codec.INT.fieldOf("lifetime").forGetter(o -> o.lifetimeTicks)
        ).apply(instance, (r, g, b, scale, lifetime) -> new ArcaneSparkleParticleOption(type, r, g, b, scale, lifetime)));
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(r);
        buf.writeFloat(g);
        buf.writeFloat(b);
        buf.writeFloat(scale);
        buf.writeVarInt(lifetimeTicks);
    }

    @Override
    public String writeToString() {
        return String.format("%s %.2f %.2f %.2f %.2f", type.toString(), r, g, b, scale);
    }

    @Override
    public ParticleType<?> getType() {
        return type;
    }

    public float getR() { return r; }
    public float getG() { return g; }
    public float getB() { return b; }
    public float getScale() { return scale; }
    public int getLifetimeTicks() { return lifetimeTicks; }
}
