package com.arcanemod.magicmod.engine.particle;

import com.arcanemod.magicmod.engine.render.FrameContext;
import com.arcanemod.magicmod.engine.shader.ShaderProgram;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Sistema de particulas na GPU. Cada particula ocupa uma instancia num buffer
 * circular e guarda apenas o estado de nascimento; o movimento e calculado no
 * vertex shader. Emitir custa uma escrita em memoria; simular nao custa nada
 * na CPU; desenhar dezenas de milhares e uma unica chamada.
 */
public final class GpuParticleSystem {

    public static final int FLOATS = 32;
    public static final String[] ATTRIBUTES = {
            "aCorner", "iPosBirth", "iVelLife", "iSizes", "iColor0", "iColor1", "iMisc", "iTarget", "iAnim"};

    private int capacity;
    private FloatBuffer cpu;
    private int vao;
    private int cornerVbo;
    private int instanceVbo;
    private boolean gpuAllocated;
    private int head;
    private int used;
    private int dirtyLo = Integer.MAX_VALUE;
    private int dirtyHi = -1;
    private boolean dirtyAll;
    private boolean anchored;
    private double originX;
    private double originY;
    private double originZ;
    private float epoch;
    private float lastDeath;
    private long emitted;

    public GpuParticleSystem(int capacity) {
        this.capacity = Math.max(64, capacity);
        this.cpu = MemoryUtil.memCallocFloat(this.capacity * FLOATS);
    }

    /** Verdadeiro se a placa suporta instancias (OpenGL 3.3 ou a extensao equivalente). */
    public static boolean isSupported() {
        GLCapabilities caps = GL.getCapabilities();
        return caps.OpenGL33 || caps.GL_ARB_instanced_arrays;
    }

    /** Troca a capacidade (mudanca de qualidade). As particulas vivas sao descartadas. */
    public void resize(int newCapacity) {
        newCapacity = Math.max(64, newCapacity);
        if (newCapacity == capacity) {
            return;
        }
        MemoryUtil.memFree(cpu);
        capacity = newCapacity;
        cpu = MemoryUtil.memCallocFloat(capacity * FLOATS);
        gpuAllocated = false;
        clear();
    }

    public void clear() {
        head = 0;
        used = 0;
        anchored = false;
        lastDeath = 0.0f;
        dirtyLo = Integer.MAX_VALUE;
        dirtyHi = -1;
        dirtyAll = false;
    }

    /** Sem particulas vivas no instante {@code time} (segundos do motor). */
    public boolean isIdle(float time) {
        return !anchored || time - epoch > lastDeath;
    }

    public int capacity() {
        return capacity;
    }

    public long emittedTotal() {
        return emitted;
    }

    /** Estimativa de particulas em uso (limite superior das vivas). */
    public int used() {
        return used;
    }

    public void emit(Particle p, float time) {
        if (isIdle(time)) {
            // Buffer vazio: reancora a origem e o relogio para manter a precisao em float.
            clear();
            anchored = true;
            originX = p.x;
            originY = p.y;
            originZ = p.z;
            epoch = time;
        }
        float birth = time - epoch + Math.max(0.0f, p.delay);
        float life = Math.max(0.02f, p.life);
        int slot = head;
        head = (head + 1) % capacity;
        used = Math.min(capacity, used + 1);
        int o = slot * FLOATS;
        cpu.put(o, (float) (p.x - originX)).put(o + 1, (float) (p.y - originY)).put(o + 2, (float) (p.z - originZ)).put(o + 3, birth);
        cpu.put(o + 4, p.vx).put(o + 5, p.vy).put(o + 6, p.vz).put(o + 7, life);
        cpu.put(o + 8, p.size0).put(o + 9, p.size1).put(o + 10, p.drag).put(o + 11, p.gravity);
        cpu.put(o + 12, p.r0).put(o + 13, p.g0).put(o + 14, p.b0).put(o + 15, p.a0);
        cpu.put(o + 16, p.r1).put(o + 17, p.g1).put(o + 18, p.b1).put(o + 19, p.a1);
        cpu.put(o + 20, p.style).put(o + 21, p.seed).put(o + 22, p.stretch).put(o + 23, p.turbulence);
        cpu.put(o + 24, (float) (p.tx - originX)).put(o + 25, (float) (p.ty - originY)).put(o + 26, (float) (p.tz - originZ)).put(o + 27, p.attract);
        cpu.put(o + 28, p.swirl).put(o + 29, p.fadeIn).put(o + 30, p.fadeOut).put(o + 31, p.flicker);
        lastDeath = Math.max(lastDeath, birth + life);
        if (dirtyHi >= 0 && slot < dirtyLo && slot != 0) {
            dirtyAll = true;
        }
        if (slot == 0 && dirtyHi >= 0) {
            dirtyAll = true;
        }
        dirtyLo = Math.min(dirtyLo, slot);
        dirtyHi = Math.max(dirtyHi, slot);
        emitted++;
    }

    private void createGpu() {
        vao = GL30.glGenVertexArrays();
        cornerVbo = GL15.glGenBuffers();
        instanceVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cornerVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f}, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        boolean core = GL.getCapabilities().OpenGL33;
        for (int i = 0; i < 8; i++) {
            GL20.glEnableVertexAttribArray(1 + i);
            GL20.glVertexAttribPointer(1 + i, 4, GL11.GL_FLOAT, false, FLOATS * 4, i * 16L);
            if (core) {
                GL33.glVertexAttribDivisor(1 + i, 1);
            } else {
                ARBInstancedArrays.glVertexAttribDivisorARB(1 + i, 1);
            }
        }
    }

    /** Envia as particulas novas e desenha todas. O programa ja deve estar em uso, com os uniforms do quadro. */
    public void draw(FrameContext ctx, ShaderProgram program) {
        if (isIdle(ctx.time) || used == 0) {
            return;
        }
        if (vao == 0) {
            createGpu();
        }
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        if (!gpuAllocated) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) capacity * FLOATS * 4L, GL15.GL_DYNAMIC_DRAW);
            gpuAllocated = true;
            dirtyAll = true;
        }
        if (dirtyAll || dirtyHi >= 0) {
            int lo = dirtyAll ? 0 : dirtyLo;
            int hi = dirtyAll ? used - 1 : dirtyHi;
            cpu.limit((hi + 1) * FLOATS).position(lo * FLOATS);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) lo * FLOATS * 4L, cpu);
            cpu.clear();
            dirtyLo = Integer.MAX_VALUE;
            dirtyHi = -1;
            dirtyAll = false;
        }
        program.set3f("uOrigin", (float) (originX - ctx.camX), (float) (originY - ctx.camY), (float) (originZ - ctx.camZ));
        program.set1f("uTime", ctx.time - epoch);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, used);
    }

    public void delete() {
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            GL15.glDeleteBuffers(cornerVbo);
            GL15.glDeleteBuffers(instanceVbo);
            vao = 0;
            gpuAllocated = false;
        }
    }
}
