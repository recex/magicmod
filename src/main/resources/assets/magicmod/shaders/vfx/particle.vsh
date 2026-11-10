// Particulas na GPU: cada instancia guarda so o estado de nascimento; a
// trajetoria inteira (arrasto, gravidade, turbulencia, giro, atracao) e
// calculada aqui em forma fechada, sem custo de CPU por particula.
#include "lib/common.glsl"

in vec2 aCorner;
in vec4 iPosBirth;   // xyz = posicao inicial (relativa a uOrigin), w = nascimento (s)
in vec4 iVelLife;    // xyz = velocidade inicial, w = vida (s)
in vec4 iSizes;      // x = tamanho inicial, y = tamanho final, z = arrasto, w = gravidade
in vec4 iColor0;     // cor inicial (HDR); a = opacidade (0 = aditivo puro)
in vec4 iColor1;     // cor final
in vec4 iMisc;       // x = estilo, y = semente, z = esticamento pela velocidade, w = turbulencia
in vec4 iTarget;     // xyz = alvo (relativo a uOrigin), w = expoente de atracao (0 = sem alvo)
in vec4 iAnim;       // x = voltas em torno do alvo, y = fracao de fade-in, z = inicio do fade-out, w = cintilacao

uniform mat4 uViewProj;
uniform vec3 uOrigin;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform float uTime;

out vec2 vCorner;
out vec4 vColor;
out vec4 vInfo;      // x = estilo, y = semente, z = idade normalizada, w = profundidade linear

void main() {
    float age = uTime - iPosBirth.w;
    float life = max(iVelLife.w, 1.0e-3);
    float t = age / life;
    vCorner = aCorner;
    vColor = vec4(0.0);
    vInfo = vec4(0.0);
    if (age < 0.0 || t >= 1.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    float seed = iMisc.y;
    float k = iSizes.z;
    float travel = k > 1.0e-3 ? (1.0 - exp(-k * age)) / k : age;
    float slow = k > 1.0e-3 ? exp(-k * age) : 1.0;
    vec3 pos = iPosBirth.xyz + iVelLife.xyz * travel;
    pos.y -= 0.5 * iSizes.w * age * age;
    vec3 vel = iVelLife.xyz * slow - vec3(0.0, iSizes.w * age, 0.0);

    if (iMisc.w > 0.0) {
        vec3 ph = vec3(seed * 61.0, seed * 97.0 + 1.3, seed * 43.0 + 2.1);
        vec3 wob = vec3(sin(age * 3.1 + ph.x), sin(age * 2.3 + ph.y), sin(age * 3.7 + ph.z));
        vec3 dwob = vec3(3.1 * cos(age * 3.1 + ph.x), 2.3 * cos(age * 2.3 + ph.y), 3.7 * cos(age * 3.7 + ph.z));
        float ramp = min(age * 2.0, 1.0);
        pos += wob * iMisc.w * ramp;
        vel += dwob * iMisc.w * ramp;
    }
    if (abs(iAnim.x) > 1.0e-4) {
        // Giro em torno do eixo vertical que passa pelo alvo.
        vec3 rel = pos - iTarget.xyz;
        rel.xz = rot2(iAnim.x * TAU * t) * rel.xz;
        vec3 swirled = iTarget.xyz + rel;
        vel += (swirled - pos) / max(age, 0.05);
        pos = swirled;
    }
    if (iTarget.w > 0.0) {
        float pull = pow(t, iTarget.w);
        vec3 pulled = mix(pos, iTarget.xyz, pull);
        vel = mix(vel, (iTarget.xyz - pos) * (iTarget.w / life), pull);
        pos = pulled;
    }

    float size = mix(iSizes.x, iSizes.y, t);
    float fadeIn = iAnim.y > 1.0e-4 ? saturate(t / iAnim.y) : 1.0;
    float fadeOut = 1.0 - smoothstep(iAnim.z, 1.0, t);
    float flicker = 1.0 - iAnim.w * (0.5 + 0.5 * sin(age * 37.0 + seed * 311.0));
    vec4 color = mix(iColor0, iColor1, t);
    float env = fadeIn * fadeOut;
    vColor = vec4(color.rgb * env * flicker, color.a * env);

    vec2 dir = vec2(0.0, 1.0);
    float stretch = 1.0;
    if (iMisc.z > 0.0) {
        vec2 v2 = vec2(dot(vel, uCamRight), dot(vel, uCamUp));
        float sp = length(v2);
        if (sp > 1.0e-4) {
            dir = v2 / sp;
        }
        stretch = 1.0 + iMisc.z * sp;
    } else {
        float ang = seed * TAU + t * (seed - 0.5) * 5.0;
        dir = vec2(sin(ang), cos(ang));
    }
    vec2 side = vec2(dir.y, -dir.x);
    vec2 off = (side * aCorner.x + dir * aCorner.y * stretch) * size;
    vec3 world = uOrigin + pos + uCamRight * off.x + uCamUp * off.y;
    gl_Position = uViewProj * vec4(world, 1.0);
    vInfo = vec4(iMisc.x, seed, t, gl_Position.w);
    vCorner = vec2(aCorner.x, aCorner.y);
}
