package com.arcanemod.magicmod.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapa de spellId (path) -> VisualProfile. Populado por
 * ModSpells.bootstrap() no servidor e tambem disponivel no cliente
 * (resource comum), lido pelo SpellVfxDispatcher.
 */
public class VisualProfiles {

    private static final Map<String, VisualProfile> PROFILES = new HashMap<>();

    public static void register(String spellPath, VisualProfile profile) {
        PROFILES.put(spellPath, profile);
    }

    public static VisualProfile get(String spellPath) {
        return PROFILES.getOrDefault(spellPath, DEFAULT);
    }

    private static final VisualProfile DEFAULT = VisualProfile.builder(0xB266FF)
            .impactParticles(30)
            .build();
}
