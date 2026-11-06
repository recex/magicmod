package com.arcanemod.magicmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

public class KeyBindings {

    public static final String CATEGORY = "key.categories.magicmod";

    public static final KeyMapping CAST_SPELL = new KeyMapping(
            "key.magicmod.cast_spell",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY
    );

    public static final KeyMapping NEXT_SPELL = new KeyMapping(
            "key.magicmod.next_spell",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_RBRACKET,
            CATEGORY
    );

    public static final KeyMapping PREV_SPELL = new KeyMapping(
            "key.magicmod.prev_spell",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LBRACKET,
            CATEGORY
    );
}
