// Marcas projetadas no terreno: reconstroi a posicao de cada pixel do cenario
// pela profundidade e desenha o padrao em cima de blocos, degraus e relevo.
// Estilos: 0 circulo runico, 1 queimadura, 2 geada, 3 fissuras, 4 poca de
// sombra, 5 alvo (aviso de impacto).
#include "lib/common.glsl"
#include "lib/noise.glsl"
#include "lib/scene.glsl"
#include "lib/output.glsl"

uniform vec3 uCenter;
uniform float uRadius;
uniform vec3 uAxis;
uniform float uAge;
uniform float uTime;
uniform float uSeed;
uniform vec4 uColorA;
uniform vec4 uColorB;
uniform vec4 uParams0;   // x = estilo, y = rotacao (rad), z = brilho, w = altura de projecao (blocos)
uniform vec4 uParams1;   // x = progresso do desenho (0..1)

float lineAt(float v, float center, float width) {
    return saturate(1.0 - abs(v - center) / width);
}

// Raio de um poligono regular de n lados na direcao "a".
float polygon(float a, float n) {
    float seg = TAU / n;
    return cos(PI / n) / cos(mod(a, seg) - PI / n);
}

void main() {
    vec2 uv = screenUv();
    float depth = texture(uSceneDepth, uv).r;
    if (depth >= 1.0) {
        discard;
    }
    vec3 p = scenePosition(uv, depth) - uCenter;
    float hgt = max(uParams0.w, 0.25);
    vec2 q = p.xz / uRadius;
    float r = length(q);
    if (abs(p.y) > hgt || r > 1.0) {
        discard;
    }
    vec3 nrm = normalize(cross(dFdx(p), dFdy(p)));
    float mask = smoothstep(0.3, 0.75, abs(nrm.y)) * (1.0 - smoothstep(hgt * 0.6, hgt, abs(p.y)));
    int style = int(uParams0.x + 0.5);
    float ang = atan(q.y, q.x);
    float fade = pow(saturate(1.0 - uAge), 1.2);
    vec3 col = vec3(0.0);
    float alpha = 0.0;

    if (style == 0) {
        float rot = uParams0.y;
        float lines = lineAt(r, 0.95, 0.018) + lineAt(r, 0.73, 0.014) + lineAt(r, 0.33, 0.012);
        float glyphs = 0.0;
        if (r > 0.76 && r < 0.92) {
            float cells = 18.0;
            float ca = (ang + rot + PI) / TAU * cells;
            float cell = floor(ca);
            vec2 g = vec2(fract(ca), (r - 0.76) / 0.16);
            vec2 gi = floor(g * vec2(4.0, 5.0));
            float bit = step(0.48, hash21(gi + cell * 17.0 + floor(uSeed * 31.0)));
            float margin = step(0.12, g.x) * step(g.x, 0.88) * step(0.1, g.y) * step(g.y, 0.9);
            glyphs = bit * margin;
        }
        float a2 = ang - rot * 1.7;
        float star = lineAt(r, polygon(a2, 3.0) * 0.66, 0.016) + lineAt(r, polygon(a2 + PI, 3.0) * 0.66, 0.016);
        star *= step(r, 0.73);
        float inner = lineAt(r, polygon(a2 * 1.0 + 0.4, 6.0) * 0.3, 0.012);
        float reveal = smoothstep(0.0, 0.08, uParams1.x - (ang + PI) / TAU * 0.92);
        float e = (lines + glyphs * 0.9 + star + inner) * reveal;
        float pulse = 0.75 + 0.25 * sin(uTime * 5.0 - r * 9.0);
        col = (uColorA.rgb * e * 2.2 + mix(uColorA.rgb, vec3(1.0), 0.6) * e * e * 1.2) * pulse * uParams0.z;
        col += uColorA.rgb * (1.0 - smoothstep(0.0, 1.0, r)) * 0.12 * reveal * uParams0.z;
    } else if (style == 1) {
        float n = fbm2(q * 3.5 + uSeed * 19.0, 4);
        float burn = 1.0 - smoothstep(0.45, 1.0, r + (n - 0.5) * 0.45);
        float veins = pow(ridged2(q * 4.5 + uSeed * 7.0, 4), 5.0);
        alpha = burn * 0.8 * saturate(1.0 - uAge * uAge);
        col = energyRamp(saturate(veins * 1.6 * (1.0 - r)), uColorA.rgb) * pow(saturate(1.0 - uAge * 1.6), 2.0) * uParams0.z * 2.5;
    } else if (style == 2) {
        vec3 c = cellular2(q * 7.0 + uSeed * 13.0);
        float n = fbm2(q * 4.0 + uSeed * 3.0, 3);
        float grow = smoothstep(0.0, 0.1, uParams1.x * 1.1 - r - (n - 0.5) * 0.3);
        float crystal = smoothstep(0.1, 0.0, c.y) * 0.8 + c.z * 0.25;
        float sparkle = step(0.985, hash21(floor(q * 90.0) + floor(uTime * 6.0)));
        alpha = grow * (0.4 + 0.3 * crystal) * fade;
        col = uColorA.rgb * alpha * (0.6 + 0.6 * crystal) + vec3(1.0) * sparkle * grow * fade * 1.5 * uParams0.z;
        col += uColorA.rgb * crystal * grow * fade * 0.35 * uParams0.z;
    } else if (style == 3) {
        vec3 c = cellular2(q * 4.0 + uSeed * 23.0);
        float reach = smoothstep(0.0, 0.15, uParams1.x * 1.15 - r);
        float crack = smoothstep(0.07, 0.0, c.y) * reach * (1.0 - smoothstep(0.7, 1.0, r));
        alpha = crack * 0.5 * fade;
        col = energyRamp(saturate(crack * (1.2 - r)), uColorA.rgb) * fade * uParams0.z * 3.0;
    } else if (style == 4) {
        float sw = ang + (1.0 - r) * 5.0 + uTime * 1.2;
        float n = fbm3(vec3(cos(sw) * r * 3.0, sin(sw) * r * 3.0, uTime * 0.3 + uSeed), 4);
        float pool = 1.0 - smoothstep(0.7, 1.0, r + (n - 0.5) * 0.25);
        alpha = pool * 0.88 * uParams1.x * fade;
        float rim = lineAt(r + (n - 0.5) * 0.25, 0.8, 0.12);
        col = uColorB.rgb * n * alpha * 0.5 + energyRamp(saturate(rim * 0.8 + n * n * (1.0 - r) * 0.7), uColorA.rgb) * uParams0.z * uParams1.x * fade;
    } else {
        float ring = lineAt(r, 0.94, 0.03) + lineAt(r, fract(1.0 - uTime * 0.9) * 0.9, 0.025) * 0.7;
        float ticks = step(0.75, fract((ang + uTime * 1.5) / TAU * 12.0)) * lineAt(r, 0.84, 0.06);
        float dot_ = 1.0 - smoothstep(0.03, 0.06, r);
        float e = ring + ticks + dot_;
        col = uColorA.rgb * e * 2.0 * uParams0.z * (0.7 + 0.3 * sin(uTime * 14.0));
    }
    float env = uColorA.a * mask;
    if (max3(col) * env + alpha * env <= 0.0005) {
        discard;
    }
    fragColor = vec4(col * env, alpha * env);
}
