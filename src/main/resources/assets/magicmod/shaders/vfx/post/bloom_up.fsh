// Bloom, passo 3: ampliacao com filtro tenda 3x3, somada ao nivel maior.
#include "lib/common.glsl"
#include "lib/output.glsl"

uniform sampler2D uSource;
uniform vec2 uTexel;
uniform float uWeight;

in vec2 vUv;

void main() {
    vec2 t = uTexel;
    vec3 sum = texture(uSource, vUv).rgb * 4.0;
    sum += texture(uSource, vUv + t * vec2(-1.0, 0.0)).rgb * 2.0;
    sum += texture(uSource, vUv + t * vec2(1.0, 0.0)).rgb * 2.0;
    sum += texture(uSource, vUv + t * vec2(0.0, -1.0)).rgb * 2.0;
    sum += texture(uSource, vUv + t * vec2(0.0, 1.0)).rgb * 2.0;
    sum += texture(uSource, vUv + t * vec2(-1.0, -1.0)).rgb;
    sum += texture(uSource, vUv + t * vec2(1.0, -1.0)).rgb;
    sum += texture(uSource, vUv + t * vec2(-1.0, 1.0)).rgb;
    sum += texture(uSource, vUv + t * vec2(1.0, 1.0)).rgb;
    fragColor = vec4(sum * (uWeight / 16.0), 1.0);
}
