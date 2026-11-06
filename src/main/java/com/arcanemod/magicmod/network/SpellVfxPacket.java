package com.arcanemod.magicmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Enviado do servidor para os clientes proximos para disparar o
 * efeito visual (particulas + shader) correspondente a magia lancada.
 * A logica real de spawn de particulas/shader fica isolada no
 * SpellVfxDispatcher (client-only) para nao vazar classes de
 * renderizacao no classloader do servidor dedicado.
 */
public class SpellVfxPacket {

    public final ResourceLocation spellId;
    public final int casterEntityId;
    public final double x, y, z;
    public final int chargeTicks;

    public SpellVfxPacket(ResourceLocation spellId, int casterEntityId, double x, double y, double z, int chargeTicks) {
        this.spellId = spellId;
        this.casterEntityId = casterEntityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.chargeTicks = chargeTicks;
    }

    public static void encode(SpellVfxPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.spellId);
        buf.writeVarInt(msg.casterEntityId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeVarInt(msg.chargeTicks);
    }

    public static SpellVfxPacket decode(FriendlyByteBuf buf) {
        return new SpellVfxPacket(buf.readResourceLocation(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }

    public static void handle(SpellVfxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.arcanemod.magicmod.client.SpellVfxDispatcher.dispatch(msg)));
        ctx.setPacketHandled(true);
    }
}
