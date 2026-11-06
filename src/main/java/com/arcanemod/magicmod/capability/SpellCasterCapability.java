package com.arcanemod.magicmod.capability;

import com.arcanemod.magicmod.spell.SpellCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.Map;

public class SpellCasterCapability implements SpellCasterData, INBTSerializable<CompoundTag> {

    private int mana;
    private int maxMana = 100;
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private ResourceLocation selectedSpell = new ResourceLocation("magicmod", "arcane_meteor");

    public SpellCasterCapability() {
        this.mana = maxMana;
    }

    @Override
    public int getMana() {
        return mana;
    }

    @Override
    public int getMaxMana() {
        return maxMana;
    }

    @Override
    public void setMana(int mana) {
        this.mana = Math.max(0, Math.min(maxMana, mana));
    }

    public void setMaxMana(int maxMana) {
        this.maxMana = Math.max(1, maxMana);
        this.mana = Math.min(this.mana, this.maxMana);
    }

    @Override
    public boolean isOnCooldown(ResourceLocation spellId) {
        return cooldowns.getOrDefault(spellId.toString(), 0) > 0;
    }

    @Override
    public int getRemainingCooldown(ResourceLocation spellId) {
        return cooldowns.getOrDefault(spellId.toString(), 0);
    }

    @Override
    public void setCooldown(ResourceLocation spellId, int ticks) {
        cooldowns.put(spellId.toString(), ticks);
    }

    @Override
    public void tickCooldowns() {
        if (cooldowns.isEmpty()) return;
        cooldowns.replaceAll((key, value) -> Math.max(0, value - 1));
        cooldowns.values().removeIf(v -> v <= 0);
    }

    @Override
    public ResourceLocation getSelectedSpell() {
        return selectedSpell;
    }

    @Override
    public void setSelectedSpell(ResourceLocation id) {
        this.selectedSpell = id;
    }

    public void copyFrom(SpellCasterCapability other) {
        this.mana = other.mana;
        this.maxMana = other.maxMana;
        this.cooldowns.clear();
        this.cooldowns.putAll(other.cooldowns);
        this.selectedSpell = other.selectedSpell;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("mana", mana);
        tag.putInt("maxMana", maxMana);
        tag.putString("selectedSpell", selectedSpell.toString());
        CompoundTag cdTag = new CompoundTag();
        cooldowns.forEach(cdTag::putInt);
        tag.put("cooldowns", cdTag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        mana = nbt.getInt("mana");
        maxMana = nbt.contains("maxMana") ? nbt.getInt("maxMana") : 100;
        if (nbt.contains("selectedSpell")) {
            selectedSpell = new ResourceLocation(nbt.getString("selectedSpell"));
        }
        cooldowns.clear();
        if (nbt.contains("cooldowns")) {
            CompoundTag cdTag = nbt.getCompound("cooldowns");
            for (String key : cdTag.getAllKeys()) {
                cooldowns.put(key, cdTag.getInt(key));
            }
        }
    }
}
