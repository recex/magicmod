package com.arcanemod.magicmod.engine.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Malha dinamica reenviada a cada desenho: posicao (3), uv (2), cor (4) e
 * extra (4) por vertice. Serve aos quadrilateros dos efeitos volumetricos e
 * as fitas (feixes, rastros, raios).
 */
public final class DynamicMesh {

    public static final int FLOATS = 13;
    public static final String[] ATTRIBUTES = {"aPosition", "aUv", "aColor", "aExtra"};

    private int vao;
    private int vbo;
    private FloatBuffer buffer = MemoryUtil.memAllocFloat(FLOATS * 6 * 64);
    private int vertices;

    private void create() {
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        int stride = FLOATS * 4;
        int[] sizes = {3, 2, 4, 4};
        int offset = 0;
        for (int i = 0; i < sizes.length; i++) {
            GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(i, sizes[i], GL11.GL_FLOAT, false, stride, offset);
            offset += sizes[i] * 4;
        }
    }

    public void begin() {
        buffer.clear();
        vertices = 0;
    }

    public void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a,
                       float e0, float e1, float e2, float e3) {
        if (buffer.remaining() < FLOATS) {
            int position = buffer.position();
            buffer = MemoryUtil.memRealloc(buffer, buffer.capacity() * 2);
            buffer.position(position);
        }
        buffer.put(x).put(y).put(z).put(u).put(v).put(r).put(g).put(b).put(a).put(e0).put(e1).put(e2).put(e3);
        vertices++;
    }

    /** Vertice so com posicao (os demais atributos zerados). */
    public void position(float x, float y, float z) {
        vertex(x, y, z, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public int vertexCount() {
        return vertices;
    }

    /** Envia os vertices acumulados e desenha como triangulos. */
    public void draw() {
        if (vertices == 0) {
            return;
        }
        if (vao == 0) {
            create();
        }
        buffer.flip();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices);
        begin();
    }

    public void delete() {
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            GL15.glDeleteBuffers(vbo);
            vao = 0;
            vbo = 0;
        }
    }
}
