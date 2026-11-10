// Singularidade: horizonte de eventos, anel de fotons e disco de acrecao em
// rotacao diferencial. No passe de distorcao curva a luz do cenario em volta.
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
uniform vec4 uParams0;   // x = raio externo do disco (em raios), y = rotacao, z = brilho do disco, w = forca da lente

void main() {
    vec2 uv = screenUv();
    vec3 ro;
    vec3 rd;
    viewRay(uv, ro, rd);
    float tScene = sceneDistance(uv, ro, rd);
    vec3 oc = ro - uCenter;
    float bq = dot(oc, rd);
    float tMid = max(-bq, 0.0);
    vec3 perp = oc + rd * tMid;
    float d = length(perp) / uRadius;
    float outer = max(uParams0.x, 1.5);
    if (tMid > tScene) {
        discard;
    }

#ifdef DISTORT_PASS
    vec2 n2 = vec2(dot(perp, uCamRight), dot(perp, uCamUp));
    float len = length(n2);
    n2 = len > 1.0e-5 ? n2 / len : vec2(0.0);
    float angular = uRadius / max(length(oc), uRadius);
    float fall = saturate(1.0 - d / (outer * 1.6));
    float mag = uParams0.w * angular * fall * fall / max(d, 0.75);
    fragColor = vec4(-n2 * mag * 0.9 * vec2(uScreenSize.y / uScreenSize.x, 1.0), 0.0, 0.0);
#else
    vec3 col = vec3(0.0);
    float alpha = 0.0;
    float h = bq * bq - (dot(oc, oc) - uRadius * uRadius);
    float tHorizon = 1.0e9;
    if (h > 0.0) {
        tHorizon = -bq - sqrt(h);
        alpha = smoothstep(1.0, 0.94, d);
    }
    // Anel de fotons e halo.
    float ring = exp(-pow((d - 1.12) * 11.0, 2.0));
    col += mix(uColorA.rgb, vec3(1.0), 0.7) * ring * 3.0 * uParams0.z;
    col += uColorA.rgb * 0.35 / (1.0 + d * d * 1.5) * saturate(1.0 - d / (outer * 1.5)) * (1.0 - alpha);

    // Disco de acrecao.
    vec3 t;
    vec3 b;
    basisFromAxis(uAxis, t, b);
    float dn = dot(rd, uAxis);
    if (abs(dn) > 1.0e-4) {
        float tp = -dot(oc, uAxis) / dn;
        if (tp > 0.0 && tp < tScene && tp < tHorizon) {
            vec3 p = oc + rd * tp;
            float r = length(p) / uRadius;
            if (r > 1.25 && r < outer) {
                float a = atan(dot(p, b), dot(p, t));
                float sw = a + uTime * uParams0.y * 2.5 / pow(r, 1.5) + log(r) * 2.2;
                float n = fbm3(vec3(cos(sw) * r * 1.4, sin(sw) * r * 1.4, r * 0.8 + uSeed * 5.0), 4);
                float streak = ridged2(vec2(sw * 2.0, r * 3.0), 3);
                float prof = smoothstep(1.25, 1.7, r) * pow(saturate(1.0 - (r - 1.25) / (outer - 1.25)), 1.4);
                vec3 tang = normalize(t * (-sin(a)) + b * cos(a));
                float doppler = 1.0 + 0.55 * dot(tang, rd);
                float e = prof * (0.35 + 0.9 * n * (0.5 + streak)) * doppler;
                col += energyRamp(saturate(e), uColorA.rgb) * uParams0.z * 2.4;
                col += uColorB.rgb * prof * n * 0.5;
                alpha = max(alpha, saturate(prof * 0.55));
            }
        }
    }
    float env = uColorA.a;
    if (max3(col) * env + alpha * env <= 0.0005) {
        discard;
    }
    fragColor = vec4(col * env, alpha * env);
#endif
}
