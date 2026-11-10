// Cristais solidos (prisao glacial, estilhacos de terra, espinhos): um feixe
// de prismas hexagonais pontiagudos tracado por campo de distancia, com
// iluminacao propria, brilho interno e reflexos.
#include "lib/common.glsl"
#include "lib/noise.glsl"
#include "lib/scene.glsl"
#include "lib/output.glsl"

#ifndef VFX_STEPS
#define VFX_STEPS 24
#endif
#define MAX_SPIKES 9

uniform vec3 uCenter;
uniform float uRadius;
uniform vec3 uAxis;
uniform float uAge;
uniform float uTime;
uniform float uSeed;
uniform vec4 uColorA;
uniform vec4 uColorB;
uniform vec4 uParams0;   // x = crescimento 0..1, y = numero de cristais, z = brilho interno, w = abertura (0 = para cima, 1 = esfera)

vec3 gDir[MAX_SPIKES];
vec3 gT[MAX_SPIKES];
vec3 gB[MAX_SPIKES];
vec2 gDim[MAX_SPIKES];
int gCount;

float hexDist(vec2 q) {
    q = abs(q);
    return max(q.x * 0.8660254 + q.y * 0.5, q.y);
}

float map(vec3 p) {
    float d = 1.0e9;
    for (int i = 0; i < MAX_SPIKES; i++) {
        if (i >= gCount) break;
        float h = dot(p, gDir[i]);
        vec2 q = vec2(dot(p, gT[i]), dot(p, gB[i]));
        float len = gDim[i].x;
        float taper = saturate((len - h) / (len * 0.4));
        float body = hexDist(q) - gDim[i].y * taper;
        d = min(d, max(body, max(-h - gDim[i].y, h - len)));
    }
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

    gCount = int(clamp(uParams0.y, 1.0, float(MAX_SPIKES)) + 0.5);
    for (int i = 0; i < MAX_SPIKES; i++) {
        float fi = float(i);
        vec3 h3 = hash33(vec3(fi * 3.1 + 1.0, uSeed * 57.0, fi * 1.7 + 4.0));
        float a = fi * 2.399963 + uSeed * 6.0;
        float tilt = i == 0 ? 0.0 : (0.25 + 0.75 * h3.x) * (0.55 + 1.0 * uParams0.w);
        vec3 dir = normalize(vec3(cos(a) * sin(tilt), cos(tilt), sin(a) * sin(tilt)));
        gDir[i] = dir;
        basisFromAxis(dir, gT[i], gB[i]);
        float grow = saturate(uParams0.x * 1.45 - h3.y * 0.45);
        grow = 1.0 - pow(1.0 - grow, 3.0);
        float len = uRadius * (i == 0 ? 0.95 : 0.5 + 0.42 * h3.z) * grow;
        gDim[i] = vec2(max(len, 1.0e-3), uRadius * (0.1 + 0.07 * h3.x) * min(grow * 2.0, 1.0));
    }

    float t = t0;
    bool found = false;
    vec3 p = vec3(0.0);
    for (int i = 0; i < VFX_STEPS * 2; i++) {
        p = ro + rd * t - uCenter;
        float d = map(p);
        if (d < 0.004 * (1.0 + t * 0.05)) {
            found = true;
            break;
        }
        t += d * 0.75;
        if (t > t1) break;
    }
    if (!found) {
        discard;
    }
    vec2 e = vec2(0.01, 0.0);
    vec3 n = normalize(vec3(map(p + e.xyy) - map(p - e.xyy), map(p + e.yxy) - map(p - e.yxy), map(p + e.yyx) - map(p - e.yyx)));
    vec3 l = normalize(vec3(0.35, 0.85, 0.3));
    float diff = saturate(dot(n, l)) * 0.6 + 0.4 * (n.y * 0.5 + 0.5);
    float fres = pow(1.0 - saturate(dot(n, -rd)), 3.0);
    float spec = pow(saturate(dot(reflect(rd, n), l)), 40.0);
    float inner = saturate(-map(p + rd * uRadius * 0.12) / (uRadius * 0.08));
    float frost = fbm3(p * 3.0 + uSeed * 9.0, 3);
    vec3 col = uColorA.rgb * (0.22 + 0.55 * diff) * (0.75 + 0.5 * frost);
    col += uColorB.rgb * inner * uParams0.z * (0.6 + 0.4 * sin(uTime * 3.0 + p.y * 2.0));
    col += mix(uColorA.rgb, vec3(1.0), 0.7) * (fres * 1.1 + spec * 2.5);
    float alpha = saturate(0.72 + 0.28 * fres + spec) * uColorA.a;
    fragColor = vec4(col * alpha, alpha);
}
