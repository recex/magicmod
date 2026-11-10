// Composicao final: junta a cena do jogo (LDR) com a luz dos efeitos (HDR),
// aplicando distorcao espacial, aberracao cromatica, desfoque radial e de
// movimento, luzes dinamicas por profundidade, bloom e gradacao de cor.
#include "lib/common.glsl"
#include "lib/output.glsl"

#define MAX_LIGHTS 8

uniform sampler2D uScene;
uniform sampler2D uVfx;
uniform sampler2D uBloom;
uniform sampler2D uDistort;
uniform sampler2D uSceneDepth;
uniform mat4 uInvViewProj;
uniform mat4 uPrevViewProj;
uniform vec3 uCamDelta;
uniform vec2 uAspect;
uniform float uBloomStrength;
uniform float uDistortStrength;
uniform float uChroma;
uniform vec4 uRadialBlur;
uniform float uMotionBlur;
uniform vec4 uFlash;
uniform vec4 uGrade;
uniform vec4 uTint;
uniform vec4 uImpact;
uniform float uLetterbox;
uniform float uFrameSeed;
uniform int uLightCount;
uniform vec4 uLightPos[MAX_LIGHTS];
uniform vec4 uLightColor[MAX_LIGHTS];

in vec2 vUv;

vec3 scenePos(vec2 uv, float depth) {
    vec4 p = uInvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// Curva de brilho dos efeitos: ombro suave e nucleos muito quentes puxando
// para o branco, como em pelicula superexposta.
vec3 tonemapGlow(vec3 c) {
    float peak = max3(c);
    if (peak <= 1.0e-5) {
        return vec3(0.0);
    }
    vec3 ratio = c / peak;
    float mapped = 1.0 - exp(-peak * 1.15);
    ratio = mix(ratio, vec3(1.0), smoothstep(1.2, 9.0, peak) * 0.9);
    return ratio * mapped;
}

vec3 sampleScene(vec2 uv, vec2 offset) {
    // Refracao prismatica: cada canal desvia um pouco diferente.
    vec2 dir = uv - 0.5;
    vec2 fringe = dir * dot(dir, dir) * uChroma * 4.0 + offset * 0.22;
    vec2 base = uv + offset;
    float r = texture(uScene, base + fringe).r;
    float g = texture(uScene, base).g;
    float b = texture(uScene, base - fringe).b;
    return vec3(r, g, b);
}

void main() {
    vec2 uv = vUv;
    vec2 offset = texture(uDistort, uv).xy * uDistortStrength;
    float depth = texture(uSceneDepth, uv).r;

    vec3 col = sampleScene(uv, offset);

    // Desfoque radial (zoom) em direcao a um ponto da tela: momentos de impacto.
    if (uRadialBlur.z > 0.001) {
        vec2 toCenter = (uRadialBlur.xy - uv) * uRadialBlur.z;
        float jitter = hash21(gl_FragCoord.xy + uFrameSeed);
        vec3 acc = col;
        for (int i = 1; i < 8; i++) {
            acc += sampleScene(uv + toCenter * ((float(i) + jitter) / 8.0), offset);
        }
        col = acc / 8.0;
    }

    // Desfoque de movimento da camera por reprojecao de profundidade.
    if (uMotionBlur > 0.001 && depth < 1.0) {
        vec3 pos = scenePos(uv, depth);
        vec4 prev = uPrevViewProj * vec4(pos + uCamDelta, 1.0);
        vec2 velocity = (uv - (prev.xy / prev.w * 0.5 + 0.5)) * uMotionBlur;
        float len = length(velocity);
        if (len > 0.0005) {
            velocity *= min(len, 0.06) / len;
            float jitter = hash21(gl_FragCoord.xy * 1.37 + uFrameSeed);
            vec3 acc = col;
            for (int i = 1; i < 6; i++) {
                acc += texture(uScene, uv + offset - velocity * ((float(i) + jitter) / 6.0)).rgb;
            }
            col = acc / 6.0;
        }
    }

    // Luzes dinamicas em espaco de tela: reconstroi posicao e normal a partir
    // da profundidade e ilumina o terreno em volta do efeito.
    if (uLightCount > 0 && depth < 0.99999) {
        vec3 pos = scenePos(uv, depth);
        vec3 nrm = normalize(cross(dFdx(pos), dFdy(pos)));
        if (dot(nrm, pos) > 0.0) {
            nrm = -nrm;
        }
        vec3 lit = vec3(0.0);
        for (int i = 0; i < MAX_LIGHTS; i++) {
            if (i >= uLightCount) break;
            vec3 toLight = uLightPos[i].xyz - pos;
            float dist = length(toLight);
            float x = dist / uLightPos[i].w;
            float att = saturate(1.0 - x * x);
            att *= att;
            float ndl = saturate(dot(nrm, toLight / max(dist, 1.0e-3))) * 0.8 + 0.2;
            lit += uLightColor[i].rgb * (att * ndl);
        }
        col += (col * 1.6 + 0.035) * lit;
    }

    // Gradacao da cena (antes da luz dos efeitos, para nao lavar o brilho).
    col *= uGrade.x;
    col = mix(vec3(luma(col)), col, uGrade.y);
    col = (col - 0.5) * uGrade.z + 0.5;
    col = mix(col, col * uTint.rgb, uTint.a);
    col = max(col, vec3(0.0));

    // Luz dos efeitos + bloom.
    vec4 fx = texture(uVfx, uv);
    vec3 bloom = texture(uBloom, uv).rgb;
    vec3 glow = tonemapGlow(fx.rgb + bloom * uBloomStrength);
    col = col * (1.0 - saturate(fx.a));
    col = col + glow * (1.0 - col * 0.55);

    // Quadro de impacto: silhueta em dois tons por um instante.
    if (uImpact.x > 0.001) {
        float l = luma(col);
        vec3 two = mix(vec3(0.02), mix(uImpact.yzw, vec3(1.0), 0.6), smoothstep(0.32, 0.4, l));
        col = mix(col, two, uImpact.x);
    }

    // Clarao e vinheta.
    col += uFlash.rgb * uFlash.a;
    vec2 q = (uv - 0.5) * uAspect;
    col *= 1.0 - uGrade.w * smoothstep(0.25, 0.95, length(q) * 1.15);

    // Barras de cinema.
    if (uLetterbox > 0.001) {
        float bar = uLetterbox * 0.5;
        float edge = smoothstep(bar - 0.002, bar, uv.y) * smoothstep(bar - 0.002, bar, 1.0 - uv.y);
        col *= edge;
    }

    // Dither para eliminar faixas nos degrades suaves do brilho.
    col += (hash21(gl_FragCoord.xy + uFrameSeed * 7.0) - 0.5) / 255.0;
    fragColor = vec4(saturate3(col), 1.0);
}
