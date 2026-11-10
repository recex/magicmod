// Cena falsa (so para o laboratorio): planicie noturna com alguns blocos.
#include "lib/common.glsl"
#include "lib/noise.glsl"
#include "lib/output.glsl"
uniform mat4 uViewProj;
uniform mat4 uInvViewProj;
uniform vec3 uCamPos;
uniform float uDay;
in vec2 vUv;

float box(vec3 ro, vec3 rd, vec3 bmin, vec3 bmax, out vec3 n) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = (bmin - ro) * inv;
    vec3 t1 = (bmax - ro) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    float tn = max(max(tmin.x, tmin.y), tmin.z);
    float tf = min(min(tmax.x, tmax.y), tmax.z);
    if (tn > tf || tf < 0.0) return -1.0;
    n = -sign(rd) * step(tmin.yzx, tmin.xyz) * step(tmin.zxy, tmin.xyz);
    return tn;
}

void main() {
    vec4 n4 = uInvViewProj * vec4(vUv * 2.0 - 1.0, -1.0, 1.0);
    vec4 f4 = uInvViewProj * vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
    vec3 roRel = n4.xyz / n4.w;
    vec3 rd = normalize(f4.xyz / f4.w - roRel);
    vec3 ro = roRel + uCamPos;
    float t = 1.0e9;
    vec3 nrm = vec3(0.0, 1.0, 0.0);
    vec3 alb = vec3(0.0);
    if (rd.y < 0.0) {
        t = -ro.y / rd.y;
        vec3 p = ro + rd * t;
        vec2 cell = floor(p.xz);
        float v = hash21(cell);
        vec2 px = floor(p.xz * 16.0);
        alb = mix(vec3(0.30, 0.48, 0.20), vec3(0.36, 0.56, 0.24), v) * (0.8 + 0.4 * hash21(px));
    }
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        vec3 c = vec3(-7.0 + fi * 2.9 + 2.0 * hash11(fi + 3.0), 0.0, -9.0 - 5.0 * hash11(fi * 7.0 + 1.0) - mod(fi, 2.0) * 4.0);
        c.xz = floor(c.xz);
        float h = 1.0 + floor(hash11(fi * 3.1) * 3.0);
        vec3 bn;
        float tb = box(ro, rd, c, c + vec3(1.0, h, 1.0), bn);
        if (tb > 0.0 && tb < t) {
            t = tb;
            nrm = bn;
            vec3 p = ro + rd * tb;
            vec3 px = floor(p * 16.0 + bn * 0.5);
            alb = vec3(0.45, 0.45, 0.47) * (0.75 + 0.35 * hash31(px));
        }
    }
    vec3 col;
    float depth = 1.0;
    vec3 skyTop = mix(vec3(0.01, 0.012, 0.03), vec3(0.35, 0.55, 0.95), uDay);
    vec3 skyHor = mix(vec3(0.05, 0.04, 0.09), vec3(0.75, 0.85, 1.0), uDay);
    vec3 sky = mix(skyHor, skyTop, saturate(rd.y * 2.2));
    float star = step(0.9985, hash21(floor(rd.xy / max(rd.z * rd.z + 0.2, 0.2) * 220.0)));
    sky += star * (1.0 - uDay) * 0.6 * step(0.0, rd.y);
    if (t < 1.0e8) {
        vec3 p = ro + rd * t;
        vec3 sun = normalize(vec3(0.4, 0.8, 0.3));
        float light = mix(0.16, 1.0, uDay) * (0.45 + 0.55 * saturate(dot(nrm, sun)));
        col = alb * light;
        float fog = 1.0 - exp(-t * 0.012);
        col = mix(col, skyHor, fog);
        vec4 clip = uViewProj * vec4(p - uCamPos, 1.0);
        depth = clip.z / clip.w * 0.5 + 0.5;
    } else {
        col = sky;
    }
    gl_FragDepth = depth;
    fragColor = vec4(col, 1.0);
}
