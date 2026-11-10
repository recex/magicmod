package com.arcanemod.magicmod.engine.shader;

import com.arcanemod.magicmod.MagicMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Biblioteca de programas GLSL do motor. Os arquivos ficam em
 * {@code assets/magicmod/shaders/vfx/} (logo, um resource pack pode
 * substitui-los), sao compilados sob demanda e ficam em cache. Um programa que
 * falha e anotado e nao e tentado de novo ate o proximo recarregamento: o
 * efeito correspondente apenas deixa de aparecer, sem derrubar o jogo.
 */
public final class ShaderLibrary {

    public static final String ROOT = "shaders/vfx/";
    private static final Logger LOGGER = LogManager.getLogger("MagicMod/Shaders");
    private static final String GLSL_VERSION = "150";

    private final Map<String, ShaderProgram> programs = new HashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();
    private int generation;

    /** Programa da variante pedida, ou {@code null} se nao compilou. */
    public ShaderProgram get(String vertex, String fragment, String[] attributes, String... defines) {
        String key = vertex + " + " + fragment + (defines.length == 0 ? "" : " {" + String.join(", ", defines) + "}");
        ShaderProgram program = programs.get(key);
        if (program != null) {
            return program;
        }
        if (failures.containsKey(key)) {
            return null;
        }
        try {
            String header = ShaderPreprocessor.header(GLSL_VERSION, defines);
            String vs = header + ShaderPreprocessor.process(vertex, ShaderLibrary::loadSource);
            String fs = header + ShaderPreprocessor.process(fragment, ShaderLibrary::loadSource);
            program = ShaderProgram.create(key, vs, fs, attributes);
            programs.put(key, program);
            return program;
        } catch (IOException | RuntimeException e) {
            failures.put(key, String.valueOf(e.getMessage()));
            LOGGER.error("Shader desativado: {}", e.getMessage());
            return null;
        }
    }

    private static String loadSource(String path) throws IOException {
        ResourceLocation id = new ResourceLocation(MagicMod.MOD_ID, ROOT + path);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            throw new IOException("shader nao encontrado: " + id);
        }
        try (InputStream in = resource.get().open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Apaga todos os programas; eles serao recompilados quando forem usados. Chamar na thread de render. */
    public void reload() {
        for (ShaderProgram program : programs.values()) {
            program.delete();
        }
        programs.clear();
        failures.clear();
        generation++;
    }

    /** Muda a cada recarregamento; quem guarda referencias a programas compara este numero. */
    public int generation() {
        return generation;
    }

    public int programCount() {
        return programs.size();
    }

    public int failureCount() {
        return failures.size();
    }

    public List<String> failureMessages() {
        return new ArrayList<>(failures.values());
    }
}
