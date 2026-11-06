#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Intensity;
uniform vec2 CenterUv;

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.14159265

void main() {
    vec2 aspect = vec2(OutSize.x / OutSize.y, 1.0);
    vec2 uv = texCoord;
    vec2 toCenter = (uv - CenterUv) * aspect;
    float dist = length(toCenter);

    // Raio do horizonte de eventos (area totalmente escura/vazia) e
    // raio de influencia da gravidade visual, ambos escalados pela
    // intensidade (que cai de 1.0 a 0.0 ao longo da duracao do efeito).
    float eventHorizon = 0.05 * Intensity;
    float pullRadius = 0.55 * Intensity;

    // Deslocamento radial: puxa cada pixel em direcao ao centro com
    // forca inversamente proporcional a distancia (like lensing),
    // suavizado perto da borda de influencia para nao cortar a tela
    // de forma abrupta.
    float pull = smoothstep(pullRadius, eventHorizon, dist);
    float warpStrength = pull * pull * 0.35 * Intensity;

    vec2 dir = dist > 0.0001 ? toCenter / dist : vec2(0.0);
    vec2 warpedUv = uv - (dir / aspect) * warpStrength;

    // Leve rotacao em espiral proporcional a proximidade do centro,
    // reforcando a sensacao de redemoinho gravitacional.
    float angle = warpStrength * 2.2;
    float s = sin(angle);
    float c = cos(angle);
    vec2 rotOffset = (warpedUv - CenterUv) ;
    vec2 spiralUv = CenterUv + vec2(
        rotOffset.x * c - rotOffset.y * s,
        rotOffset.x * s + rotOffset.y * c
    );

    vec3 color;
    if (dist < eventHorizon) {
        // Dentro do horizonte de eventos: vazio absoluto.
        color = vec3(0.0);
    } else {
        color = texture(DiffuseSampler, clamp(spiralUv, 0.0, 1.0)).rgb;
    }

    // Anel de aberracao cromatica roxo/azulado na borda do horizonte,
    // reforcando a leitura visual de "buraco negro arcano".
    float ringGlow = smoothstep(eventHorizon, eventHorizon + 0.03, dist)
                    * (1.0 - smoothstep(eventHorizon + 0.03, eventHorizon + 0.09, dist));
    vec3 ringColor = vec3(0.55, 0.25, 0.95) * ringGlow * Intensity * 2.0;

    fragColor = vec4(color + ringColor, 1.0);
}
