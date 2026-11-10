// Biblioteca comum do motor de VFX: constantes, hashes e utilitarios.
// Escrita no subconjunto comum de GLSL 1.50 (desktop) e GLSL ES 3.00.
#ifdef GL_ES
precision highp float;
precision highp int;
precision highp sampler2D;
#endif

const float PI = 3.14159265358979;
const float TAU = 6.28318530717959;

float saturate(float v) { return clamp(v, 0.0, 1.0); }
vec3 saturate3(vec3 v) { return clamp(v, 0.0, 1.0); }
float remap01(float v, float lo, float hi) { return clamp((v - lo) / (hi - lo), 0.0, 1.0); }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float max3(vec3 c) { return max(c.r, max(c.g, c.b)); }

mat2 rot2(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, s, -s, c);
}

// Hashes sem operacoes de bit (Dave Hoskins): estaveis entre GPUs.
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float hash31(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

// Base ortonormal a partir de um eixo (para discos, portais e planos).
void basisFromAxis(vec3 n, out vec3 t, out vec3 b) {
    vec3 up = abs(n.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    t = normalize(cross(up, n));
    b = cross(n, t);
}

// Rampa de "energia": cauda escura e saturada -> cor base -> nucleo branco
// superexposto. t em [0,1]; o resultado e HDR (pode passar de 1).
vec3 energyRamp(float t, vec3 base) {
    t = saturate(t);
    vec3 deep = base * base * 0.35;
    vec3 hot = mix(base, vec3(1.0), 0.75) * 2.6;
    vec3 c = mix(deep, base * 1.25, smoothstep(0.0, 0.45, t));
    return mix(c, hot, smoothstep(0.45, 1.0, t)) * t;
}
