// Coluna de energia: pilar de luz, feixe vindo do ceu ou tornado. Integra o
// brilho de forma analitica (ponto mais proximo entre o raio e o eixo).
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
uniform vec4 uParams0;   // x = altura, y = velocidade do fluxo, z = espiral, w = brilho
uniform vec4 uParams1;   // x = estilo (0 pilar, 1 tornado), y = nucleo (0..1), z = opacidade (tornado)

void main() {
    vec2 uv = screenUv();
    vec3 ro;
    vec3 rd;
    viewRay(uv, ro, rd);
    float tScene = sceneDistance(uv, ro, rd);
    float height = max(uParams0.x, 0.1);
    int style = int(uParams1.x + 0.5);

    vec3 w0 = ro - uCenter;
    float bb = dot(rd, uAxis);
    float dd = dot(rd, w0);
    float ee = dot(uAxis, w0);
    float denom = max(1.0 - bb * bb, 1.0e-4);
    float sc = clamp((ee - bb * dd) / denom, 0.0, height);
    float tc = clamp(dot(uCenter + uAxis * sc - ro, rd), 0.0, tScene);
    vec3 p = ro + rd * tc;
    sc = clamp(dot(p - uCenter, uAxis), 0.0, height);
    vec3 q = p - (uCenter + uAxis * sc);
    float hf = sc / height;
    float radius = uRadius * (style == 1 ? 0.22 + 0.78 * pow(hf, 0.8) : 1.0);
    float dist = length(q) / radius;
    if (dist > 2.2) {
        discard;
    }
    vec3 t;
    vec3 b;
    basisFromAxis(uAxis, t, b);
    float ang = atan(dot(q, b), dot(q, t)) + sc * uParams0.z + uTime * (style == 1 ? 5.0 : 0.6);
    float flow = fbm3(vec3(cos(ang) * 1.6, sin(ang) * 1.6, sc * 0.55 - uTime * uParams0.y), 4);
    float streak = ridged2(vec2(ang * 1.5, sc * 0.35 - uTime * uParams0.y * 1.7), 3);
    float thick = min(1.0 / sqrt(denom), 3.0);
    float top = 1.0 - smoothstep(0.55, 1.0, hf);
    float base = 1.0 + 1.5 * exp(-sc * 1.2);

    vec3 col;
    float alpha = 0.0;
    if (style == 1) {
        // Tornado: funil oco de poeira girando, com veios de energia.
        float shell = exp(-pow((dist - 0.72) * 2.4, 2.0)) + 0.3 * exp(-dist * dist * 2.5);
        float body = shell * (0.3 + 0.95 * flow * (0.45 + streak));
        alpha = saturate(body * uParams1.z) * top;
        col = uColorB.rgb * alpha + energyRamp(saturate(shell * streak * streak * 0.75), uColorA.rgb) * uParams0.w * top;
    } else {
        float halo = exp(-dist * dist * 1.8) * (0.3 + 0.9 * flow * streak);
        float core = exp(-dist * dist / max(uParams1.y * uParams1.y, 0.003));
        float e = (halo * 0.8 + core * 1.3) * top * base * thick * 0.6;
        col = energyRamp(saturate(e), uColorA.rgb) * uParams0.w * (1.0 + core * 2.0) + uColorB.rgb * halo * top * 0.4;
    }
    col *= uColorA.a;
    alpha *= uColorA.a;
    if (max3(col) + alpha <= 0.0005) {
        discard;
    }
    fragColor = vec4(col, alpha);
}
