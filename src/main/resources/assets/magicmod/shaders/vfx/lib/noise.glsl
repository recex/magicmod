// Ruido procedural: valor 2D/3D, fbm, distorcao de dominio e celular.

float vnoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i);
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float fbm2(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        sum += vnoise2(p) * amp;
        norm += amp;
        p = m * p + 17.3;
        amp *= 0.5;
    }
    return sum / max(norm, 1.0e-4);
}

float fbm3(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        sum += vnoise3(p) * amp;
        norm += amp;
        p = p * 2.03 + vec3(31.7, 11.3, 5.1);
        amp *= 0.5;
    }
    return sum / max(norm, 1.0e-4);
}

// fbm com cristas (|n - 0.5|): filamentos de energia, veios, relampagos.
float ridged2(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        float n = 1.0 - abs(vnoise2(p) * 2.0 - 1.0);
        sum += n * n * amp;
        norm += amp;
        p = m * p + 9.1;
        amp *= 0.5;
    }
    return sum / max(norm, 1.0e-4);
}

// Celular (Worley) 2D: x = distancia ao ponto mais proximo, y = borda
// (segunda - primeira), z = id aleatorio da celula.
vec3 cellular2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float d1 = 8.0;
    float d2 = 8.0;
    float id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = hash22(i + g);
            vec2 r = g + o - f;
            float d = dot(r, r);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                id = hash21(i + g + 0.37);
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    d1 = sqrt(d1);
    d2 = sqrt(d2);
    return vec3(d1, d2 - d1, id);
}
