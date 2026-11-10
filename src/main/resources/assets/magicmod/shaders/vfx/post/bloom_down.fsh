// Bloom, passo 2: reducao de 13 amostras (filtro usado em jogos AAA), que
// espalha a luz sem aliasing nem tremulacao.
#include "lib/common.glsl"
#include "lib/output.glsl"

uniform sampler2D uSource;
uniform vec2 uTexel;

in vec2 vUv;

void main() {
    vec2 t = uTexel;
    vec3 a = texture(uSource, vUv + t * vec2(-2.0, 2.0)).rgb;
    vec3 b = texture(uSource, vUv + t * vec2(0.0, 2.0)).rgb;
    vec3 c = texture(uSource, vUv + t * vec2(2.0, 2.0)).rgb;
    vec3 d = texture(uSource, vUv + t * vec2(-2.0, 0.0)).rgb;
    vec3 e = texture(uSource, vUv).rgb;
    vec3 f = texture(uSource, vUv + t * vec2(2.0, 0.0)).rgb;
    vec3 g = texture(uSource, vUv + t * vec2(-2.0, -2.0)).rgb;
    vec3 h = texture(uSource, vUv + t * vec2(0.0, -2.0)).rgb;
    vec3 i = texture(uSource, vUv + t * vec2(2.0, -2.0)).rgb;
    vec3 j = texture(uSource, vUv + t * vec2(-1.0, 1.0)).rgb;
    vec3 k = texture(uSource, vUv + t * vec2(1.0, 1.0)).rgb;
    vec3 l = texture(uSource, vUv + t * vec2(-1.0, -1.0)).rgb;
    vec3 m = texture(uSource, vUv + t * vec2(1.0, -1.0)).rgb;
    vec3 sum = e * 0.125;
    sum += (a + c + g + i) * 0.03125;
    sum += (b + d + f + h) * 0.0625;
    sum += (j + k + l + m) * 0.125;
    fragColor = vec4(sum, 1.0);
}
