package com.arcanemod.magicmod.client.shader;

import com.arcanemod.magicmod.MagicMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;

/**
 * Controla a ativacao do shader de pos-processamento de tela cheia
 * (post/spell_distortion.json) usado por magias "fullscreenShader".
 * O shader real (distorcao de lente + vinheta colorida que pulsa) fica
 * no resource pack em assets/magicmod/shaders/post/spell_distortion.json
 * e assets/magicmod/shaders/core/spell_distortion.fsh.
 *
 * NOTA TECNICA: o nome exato do metodo de GameRenderer que liga/desliga
 * um post-effect por ResourceLocation varia entre mapeamentos/versoes
 * (setPostEffect / loadEffect / togglePostEffect, dependendo da build).
 * Para nao depender de adivinhar o nome certo e travar a compilacao de
 * novo, chamamos esses metodos via reflection, tentando uma lista de
 * nomes conhecidos em ordem. Se nenhum bater, o efeito e ignorado em
 * silencio (log de aviso uma unica vez) em vez de quebrar o build ou
 * o jogo - as particulas das magias continuam funcionando normalmente
 * mesmo sem o shader de tela cheia.
 */
public class ShaderEffectManager {

    private static final ShaderEffectManager INSTANCE = new ShaderEffectManager();

    private static final ResourceLocation SPELL_SHADER =
            new ResourceLocation(MagicMod.MOD_ID, "shaders/post/spell_distortion.json");

    private static final ResourceLocation SINGULARITY_SHADER =
            new ResourceLocation(MagicMod.MOD_ID, "shaders/post/singularity.json");

    private static final String[] SET_METHOD_NAMES = {
            "setPostEffect", "loadEffect", "togglePostEffect", "setPostChain"
    };
    private static final String[] CLEAR_METHOD_NAMES = {
            "clearPostEffect", "shutdownEffect", "stopUsingEffect"
    };

    private Method cachedSetMethod;
    private Method cachedClearMethod;
    private boolean warnedMissing = false;

    private int remainingTicks = 0;
    private int totalTicks = 0;
    private float colorR, colorG, colorB;
    private boolean active = false;
    private ResourceLocation activeShader = SPELL_SHADER;

    public static ShaderEffectManager get() {
        return INSTANCE;
    }

    public void triggerSpellDistortion(int durationTicks, float r, float g, float b) {
        trigger(SPELL_SHADER, durationTicks, r, g, b);
    }

    /**
     * Ativa o shader experimental de singularidade (efeito "buraco
     * negro" que deforma a geometria da tela ao redor do ponto de
     * impacto). Mesmo mecanismo do shader padrao, apenas trocando
     * qual programa de post-processing e carregado.
     */
    public void triggerSingularity(int durationTicks, float r, float g, float b) {
        trigger(SINGULARITY_SHADER, durationTicks, r, g, b);
    }

    private void trigger(ResourceLocation shader, int durationTicks, float r, float g, float b) {
        this.remainingTicks = durationTicks;
        this.totalTicks = durationTicks;
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;

        if (active && !shader.equals(activeShader)) {
            invokeClear();
            active = false;
        }

        this.activeShader = shader;

        if (!active) {
            invokeSet();
            active = true;
        }
    }

    /** Chamado todo client tick por SpellShaderTickHandler. */
    public void tick() {
        if (!active) return;

        if (remainingTicks <= 0) {
            invokeClear();
            active = false;
            return;
        }
        remainingTicks--;
    }

    public float getIntensity() {
        if (!active || totalTicks <= 0) return 0f;
        return remainingTicks / (float) totalTicks;
    }

    public float getColorR() { return colorR; }
    public float getColorG() { return colorG; }
    public float getColorB() { return colorB; }
    public boolean isActive() { return active; }

    private void invokeSet() {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        if (cachedSetMethod == null) {
            cachedSetMethod = findMethod(renderer.getClass(), SET_METHOD_NAMES, ResourceLocation.class);
        }
        if (cachedSetMethod != null) {
            try {
                cachedSetMethod.invoke(renderer, activeShader);
                return;
            } catch (ReflectiveOperationException ignored) {
                // cai no aviso abaixo
            }
        }
        warnOnceMissing();
    }

    private void invokeClear() {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        if (cachedClearMethod == null) {
            cachedClearMethod = findMethod(renderer.getClass(), CLEAR_METHOD_NAMES);
        }
        if (cachedClearMethod != null) {
            try {
                cachedClearMethod.invoke(renderer);
            } catch (ReflectiveOperationException ignored) {
                // sem acao - nao ha o que limpar se nunca ligou
            }
        }
    }

    private Method findMethod(Class<?> clazz, String[] names, Class<?>... paramTypes) {
        for (String name : names) {
            try {
                Method m = clazz.getMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // tenta o proximo nome
            }
        }
        return null;
    }

    private void warnOnceMissing() {
        if (!warnedMissing) {
            warnedMissing = true;
            MagicMod.LOGGER.warn("Nao foi possivel ativar o shader de tela cheia das magias " +
                    "(metodo de post-effect nao encontrado por reflection nesta versao do Forge). " +
                    "As particulas continuam funcionando normalmente.");
        }
    }
}
