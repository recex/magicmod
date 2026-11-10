// Explosao volumetrica: marcha de raios por uma esfera de fogo turbulento que
// se expande, esfria do branco para a cor base e se desfaz em fumaca.
#include "lib/common.glsl"
#include "lib/noise.glsl"
#include "lib/scene.glsl"
#include "lib/output.glsl"

#ifndef VFX_STEPS
#define VFX_STEPS 24
#endif

uniform vec3 uCenter;
uniform float uRadius;
uniform float uAge;
uniform float uTime;
uniform float uSeed;
uniform vec4 uColorA;
uniform vec4 uColorB;
uniform vec4 uParams0;   // x = densidade, y = turbulencia, z = brilho, w = fumaca

float density(vec3 p, out float temp) {
    float r = length(p);
    vec3 q = p * (1.5 + uParams0.y) + vec3(uSeed * 13.1, -uTime * 0.5, uSeed * 7.7);
    // Distorcao de dominio: da o aspecto de labaredas que se enrolam.
    q += (vnoise3(q * 0.8 + 3.1) - 0.5) * 1.1;
    float disp = fbm3(q, 5);
    // Superficie "piroclastica": uma esfera deslocada por ruido, como as
    // bolhas de uma explosao real; nunca encosta na esfera limite.
    float sdf = r - (0.40 + 0.60 * disp);
    float d = saturate(-sdf * 5.0);
    // Com a idade a nuvem se rasga em fiapos, de fora para dentro.
    d *= smoothstep(uAge * 1.25 - 0.32, uAge * 1.25 + 0.12, disp * 0.6 + (1.0 - r) * 0.5);
    // No inicio tudo queima; depois esfria de fora para dentro e so as
    // fendas entre as bolhas continuam incandescentes.
    temp = saturate(1.55 - r * 0.55 + (0.5 - disp) * 1.2 - uAge * 2.3);
    return d;
}

void main() {
    vec2 uv = screenUv();
    vec3 ro;
    vec3 rd;
    viewRay(uv, ro, rd);
    vec2 hit = raySphere(ro, rd, uCenter, uRadius);
    float tScene = sceneDistance(uv, ro, rd);
    float t0 = max(hit.x, 0.0);
    float t1 = min(hit.y, tScene);
    if (hit.y < 0.0 || t1 <= t0) {
        discard;
    }

    float stepLen = (t1 - t0) / float(VFX_STEPS);
    float t = t0 + stepLen * hash21(gl_FragCoord.xy + uSeed * 91.0);
    vec3 glow = vec3(0.0);
    float trans = 1.0;
    float smoke = 0.0;
    for (int i = 0; i < VFX_STEPS; i++) {
        vec3 p = (ro + rd * t - uCenter) / uRadius;
        float temp;
        float d = density(p, temp);
        if (d > 0.002) {
            float a = 1.0 - exp(-d * stepLen / uRadius * 14.0 * uParams0.x);
            vec3 emit = energyRamp(temp, uColorA.rgb) * uParams0.z * (0.08 + temp * temp * 5.5);
            glow += trans * emit * a;
            float cool = (1.0 - temp * temp) * uParams0.w;
            smoke += trans * a * cool;
            trans *= 1.0 - a * (0.35 + cool * 0.65);
            if (trans < 0.02) break;
        }
        t += stepLen;
    }
    // Borda suave contra o terreno.
    float soft = saturate((tScene - t0) / (uRadius * 0.15));
    float alpha = saturate(smoke) * soft * uColorB.a;
    fragColor = vec4(glow * soft + uColorB.rgb * alpha, alpha) * uColorA.a;
}
