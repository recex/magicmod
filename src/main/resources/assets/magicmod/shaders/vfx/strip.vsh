// Fitas (feixes, rastros, raios, cipos): malha dinamica ja voltada para a camera.
#include "lib/common.glsl"

in vec3 aPosition;   // relativa a camera
in vec2 aUv;         // x = distancia ao longo da fita (blocos), y = -1..1 na largura
in vec4 aColor;      // cor HDR; a = opacidade
in vec4 aExtra;      // x = estilo, y = idade normalizada, z = semente, w = comprimento total

uniform mat4 uViewProj;

out vec2 vUv;
out vec4 vColor;
out vec4 vExtra;
out float vEyeDepth;

void main() {
    gl_Position = uViewProj * vec4(aPosition, 1.0);
    vUv = aUv;
    vColor = aColor;
    vExtra = aExtra;
    vEyeDepth = gl_Position.w;
}
