// Sprites procedurais das particulas: nenhum arquivo de textura. A saida e
// premultiplicada: alfa 0 = luz aditiva pura; alfa > 0 tambem encobre o fundo.
#include "lib/common.glsl"
#include "lib/noise.glsl"
#include "lib/scene.glsl"
#include "lib/output.glsl"

in vec2 vCorner;
in vec4 vColor;
in vec4 vInfo;

void main() {
    vec2 p = vCorner;
    float r = length(p);
    int style = int(vInfo.x + 0.5);
    float seed = vInfo.y;
    float t = vInfo.z;
    float shape = 0.0;
    float alphaShape = 0.0;
    if (style == 0) {
        // Brilho suave (energia, magia, poeira luminosa).
        float g = saturate(1.0 - r);
        shape = g * g * (0.55 + 2.4 * g * g * g);
    } else if (style == 1) {
        // Faisca: nucleo duro e alongado; fica fina quando esticada pela velocidade.
        float core = saturate(1.0 - abs(p.x) * 2.6) * saturate(1.0 - abs(p.y));
        shape = core * core * 3.0 + pow(saturate(1.0 - r), 3.0) * 0.6;
    } else if (style == 2) {
        // Fumaca: disco macio corroido por ruido; encobre o fundo.
        float n = fbm2(p * 1.7 + seed * 53.0 + vec2(0.0, -t * 0.6), 3);
        float d = saturate(1.0 - r * (0.85 + 0.6 * n));
        alphaShape = smoothstep(0.0, 0.55, d) * (0.55 + 0.45 * n);
        shape = alphaShape;
    } else if (style == 3) {
        // Estrela de quatro pontas (cintilacoes, luz sagrada, gelo).
        float cross4 = max(saturate(1.0 - abs(p.x) * 9.0) * saturate(1.0 - abs(p.y)),
                           saturate(1.0 - abs(p.y) * 9.0) * saturate(1.0 - abs(p.x)));
        float g = saturate(1.0 - r);
        shape = cross4 * cross4 * 2.5 + g * g * g * 2.0;
    } else if (style == 4) {
        // Estilhaco: losango facetado (gelo, cristal, pedra).
        float d = abs(p.x) * 1.9 + abs(p.y);
        float m = smoothstep(1.0, 0.86, d);
        float facet = 0.45 + 0.55 * step(0.0, p.x * (seed - 0.5)) + 0.5 * saturate(p.y);
        shape = m * facet;
        alphaShape = m;
    } else if (style == 5) {
        // Anel (bolhas, ondas, runas simples).
        float ring = saturate(1.0 - abs(r - 0.72) * 7.0);
        shape = ring * ring * 1.8 + pow(saturate(1.0 - r), 4.0) * 0.25;
    } else if (style == 6) {
        // Folha / petala.
        vec2 q = vec2(abs(p.x) * 1.8, p.y);
        float d = length(q - vec2(0.0, 0.0)) + abs(q.x) * 0.9;
        float m = smoothstep(1.0, 0.8, d + 0.25 * abs(p.y));
        float vein = 0.7 + 0.3 * smoothstep(0.0, 0.12, abs(p.x));
        shape = m * vein;
        alphaShape = m;
    } else {
        // Chama: gota que sobe, corroida por ruido.
        float n = fbm2(vec2(p.x * 2.0 + seed * 31.0, p.y * 1.4 - t * 2.5 + seed * 17.0), 3);
        float d = length(vec2(p.x * (1.3 + 0.9 * saturate(p.y + 0.3)), p.y * 0.9)) + (n - 0.5) * 0.7;
        float g = saturate(1.0 - d);
        shape = g * g * 3.0;
    }
    if (shape <= 0.001) {
        discard;
    }
    float soft = depthFade(vInfo.w, 0.6);
    // Evita que a particula "estoure" na tela quando atravessa a camera.
    soft *= saturate((vInfo.w - 0.15) * 4.0);
    fragColor = vec4(vColor.rgb * (shape * soft), vColor.a * alphaShape * soft);
}
