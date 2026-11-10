package com.arcanemod.magicmod.engine.shader;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pre-processador dos shaders do motor: resolve {@code #include "arquivo"}
 * (cada arquivo entra uma unica vez) e monta o cabecalho com a versao do GLSL
 * e os {@code #define} de cada variante. Nao depende de OpenGL nem do
 * Minecraft, por isso e coberto por testes unitarios.
 */
public final class ShaderPreprocessor {

    /** Fornece o texto de um arquivo de shader a partir do caminho relativo a raiz dos shaders. */
    @FunctionalInterface
    public interface SourceLoader {
        String load(String path) throws IOException;
    }

    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+\"([^\"]+)\"\\s*$");
    private static final int MAX_DEPTH = 8;

    private ShaderPreprocessor() {
    }

    /** Devolve o codigo de {@code path} com todos os includes expandidos. */
    public static String process(String path, SourceLoader loader) throws IOException {
        StringBuilder out = new StringBuilder(4096);
        Set<String> seen = new HashSet<>();
        seen.add(path);
        expand(path, loader, seen, out, 0);
        return out.toString();
    }

    private static void expand(String path, SourceLoader loader, Set<String> seen, StringBuilder out, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("includes aninhados demais em " + path);
        }
        String source = loader.load(path);
        if (source == null) {
            throw new IOException("shader nao encontrado: " + path);
        }
        for (String line : source.split("\n", -1)) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            Matcher m = INCLUDE.matcher(line);
            if (m.matches()) {
                String included = m.group(1);
                if (seen.add(included)) {
                    expand(included, loader, seen, out, depth + 1);
                }
            } else if (line.trim().startsWith("#version")) {
                throw new IOException("#version nao e permitido nos arquivos (" + path + "): o motor define a versao");
            } else {
                out.append(line).append('\n');
            }
        }
    }

    /** Cabecalho: {@code #version} seguido de um {@code #define} por variante. */
    public static String header(String version, String... defines) {
        StringBuilder sb = new StringBuilder("#version ").append(version).append('\n');
        for (String define : defines) {
            if (define != null && !define.isEmpty()) {
                sb.append("#define ").append(define).append('\n');
            }
        }
        return sb.toString();
    }
}
