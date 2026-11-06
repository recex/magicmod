package com.arcanemod.magicmod;

import com.arcanemod.magicmod.capability.ModCapabilities;
import com.arcanemod.magicmod.network.ModNetwork;
import com.arcanemod.magicmod.registry.ModItems;
import com.arcanemod.magicmod.registry.ModParticles;
import com.arcanemod.magicmod.registry.ModSpells;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MagicMod.MOD_ID)
public class MagicMod {

    public static final String MOD_ID = "magicmod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public MagicMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(ModCapabilities::register);

        if (FMLEnvironment.dist.isClient()) {
            com.arcanemod.magicmod.client.ClientSetup.init(modEventBus);
        }

        ModNetwork.register();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Arcane Spells mod inicializando...");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModSpells.bootstrap();
            LOGGER.info("Arcane Spells: {} magias registradas", ModSpells.all().size());
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(ModItems.GRIMOIRE.get()));
            event.accept(new ItemStack(ModItems.SPELL_FOCUS.get()));
        }
    }
}
