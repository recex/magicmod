package com.arcanemod.magicmod.item;

import net.minecraft.world.item.Item;

/**
 * Item equipavel/segurado que atua como o "foco" visual de cast.
 * A acao de lancar magia e feita pela tecla CAST_SPELL (ver
 * SpellCastInputHandler) enquanto este item esta na mao, nao pelo
 * clique direito padrao, para permitir carregar o cast (hold).
 */
public class SpellFocusItem extends Item {
    public SpellFocusItem(Properties properties) {
        super(properties);
    }
}
