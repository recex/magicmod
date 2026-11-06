package com.arcanemod.magicmod.registry;

import com.arcanemod.magicmod.MagicMod;
import com.arcanemod.magicmod.item.GrimoireItem;
import com.arcanemod.magicmod.item.SpellFocusItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MagicMod.MOD_ID);

    public static final RegistryObject<Item> GRIMOIRE = ITEMS.register("grimoire",
            () -> new GrimoireItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SPELL_FOCUS = ITEMS.register("spell_focus",
            () -> new SpellFocusItem(new Item.Properties().stacksTo(1).durability(500)));
}
