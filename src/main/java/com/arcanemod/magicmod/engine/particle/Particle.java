package com.arcanemod.magicmod.engine.particle;

/**
 * Estado de nascimento de uma particula. Objeto reutilizavel: preencha os
 * campos e entregue a {@link GpuParticleSystem#emit}; nada e guardado.
 */
public final class Particle {
    public double x;
    public double y;
    public double z;
    public float vx;
    public float vy;
    public float vz;
    public float life = 1.0f;
    public float size0 = 0.1f;
    public float size1 = 0.03f;
    public float drag;
    public float gravity;
    public float r0 = 1.0f;
    public float g0 = 1.0f;
    public float b0 = 1.0f;
    public float a0;
    public float r1 = 1.0f;
    public float g1 = 1.0f;
    public float b1 = 1.0f;
    public float a1;
    public float style;
    public float seed;
    public float stretch;
    public float turbulence;
    public double tx;
    public double ty;
    public double tz;
    public float attract;
    public float swirl;
    public float fadeIn;
    public float fadeOut = 0.6f;
    public float flicker;
    /** Segundos ate nascer (permite espalhar uma rajada no tempo). */
    public float delay;
}
