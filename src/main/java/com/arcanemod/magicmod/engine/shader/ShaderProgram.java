package com.arcanemod.magicmod.engine.shader;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

/** Programa GLSL compilado, com cache das posicoes dos uniforms. */
public final class ShaderProgram {

    /** Erro de compilacao ou de ligacao, com o log do driver. */
    public static final class CompileException extends RuntimeException {
        public CompileException(String message) {
            super(message);
        }
    }

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private final String name;
    private final int id;
    private final Object2IntOpenHashMap<String> locations = new Object2IntOpenHashMap<>();
    /** Marca usada pelo renderizador para enviar os uniforms do quadro uma so vez por passe. */
    public long frameStamp = -1L;

    private ShaderProgram(String name, int id) {
        this.name = name;
        this.id = id;
        this.locations.defaultReturnValue(Integer.MIN_VALUE);
    }

    public static ShaderProgram create(String name, String vertexSource, String fragmentSource, String[] attributes) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vertexSource, name + " [vertice]");
        int fs;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource, name + " [fragmento]");
        } catch (CompileException e) {
            GL20.glDeleteShader(vs);
            throw e;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        for (int i = 0; i < attributes.length; i++) {
            GL20.glBindAttribLocation(program, i, attributes[i]);
        }
        GL20.glLinkProgram(program);
        boolean linked = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE;
        String log = linked ? "" : GL20.glGetProgramInfoLog(program, 8192);
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (!linked) {
            GL20.glDeleteProgram(program);
            throw new CompileException("ligacao de " + name + ": " + log.trim());
        }
        return new ShaderProgram(name, program);
    }

    private static int compile(int type, String source, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            String log = GL20.glGetShaderInfoLog(shader, 8192);
            GL20.glDeleteShader(shader);
            throw new CompileException("compilacao de " + label + ": " + log.trim());
        }
        return shader;
    }

    public String name() {
        return name;
    }

    public void use() {
        GL20.glUseProgram(id);
    }

    public int location(String uniform) {
        int loc = locations.getInt(uniform);
        if (loc == Integer.MIN_VALUE) {
            loc = GL20.glGetUniformLocation(id, uniform);
            locations.put(uniform, loc);
        }
        return loc;
    }

    public void set1i(String uniform, int v) {
        int loc = location(uniform);
        if (loc >= 0) {
            GL20.glUniform1i(loc, v);
        }
    }

    public void set1f(String uniform, float v) {
        int loc = location(uniform);
        if (loc >= 0) {
            GL20.glUniform1f(loc, v);
        }
    }

    public void set2f(String uniform, float x, float y) {
        int loc = location(uniform);
        if (loc >= 0) {
            GL20.glUniform2f(loc, x, y);
        }
    }

    public void set3f(String uniform, float x, float y, float z) {
        int loc = location(uniform);
        if (loc >= 0) {
            GL20.glUniform3f(loc, x, y, z);
        }
    }

    public void set3f(String uniform, Vector3f v) {
        set3f(uniform, v.x, v.y, v.z);
    }

    public void set4f(String uniform, float x, float y, float z, float w) {
        int loc = location(uniform);
        if (loc >= 0) {
            GL20.glUniform4f(loc, x, y, z, w);
        }
    }

    public void set4f(String uniform, float[] v) {
        set4f(uniform, v[0], v[1], v[2], v[3]);
    }

    /** Envia um vetor de vec4 ({@code count} elementos a partir de {@code data[0]}). */
    public void set4fv(String uniform, FloatBuffer data) {
        int loc = location(uniform);
        if (loc >= 0) {
            GL20.glUniform4fv(loc, data);
        }
    }

    public void setMatrix(String uniform, Matrix4f m) {
        int loc = location(uniform);
        if (loc >= 0) {
            m.get(MATRIX);
            GL20.glUniformMatrix4fv(loc, false, MATRIX);
        }
    }

    public void delete() {
        GL20.glDeleteProgram(id);
    }
}
