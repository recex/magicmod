package com.arcanemod.magicmod.spell;

/**
 * Escolas de magia usadas para organizar as 30 magias do mod e
 * para colorir automaticamente particulas/UI por categoria.
 */
public enum SpellSchool {
    FIRE(0xFF5A1E),
    FROST(0x7FDFFF),
    ARCANE(0xB266FF),
    NATURE(0x55C45E),
    SHADOW(0x5A2A7A),
    LIGHT(0xFFE680),
    STORM(0x4FA8FF),
    BLOOD(0xB3001F);

    private final int color;

    SpellSchool(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }
}
