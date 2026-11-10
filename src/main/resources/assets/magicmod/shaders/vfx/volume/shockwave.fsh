// Onda de choque: casca esferica - ou achatada em anel rente ao chao - que se
// expande. No passe de distorcao ela dobra a imagem do cenario como uma lente.
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
uniform vec4 uParams0;   // x = achatamento (1 esfera, 0.1 anel), y = espessura relativa, z = brilho, w = distorcao

// Trecho do raio dentro da casca entre os raios r0 e r1, ate tMax.
float shellPath(vec3 ro, vec3 rd, float r0, float r1, float tMax) {
    float b = dot(ro, rd);
    float c = dot(ro, ro);
    float h1 = b * b - (c - r1 * r1);
    if (h1 <= 0.0) {
        return 0.0;
    }
    h1 = sqrt(h1);
    float tA = -b - h1;
    float tD = -b + h1;
    float h0 = b * b - (c - r0 * r0);
    if (h0 > 0.0) {
        h0 = sqrt(h0);
        return max(0.0, min(-b - h0, tMax) - max(tA, 0.0)) + max(0.0, min(tD, tMax) - max(-b + h0, 0.0));
    }
    return max(0.0, min(tD, tMax) - max(tA, 0.0));
}

void main() {
    vec2 uv = screenUv();
    vec3 ro;
    vec3 rd;
    viewRay(uv, ro, rd);
    float tScene = sceneDistance(uv, ro, rd);

    vec3 t;
    vec3 b;
    basisFromAxis(uAxis, t, b);
    float flat_ = clamp(uParams0.x, 0.02, 1.0);
    vec3 oc = ro - uCenter;
    vec3 so = vec3(dot(oc, t), dot(oc, uAxis) / flat_, dot(oc, b));
    vec3 sd = vec3(dot(rd, t), dot(rd, uAxis) / flat_, dot(rd, b));
    float sl = length(sd);
    sd /= sl;

    float w = uRadius * clamp(uParams0.y, 0.01, 0.9);
    float path = shellPath(so, sd, uRadius - w, uRadius, tScene * sl);
    float e = saturate(path / (2.0 * sqrt(2.0 * uRadius * w)));
    if (e <= 0.0005) {
        discard;
    }
    float tm = max(-dot(so, sd), 0.0);
    vec3 radial = so + sd * tm;
    float ang = atan(radial.z, radial.x);
    float fade = pow(saturate(1.0 - uAge), 1.5);
    float streak = 0.65 + 0.7 * fbm3(vec3(cos(ang) * 3.0, sin(ang) * 3.0, uSeed * 9.0 + uAge * 2.0), 3);

#ifdef DISTORT_PASS
    vec3 radialWorld = t * radial.x + uAxis * (radial.y * flat_) + b * radial.z;
    vec2 n2 = vec2(dot(radialWorld, uCamRight), dot(radialWorld, uCamUp));
    float len = length(n2);
    n2 = len > 1.0e-5 ? n2 / len : vec2(0.0);
    float mag = e * e * fade * uParams0.w * 0.06;
    fragColor = vec4(-n2 * mag * vec2(uScreenSize.y / uScreenSize.x, 1.0), 0.0, 0.0);
#else
    float glow = pow(e, 1.6) * streak * fade * uParams0.z;
    vec3 col = energyRamp(saturate(glow), uColorA.rgb) * (1.0 + 2.0 * e * e) + uColorB.rgb * e * fade * 0.35;
    fragColor = vec4(col * uColorA.a, 0.0);
#endif
}
