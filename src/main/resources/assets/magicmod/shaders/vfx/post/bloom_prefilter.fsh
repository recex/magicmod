// Bloom, passo 1: extrai a luz dos efeitos (e, se pedido, os realces da cena)
// para meia resolucao, com media de Karis para nao piscar com pixels isolados.
#include "lib/common.glsl"
#include "lib/output.glsl"

uniform sampler2D uSource;
uniform sampler2D uScene;
uniform vec2 uTexel;
uniform float uSceneBloom;

in vec2 vUv;

vec3 fetch(vec2 uv) {
    vec3 c = texture(uSource, uv).rgb;
    if (uSceneBloom > 0.0) {
        vec3 s = texture(uScene, uv).rgb;
        c += s * smoothstep(0.55, 1.0, luma(s)) * uSceneBloom;
    }
    return c;
}

float karis(vec3 c) {
    return 1.0 / (1.0 + luma(c));
}

void main() {
    vec3 a = fetch(vUv + uTexel * vec2(-1.0, -1.0));
    vec3 b = fetch(vUv + uTexel * vec2(1.0, -1.0));
    vec3 c = fetch(vUv + uTexel * vec2(-1.0, 1.0));
    vec3 d = fetch(vUv + uTexel * vec2(1.0, 1.0));
    float wa = karis(a);
    float wb = karis(b);
    float wc = karis(c);
    float wd = karis(d);
    vec3 sum = (a * wa + b * wb + c * wc + d * wd) / (wa + wb + wc + wd);
    fragColor = vec4(max(sum, vec3(0.0)), 1.0);
}
