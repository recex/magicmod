package com.arcanemod.magicmod.network;

import com.arcanemod.magicmod.capability.ModCapabilities;
import com.arcanemod.magicmod.registry.ModSpells;
import com.arcanemod.magicmod.spell.Spell;
import com.arcanemod.magicmod.spell.SpellCastContext;
import com.arcanemod.magicmod.spell.SpellCastResult;
import com.arcanemod.magicmod.spell.SpellCasterData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Enviado do cliente para o servidor quando o jogador tenta lancar
 * uma magia (clique com o Foco de Magia / grimorio). O servidor valida
 * mana/cooldown, executa a logica e entao propaga o SpellVfxPacket
 * para os clientes proximos para os efeitos visuais.
 */
public class RequestCastSpellPacket {

    private final ResourceLocation spellId;
    private final int chargeTicks;

    public RequestCastSpellPacket(ResourceLocation spellId, int chargeTicks) {
        this.spellId = spellId;
        this.chargeTicks = chargeTicks;
    }

    public static void encode(RequestCastSpellPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.spellId);
        buf.writeVarInt(msg.chargeTicks);
    }

    public static RequestCastSpellPacket decode(FriendlyByteBuf buf) {
        return new RequestCastSpellPacket(buf.readResourceLocation(), buf.readVarInt());
    }

    public static void handle(RequestCastSpellPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Spell spell = ModSpells.get(msg.spellId);
            if (spell == null) return;

            player.getCapability(ModCapabilities.SPELL_CASTER).ifPresent(data -> {
                if (!spell.canAfford(data)) {
                    return;
                }

                HitResult hit = pickTarget(player, 32.0);
                SpellCastContext context = new SpellCastContext(hit, msg.chargeTicks);

                SpellCastResult result = spell.cast(player.level(), player, context);

                if (result == SpellCastResult.SUCCESS) {
                    data.consumeMana(spell.getManaCost());
                    data.setCooldown(spell.getId(), spell.getCooldownTicks());

                    double x = player.getX();
                    double y = player.getEyeY();
                    double z = player.getZ();
                    if (hit instanceof EntityHitResult eh) {
                        x = eh.getLocation().x;
                        y = eh.getLocation().y;
                        z = eh.getLocation().z;
                    } else if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                        x = hit.getLocation().x;
                        y = hit.getLocation().y;
                        z = hit.getLocation().z;
                    }

                    SpellVfxPacket vfx = new SpellVfxPacket(spell.getId(), player.getId(), x, y, z, msg.chargeTicks);
                    ModNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                            vfx
                    );
                }
            });
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Raytrace manual de bloco + entidade na direcao do olhar do
     * jogador, ja que Player/Entity nao expoe um metodo publico
     * equivalente ao Minecraft.hitResult do lado cliente.
     */
    private static HitResult pickTarget(ServerPlayer player, double distance) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(look.x * distance, look.y * distance, look.z * distance);

        HitResult blockHit = player.level().clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? distance
                : blockHit.getLocation().distanceTo(eyePos);

        Vec3 searchEnd = eyePos.add(look.x * blockDistance, look.y * blockDistance, look.z * blockDistance);
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(blockDistance)).inflate(1.0);

        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eyePos, searchEnd, searchBox,
                (Entity e) -> !e.isSpectator() && e.isPickable() && e != player,
                blockDistance * blockDistance
        );

        if (entityHit != null) {
            return entityHit;
        }
        return blockHit;
    }
}
