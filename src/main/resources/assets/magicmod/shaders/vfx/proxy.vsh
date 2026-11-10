// Geometria substituta (um quadrilatero que cobre o efeito na tela) usada
// pelos efeitos volumetricos, que fazem o trabalho no fragment shader.
#include "lib/common.glsl"

in vec3 aPosition;

uniform mat4 uViewProj;
uniform float uFullscreen;

void main() {
    vec4 clip = uFullscreen > 0.5 ? vec4(aPosition.xy, 0.0, 1.0) : uViewProj * vec4(aPosition, 1.0);
    // A profundidade e tratada no fragment shader; fixa z para nao ser
    // cortado pelos planos proximo/distante.
    clip.z = 0.0;
    gl_Position = clip;
}
