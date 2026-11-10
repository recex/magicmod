// Esfera de energia: orbe de plasma, estrela, escudo hexagonal ou esfera de
// sombra, conforme o estilo. Serve a projeteis, cargas, barreiras e nucleos.
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
uniform vec4 uParams0;   // x = estilo (0 plasma, 1 estrela, 2 escudo, 3 sombra), y = giro, z = nucleo, w = coroa (em raios)
uniform vec4 uParams1;   // x = densidade do padrao, y = pulso de impacto 0..1, z = opacidade do corpo

float hexLines(vec2 p) {
    vec2 s = vec2(1.0, 1.7320508);
    vec2 a = mod(p, s) - s * 0.5;
    vec2 b2 = mod(p - s * 0.5, s) - s * 0.5;
    vec2 g = dot(a, a) < dot(b2, b2) ? a : b2;
    g = abs(g);
    float d = max(dot(g, s * 0.5), g.x);
    return smoothstep(0.42, 0.5, d);
}

vec3 spin(vec3 n, float a) {
    n.xz = rot2(a) * n.xz;
    return n;
}

float surfacePattern(vec3 n, int style, float density) {
    vec3 q = spin(n, uTime * uParams0.y);
    if (style == 2) {
        vec2 sph = vec2(atan(q.z, q.x) * 3.0, asin(clamp(q.y, -1.0, 1.0)) * 4.0) * density;
        float pulse = uParams1.y;
        float ringPos = acos(clamp(dot(n, uAxis), -1.0, 1.0)) / PI;
        float ripple = pulse > 0.001 ? saturate(1.0 - abs(ringPos - pulse) * 9.0) * (1.0 - pulse) : 0.0;
        return hexLines(sph) * 0.8 + ripple * 2.0;
    }
    if (style == 1) {
        float gran = fbm3(q * 3.2 * density + vec3(0.0, uTime * 0.25, uSeed * 7.0), 4);
        float flare = ridged2(q.xy * 2.5 * density + vec2(uTime * 0.2, q.z * 2.0), 3);
        return 0.55 + 0.7 * gran + 0.6 * flare * flare;
    }
    float flow = ridged2(vec2(atan(q.z, q.x) * 1.6, q.y * 2.6) * density + vec2(uSeed * 5.0, -uTime * 0.9), 4);
    float n3 = fbm3(q * 2.4 * density + vec3(uSeed * 3.0, uTime * 0.6, 0.0), 3);
    return flow * flow * 1.3 + n3 * 0.5;
}

void main() {
    vec2 uv = screenUv();
    vec3 ro;
    vec3 rd;
    viewRay(uv, ro, rd);
    float tScene = sceneDistance(uv, ro, rd);
    int style = int(uParams0.x + 0.5);
    float density = max(uParams1.x, 0.1);

    vec3 oc = ro - uCenter;
    float bq = dot(oc, rd);
    float tMid = max(-bq, 0.0);
    float dClosest = length(oc + rd * tMid) / uRadius;
    float h = bq * bq - (dot(oc, oc) - uRadius * uRadius);

    vec3 col = vec3(0.0);
    float alpha = 0.0;

    // Coroa: brilho que vaza para fora da esfera.
    float ext = max(uParams0.w, 0.001);
    if (tMid < tScene && uParams0.w > 0.001) {
        float corona = saturate(1.0 - (dClosest - 0.85) / ext);
        if (style == 3) {
            // A esfera de sombra so brilha na borda: o miolo fica escuro.
            corona *= smoothstep(0.8, 1.02, dClosest);
        }
        float rays = 1.0;
        if (style == 1) {
            vec3 pm = oc + rd * tMid;
            float a = atan(dot(pm, uCamUp), dot(pm, uCamRight));
            rays = 0.55 + 0.9 * fbm3(vec3(cos(a) * 2.2, sin(a) * 2.2, uTime * 0.7 + uSeed), 3);
        }
        col += uColorA.rgb * pow(corona, 2.6) * rays * uParams0.z * 0.9;
    }

    if (h > 0.0) {
        float sq = sqrt(h);
        float t0 = -bq - sq;
        float t1 = -bq + sq;
        if (t1 > 0.0 && max(t0, 0.0) < tScene) {
            bool inside = t0 < 0.0;
            float tHit = inside ? t1 : t0;
            vec3 n = normalize(oc + rd * tHit);
            float ndv = abs(dot(n, rd));
            float fres = pow(1.0 - ndv, 2.5);
            float chord = saturate((min(t1, tScene) - max(t0, 0.0)) / (2.0 * uRadius));
            float soft = saturate((tScene - max(t0, 0.0)) / (uRadius * 0.25));
            float pat = surfacePattern(n, style, density);
            if (style == 2) {
                vec3 nb = normalize(oc + rd * t1);
                float back = t1 < tScene ? surfacePattern(nb, style, density) * 0.35 : 0.0;
                float e = fres * 1.3 + (pat + back) * (0.25 + 0.75 * fres) + 0.04;
                col += energyRamp(saturate(e * 0.7), uColorA.rgb) * uParams0.z * soft;
                alpha = saturate(uParams1.z * (0.15 + 0.5 * fres)) * soft;
            } else if (style == 3) {
                float swirl = fbm3(spin(n, uTime * uParams0.y) * 2.0 + vec3(0.0, uTime * 0.3, uSeed), 4);
                float body = saturate(uParams1.z * (0.55 + 0.45 * chord));
                col += uColorB.rgb * swirl * 0.6 * chord * soft;
                col += energyRamp(saturate(fres * 1.4 + pat * fres), uColorA.rgb) * uParams0.z * soft;
                alpha = body * (1.0 - fres * 0.7) * soft;
            } else if (style == 1) {
                float e = (0.45 + 0.55 * chord) * pat;
                col += energyRamp(saturate(e), uColorA.rgb) * uParams0.z * 2.2 * soft;
                col += mix(uColorA.rgb, vec3(1.0), 0.6) * pow(chord, 6.0) * uParams0.z * 2.0 * soft;
                alpha = saturate(uParams1.z) * soft;
            } else {
                float e = fres * 0.9 + pat * (0.3 + 0.7 * chord);
                col += energyRamp(saturate(e), uColorA.rgb) * (0.6 + uParams0.z) * soft;
                col += mix(uColorA.rgb, vec3(1.0), 0.75) * pow(chord, 5.0) * uParams0.z * 2.6 * soft;
                col += uColorB.rgb * fres * soft;
                alpha = saturate(uParams1.z * chord) * soft;
            }
        }
    }
    col *= uColorA.a;
    alpha *= uColorA.a;
    if (max3(col) + alpha <= 0.0005) {
        discard;
    }
    fragColor = vec4(col, alpha);
}
