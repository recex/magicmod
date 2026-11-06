package com.arcanemod.magicmod.network;

import com.arcanemod.magicmod.MagicMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MagicMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, RequestCastSpellPacket.class,
                RequestCastSpellPacket::encode, RequestCastSpellPacket::decode, RequestCastSpellPacket::handle);

        CHANNEL.registerMessage(id++, SpellVfxPacket.class,
                SpellVfxPacket::encode, SpellVfxPacket::decode, SpellVfxPacket::handle);
    }
}
