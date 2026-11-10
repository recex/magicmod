// Aparencia das fitas, toda procedural. Estilos:
// 0 feixe de energia, 1 rastro, 2 raio, 3 cipo (solido), 4 fluxo espectral, 5 corte.
#include "lib/common.glsl"
#include "lib/noise.glsl"
#include "lib/scene.glsl"
#include "lib/output.glsl"

uniform float uTime;

in vec2 vUv;
in vec4 vColor;
in vec4 vExtra;
in float vEyeDepth;

void main() {
    int style = int(vExtra.x + 0.5);
    float age = vExtra.y;
    float seed = vExtra.z;
    float total = max(vExtra.w, 1.0e-3);
    float u = vUv.x;
    float f = u / total;
    float v = vUv.y;
    float av = abs(v);
    vec3 rgb = vec3(0.0);
    float alpha = 0.0;
    float ends = smoothstep(0.0, 0.06, f) * smoothstep(1.0, 0.94, f);

    if (style == 0) {
        float flow = fbm2(vec2(u * 0.9 - uTime * 7.0, v * 1.5 + seed * 9.0), 3);
        float streak = ridged2(vec2(u * 0.35 - uTime * 11.0, v * 2.5 + seed * 3.0), 3);
        float core = pow(saturate(1.0 - av * 2.6), 2.0);
        float halo = pow(saturate(1.0 - av), 2.2);
        float e = core * 1.6 + halo * (0.25 + 0.9 * flow * streak);
        rgb = energyRamp(saturate(e * 0.75), vColor.rgb) * (1.0 + core * 2.5) * ends;
    } else if (style == 1) {
        float tail = pow(saturate(f), 1.6);
        float n = fbm2(vec2(u * 1.3 - uTime * 2.0, v * 2.0 + seed * 7.0), 3);
        float halo = pow(saturate(1.0 - av), 1.6);
        float e = halo * tail * (0.45 + 0.75 * n);
        rgb = energyRamp(saturate(e), vColor.rgb) * 1.6;
    } else if (style == 2) {
        float flick = 0.55 + 0.45 * hash11(floor(uTime * 28.0) + seed * 71.0);
        float core = pow(saturate(1.0 - av * 5.0), 1.5);
        float halo = pow(saturate(1.0 - av), 3.0);
        rgb = (mix(vColor.rgb, vec3(1.0), 0.8) * core * 7.0 + vColor.rgb * halo * 1.3) * flick * ends;
    } else if (style == 3) {
        // Cipo: cilindro sombreado com casca e espinhos; cresce conforme "age".
        float grown = smoothstep(f - 0.02, f + 0.02, age);
        float prof = sqrt(saturate(1.0 - v * v));
        float bark = 0.7 + 0.3 * vnoise2(vec2(u * 6.0, v * 3.0 + seed * 5.0));
        float thorn = smoothstep(0.75, 1.0, av) * step(0.6, fract(u * 2.3 + seed));
        float edge = smoothstep(1.0, 0.82, av - thorn * 0.25);
        rgb = vColor.rgb * (0.25 + 0.75 * prof) * bark;
        rgb += vColor.rgb * pow(prof, 8.0) * 0.6;
        alpha = edge * grown * vColor.a;
        rgb *= alpha;
    } else if (style == 4) {
        float n = fbm2(vec2(u * 0.8 - uTime * 3.0, v * 1.4 + seed * 13.0), 4);
        float wisp = saturate(1.0 - av * (0.9 + 0.9 * n));
        float head = smoothstep(0.0, 0.25, f) * smoothstep(1.0, 0.55, f);
        float e = pow(wisp, 1.7) * head * (0.5 + n);
        rgb = energyRamp(saturate(e * 0.85), vColor.rgb) * 1.4;
        alpha = saturate(e * 0.35) * vColor.a;
    } else {
        // Corte: crescente afiado que some rapido.
        float edge = pow(saturate(1.0 - abs(v - 0.55) * 3.5), 2.0);
        float body = saturate(0.55 - v) * saturate(v + 1.0) * 0.6;
        float sweep = smoothstep(age * 1.4 - 0.5, age * 1.4, f) * (1.0 - smoothstep(age * 1.4, age * 1.4 + 0.25, f));
        rgb = (mix(vColor.rgb, vec3(1.0), 0.7) * edge * 5.0 + vColor.rgb * body) * (0.25 + sweep) * ends;
    }
    float soft = depthFade(vEyeDepth, 0.35) * saturate((vEyeDepth - 0.1) * 5.0);
    if (max3(rgb) * soft + alpha * soft <= 0.0005) {
        discard;
    }
    fragColor = vec4(rgb * soft, alpha * soft);
}
