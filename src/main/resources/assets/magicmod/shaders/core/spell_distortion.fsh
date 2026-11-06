#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Intensity;
uniform vec3 EffectColor;
uniform float GameTime;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 center = vec2(0.5, 0.5);
    vec2 toCenter = texCoord - center;
    float dist = length(toCenter);

    // Distorcao radial tipo onda de choque, mais forte perto do centro
    // no inicio do efeito e se espalhando para fora com o tempo.
    float wave = sin(dist * 40.0 - GameTime * 60.0) * 0.004 * Intensity;
    vec2 distortedUv = texCoord + normalize(toCenter + 0.0001) * wave;

    // Leve aberracao cromatica proporcional a intensidade do efeito.
    float aberration = 0.0025 * Intensity;
    float r = texture(DiffuseSampler, distortedUv + toCenter * aberration).r;
    float g = texture(DiffuseSampler, distortedUv).g;
    float b = texture(DiffuseSampler, distortedUv - toCenter * aberration).b;

    vec3 baseColor = vec3(r, g, b);

    // Vinheta colorida pulsante nas bordas, usando a cor da escola de magia.
    float vignette = smoothstep(0.3, 0.9, dist);
    float pulse = 0.5 + 0.5 * sin(GameTime * 10.0);
    vec3 tinted = mix(baseColor, EffectColor, vignette * Intensity * (0.35 + 0.15 * pulse));

    fragColor = vec4(tinted, 1.0);
}
