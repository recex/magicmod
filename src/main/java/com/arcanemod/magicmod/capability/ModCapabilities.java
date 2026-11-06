package com.arcanemod.magicmod.capability;

import com.arcanemod.magicmod.MagicMod;
import com.arcanemod.magicmod.spell.SpellCasterData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ModCapabilities {

    public static final Capability<SpellCasterData> SPELL_CASTER =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation ID = new ResourceLocation(MagicMod.MOD_ID, "spell_caster");

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(SpellCasterData.class);
    }

    @Mod.EventBusSubscriber(modid = MagicMod.MOD_ID)
    public static class Provider {

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
            if (event.getObject() instanceof net.minecraft.world.entity.player.Player) {
                SpellCasterCapability impl = new SpellCasterCapability();
                LazyOptional<SpellCasterData> optional = LazyOptional.of(() -> impl);
                event.addCapability(ID, new ICapabilityProvider() {
                    @Override
                    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                        if (cap == SPELL_CASTER) {
                            return optional.cast();
                        }
                        return LazyOptional.empty();
                    }
                });
                event.addListener(optional::invalidate);
            }
        }

        @SubscribeEvent(priority = EventPriority.NORMAL)
        public static void clonePlayer(PlayerEvent.Clone event) {
            if (event.isWasDeath() || true) {
                event.getOriginal().getCapability(SPELL_CASTER).ifPresent(oldStore -> {
                    event.getEntity().getCapability(SPELL_CASTER).ifPresent(newStore -> {
                        if (oldStore instanceof SpellCasterCapability oldImpl
                                && newStore instanceof SpellCasterCapability newImpl) {
                            newImpl.copyFrom(oldImpl);
                        }
                    });
                });
            }
        }
    }
}
