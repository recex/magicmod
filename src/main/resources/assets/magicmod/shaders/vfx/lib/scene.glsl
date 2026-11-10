// Acesso a cena do jogo: profundidade, raio de visao e reconstrucao de
// posicao. Todas as posicoes sao relativas a camera (a camera fica na
// origem), o que mantem a precisao em float longe do centro do mundo.

uniform sampler2D uSceneDepth;
uniform mat4 uViewProj;
uniform mat4 uInvViewProj;
uniform vec2 uScreenSize;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCamForward;

vec2 screenUv() {
    return gl_FragCoord.xy / uScreenSize;
}

// Posicao (relativa a camera) do ponto de profundidade bruta "depth" no pixel uv.
vec3 scenePosition(vec2 uv, float depth) {
    vec4 p = uInvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// Raio de visao exato do pixel (inclui o balanco da caminhada do jogo).
void viewRay(vec2 uv, out vec3 ro, out vec3 rd) {
    vec4 n = uInvViewProj * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
    vec4 f = uInvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    ro = n.xyz / n.w;
    rd = normalize(f.xyz / f.w - ro);
}

// Distancia, ao longo do raio, ate a superficie da cena naquele pixel.
float sceneDistance(vec2 uv, vec3 ro, vec3 rd) {
    float d = texture(uSceneDepth, uv).r;
    return dot(scenePosition(uv, d) - ro, rd);
}

// Profundidade linear (w de clip) da cena no pixel: so um produto escalar.
float sceneEyeDepth(vec2 uv) {
    float d = texture(uSceneDepth, uv).r;
    vec4 row = vec4(uInvViewProj[0].w, uInvViewProj[1].w, uInvViewProj[2].w, uInvViewProj[3].w);
    return 1.0 / dot(row, vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0));
}

// Teste de profundidade manual com borda suave para geometria rasterizada:
// 0 atras da cena, 1 bem na frente. "eyeDepth" e o w de clip do fragmento.
float depthFade(float eyeDepth, float softness) {
    float scene = sceneEyeDepth(screenUv());
    return clamp((scene - eyeDepth) / max(softness, 1.0e-4), 0.0, 1.0);
}

// Intersecao raio-esfera; devolve (entrada, saida) ou (-1, -1) se nao cruza.
vec2 raySphere(vec3 ro, vec3 rd, vec3 center, float radius) {
    vec3 oc = ro - center;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) {
        return vec2(-1.0);
    }
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}
