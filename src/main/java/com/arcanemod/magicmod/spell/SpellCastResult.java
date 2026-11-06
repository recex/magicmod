package com.arcanemod.magicmod.spell;

/**
 * Resultado de uma tentativa de cast. SUCCESS consome mana e cooldown;
 * os outros valores abortam o cast sem consumir recursos.
 */
public enum SpellCastResult {
    SUCCESS,
    NOT_ENOUGH_MANA,
    ON_COOLDOWN,
    NO_VALID_TARGET,
    FAILED
}
