// Lado da pagina do laboratorio: um mini motor que espelha o RenderEngine do mod.
(function () {
  const M = {
    mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
    persp(fovy, aspect, n, f) { const t = 1 / Math.tan(fovy / 2); const o = new Float32Array(16); o[0] = t / aspect; o[5] = t; o[10] = (f + n) / (n - f); o[11] = -1; o[14] = 2 * f * n / (n - f); return o; },
    rotX(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1]); },
    rotY(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1]); },
    inv(m) { const o = new Float32Array(16); const a = m;
      const b00 = a[0] * a[5] - a[1] * a[4], b01 = a[0] * a[6] - a[2] * a[4], b02 = a[0] * a[7] - a[3] * a[4], b03 = a[1] * a[6] - a[2] * a[5], b04 = a[1] * a[7] - a[3] * a[5], b05 = a[2] * a[7] - a[3] * a[6];
      const b06 = a[8] * a[13] - a[9] * a[12], b07 = a[8] * a[14] - a[10] * a[12], b08 = a[8] * a[15] - a[11] * a[12], b09 = a[9] * a[14] - a[10] * a[13], b10 = a[9] * a[15] - a[11] * a[13], b11 = a[10] * a[15] - a[11] * a[14];
      const d = 1 / (b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06);
      o[0] = (a[5] * b11 - a[6] * b10 + a[7] * b09) * d; o[1] = (a[2] * b10 - a[1] * b11 - a[3] * b09) * d; o[2] = (a[13] * b05 - a[14] * b04 + a[15] * b03) * d; o[3] = (a[10] * b04 - a[9] * b05 - a[11] * b03) * d;
      o[4] = (a[6] * b08 - a[4] * b11 - a[7] * b07) * d; o[5] = (a[0] * b11 - a[2] * b08 + a[3] * b07) * d; o[6] = (a[14] * b02 - a[12] * b05 - a[15] * b01) * d; o[7] = (a[8] * b05 - a[10] * b02 + a[11] * b01) * d;
      o[8] = (a[4] * b10 - a[5] * b08 + a[7] * b06) * d; o[9] = (a[1] * b08 - a[0] * b10 - a[3] * b06) * d; o[10] = (a[12] * b04 - a[13] * b02 + a[15] * b00) * d; o[11] = (a[9] * b02 - a[8] * b04 - a[11] * b00) * d;
      o[12] = (a[5] * b07 - a[4] * b09 - a[6] * b06) * d; o[13] = (a[0] * b09 - a[1] * b07 + a[2] * b06) * d; o[14] = (a[13] * b01 - a[12] * b03 - a[14] * b00) * d; o[15] = (a[8] * b03 - a[9] * b01 + a[10] * b00) * d; return o; },
  };
  const V = { sub: (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]], add: (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]], mul: (a, s) => [a[0] * s, a[1] * s, a[2] * s],
    dot: (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2], cross: (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]],
    len: a => Math.hypot(a[0], a[1], a[2]), norm(a) { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; } };

  window.labRun = async function (sources, scenarioSrc, W, H) {
    const log = []; let failed = false;
    const glc = document.createElement('canvas'); glc.width = W; glc.height = H;
    const gl = glc.getContext('webgl2', { antialias: false, alpha: false, preserveDrawingBuffer: true });
    if (!gl) throw new Error('sem WebGL2');
    gl.getExtension('EXT_color_buffer_float'); gl.getExtension('OES_texture_float_linear');
    const HEADER = '#version 300 es\n';
    const programs = {};
    function shader(type, src, name) { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) { const info = gl.getShaderInfoLog(s); const lines = src.split('\n');
        const detail = (info.match(/0:(\d+)/g) || []).slice(0, 6).map(m => { const n = parseInt(m.slice(2)); return '   L' + n + ': ' + (lines[n - 1] || '').trim(); }).join('\n');
        throw new Error('compilacao ' + name + ':\n' + info + detail); } return s; }
    function program(key, vs, fs, defines, attribs) {
      const id = key + '|' + (defines || []).join(',');
      if (programs[id]) return programs[id];
      const defs = (defines || []).map(d => '#define ' + d + '\n').join('');
      const p = gl.createProgram();
      try {
        gl.attachShader(p, shader(gl.VERTEX_SHADER, HEADER + defs + sources[vs], vs));
        gl.attachShader(p, shader(gl.FRAGMENT_SHADER, HEADER + defs + sources[fs], fs));
        (attribs || []).forEach((a, i) => gl.bindAttribLocation(p, i, a));
        gl.linkProgram(p);
        if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error('link ' + id + ': ' + gl.getProgramInfoLog(p));
      } catch (e) { log.push('FALHA ' + id + ' -> ' + e.message); failed = true; programs[id] = null; return null; }
      const prog = { p, loc: {}, types: {} };
      const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
      for (let i = 0; i < n; i++) { const u = gl.getActiveUniform(p, i); const nm = u.name.replace(/\[0\]$/, ''); prog.loc[nm] = gl.getUniformLocation(p, u.name); prog.types[nm] = u.type; }
      programs[id] = prog; return prog;
    }
    function setU(prog, name, v) { const l = prog.loc[name]; if (l == null) return; const t = prog.types[name];
      if (t === gl.FLOAT) gl.uniform1fv(l, typeof v === 'number' ? [v] : v); else if (t === gl.FLOAT_VEC2) gl.uniform2fv(l, v); else if (t === gl.FLOAT_VEC3) gl.uniform3fv(l, v);
      else if (t === gl.FLOAT_VEC4) gl.uniform4fv(l, v); else if (t === gl.FLOAT_MAT4) gl.uniformMatrix4fv(l, false, v); else if (t === gl.INT || t === gl.SAMPLER_2D) gl.uniform1i(l, v); }
    function tex(w, h, ifmt, fmt, type, filter) { const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t); gl.texImage2D(gl.TEXTURE_2D, 0, ifmt, w, h, 0, fmt, type, null);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, filter); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, filter);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE); return t; }
    function target(w, h, hdr, depth) { const f = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, f);
      const c = hdr ? tex(w, h, gl.RGBA16F, gl.RGBA, gl.HALF_FLOAT, gl.LINEAR) : tex(w, h, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, gl.LINEAR);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, c, 0); let d = null;
      if (depth) { d = tex(w, h, gl.DEPTH_COMPONENT24, gl.DEPTH_COMPONENT, gl.UNSIGNED_INT, gl.NEAREST); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.TEXTURE_2D, d, 0); }
      if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) throw new Error('FBO incompleto');
      return { f, c, d, w, h }; }
    function bind(t) { gl.bindFramebuffer(gl.FRAMEBUFFER, t ? t.f : null); gl.viewport(0, 0, t ? t.w : W, t ? t.h : H); }
    function useTex(unit, t) { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t); }

    const fsVao = gl.createVertexArray(); gl.bindVertexArray(fsVao); const fsBuf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, fsBuf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW); gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    function fullscreen() { gl.bindVertexArray(fsVao); gl.drawArrays(gl.TRIANGLES, 0, 3); }

    // Malha dinamica: pos(3) uv(2) cor(4) extra(4)
    const meshVao = gl.createVertexArray(); gl.bindVertexArray(meshVao); const meshBuf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, meshBuf);
    const STRIDE = 13 * 4; [[0, 3, 0], [1, 2, 12], [2, 4, 20], [3, 4, 36]].forEach(([i, n, o]) => { gl.enableVertexAttribArray(i); gl.vertexAttribPointer(i, n, gl.FLOAT, false, STRIDE, o); });
    function drawMesh(verts) { gl.bindVertexArray(meshVao); gl.bindBuffer(gl.ARRAY_BUFFER, meshBuf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(verts), gl.STREAM_DRAW); gl.drawArrays(gl.TRIANGLES, 0, verts.length / 13); }

    // Particulas instanciadas: 7 vec4 por instancia
    const PSTRIDE = 32 * 4; const partVao = gl.createVertexArray(); gl.bindVertexArray(partVao);
    const cornerBuf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, cornerBuf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    const partBuf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, partBuf);
    for (let i = 0; i < 8; i++) { gl.enableVertexAttribArray(1 + i); gl.vertexAttribPointer(1 + i, 4, gl.FLOAT, false, PSTRIDE, i * 16); gl.vertexAttribDivisor(1 + i, 1); }

    const half = (v) => Math.max(1, v >> 1);
    const T = { scene: target(W, H, false, true), vfx: target(W, H, true, false), distort: target(half(W), half(H), true, false), final: target(W, H, false, false), bloom: [] };
    { let w = half(W), h = half(H); for (let i = 0; i < 6 && w > 8 && h > 8; i++) { T.bloom.push(target(w, h, true, false)); w = half(w); h = half(h); } }
    const quality = (new Function('return ' + (scenarioSrc.match(/\/\/\s*steps\s*=\s*(\d+)/) || [0, 32])[1]))();

    function cameraOf(cam) {
      const pos = cam.pos || [0, 1.62, 0]; const yaw = (cam.yaw || 0) * Math.PI / 180, pitch = (cam.pitch || 0) * Math.PI / 180;
      const proj = M.persp((cam.fov || 70) * Math.PI / 180, W / H, 0.05, 512);
      const view = M.mul(M.rotX(pitch), M.rotY(yaw)); const vp = M.mul(proj, view); const ivp = M.inv(vp);
      const right = [view[0], view[4], view[8]], up = [view[1], view[5], view[9]], fwd = [-view[2], -view[6], -view[10]];
      return { pos, proj, view, vp, ivp, right, up, fwd };
    }
    function worldUniforms(prog, cam, tgt) { setU(prog, 'uViewProj', cam.vp); setU(prog, 'uInvViewProj', cam.ivp); setU(prog, 'uScreenSize', [tgt.w, tgt.h]);
      setU(prog, 'uCamRight', cam.right); setU(prog, 'uCamUp', cam.up); setU(prog, 'uCamForward', cam.fwd); useTex(0, T.scene.d); setU(prog, 'uSceneDepth', 0); }
    function proxyQuad(cam, center, radius) {
      const rel = V.sub(center, cam.pos); const d = V.len(rel);
      if (d < radius * 1.25) return { full: 1, verts: [[-1, -1, 0], [1, -1, 0], [1, 1, 0], [-1, -1, 0], [1, 1, 0], [-1, 1, 0]] };
      const s = radius * d / Math.sqrt(d * d - radius * radius) * 1.05; const dir = V.mul(rel, 1 / d);
      let r = V.norm(V.cross(dir, [0, 1, 0])); if (!isFinite(r[0]) || V.len(V.cross(dir, [0, 1, 0])) < 1e-4) r = [1, 0, 0]; const u = V.cross(r, dir);
      const c = (a, b) => V.add(rel, V.add(V.mul(r, a * s), V.mul(u, b * s)));
      return { full: 0, verts: [c(-1, -1), c(1, -1), c(1, 1), c(-1, -1), c(1, 1), c(-1, 1)] };
    }
    function drawProxy(cam, it, tgt, distortPass) {
      const defs = ['VFX_STEPS ' + (it.steps || quality)]; if (distortPass) defs.push('DISTORT_PASS');
      const prog = program(it.shader, 'proxy.vsh', it.shader + '.fsh', defs, ['aPosition']); if (!prog) return;
      gl.useProgram(prog.p); worldUniforms(prog, cam, tgt);
      const q = proxyQuad(cam, it.center, it.radius * (it.bound || 1)); setU(prog, 'uFullscreen', q.full);
      setU(prog, 'uCenter', V.sub(it.center, cam.pos)); setU(prog, 'uRadius', it.radius); setU(prog, 'uAxis', V.norm(it.axis || [0, 1, 0]));
      setU(prog, 'uAge', it.age || 0); setU(prog, 'uTime', it.time || 0); setU(prog, 'uSeed', it.seed || 0);
      setU(prog, 'uColorA', it.colorA || [1, 1, 1, 1]); setU(prog, 'uColorB', it.colorB || [0, 0, 0, 1]);
      for (let i = 0; i < 4; i++) setU(prog, 'uParams' + i, it['params' + i] || [0, 0, 0, 0]);
      const verts = []; for (const p of q.verts) verts.push(p[0], p[1], p[2], 0, 0, 1, 1, 1, 1, 0, 0, 0, 0); drawMesh(verts);
    }
    function stripVerts(cam, it) { // fita voltada para a camera ao longo dos pontos
      const pts = it.points.map(p => V.sub(p, cam.pos)); const verts = []; let dist = 0; const rows = [];
      for (let i = 0; i < pts.length; i++) { const a = pts[Math.max(i - 1, 0)], b = pts[Math.min(i + 1, pts.length - 1)]; const tan = V.norm(V.sub(b, a));
        let side = V.cross(tan, V.norm(pts[i])); const sl = V.len(side); side = sl < 1e-5 ? [1, 0, 0] : V.mul(side, 1 / sl);
        if (i > 0) dist += V.len(V.sub(pts[i], pts[i - 1])); const f = pts.length > 1 ? i / (pts.length - 1) : 0;
        const w = (typeof it.width === 'function' ? it.width(f) : it.width) * 0.5; rows.push({ p: pts[i], side, w, dist, f }); }
      const total = dist || 1; const col = it.color || [1, 1, 1, 1];
      const vtx = (r, v) => { const p = V.add(r.p, V.mul(r.side, r.w * v)); verts.push(p[0], p[1], p[2], r.dist, v, col[0], col[1], col[2], col[3], it.style || 0, it.age || 0, it.seed || 0, total); };
      for (let i = 0; i + 1 < rows.length; i++) { const a = rows[i], b = rows[i + 1]; vtx(a, -1); vtx(a, 1); vtx(b, 1); vtx(a, -1); vtx(b, 1); vtx(b, -1); }
      return verts;
    }
    function drawItems(cam, items, tgt, time, distortPass) {
      for (const it of items) {
        if (it.type === 'proxy') { if (distortPass && !it.distort) continue; if (!distortPass && it.distortOnly) continue; drawProxy(cam, it, tgt, distortPass); }
        else if (it.type === 'strip' && !distortPass) { const prog = program('strip', 'strip.vsh', 'strip.fsh', [], ['aPosition', 'aUv', 'aColor', 'aExtra']); if (!prog) continue;
          gl.useProgram(prog.p); worldUniforms(prog, cam, tgt); setU(prog, 'uTime', time); drawMesh(stripVerts(cam, it)); }
        else if (it.type === 'particles' && !distortPass) { const prog = program('particle', 'particle.vsh', 'particle.fsh', [], ['aCorner', 'iPosBirth', 'iVelLife', 'iSizes', 'iColor0', 'iColor1', 'iMisc', 'iTarget', 'iAnim']); if (!prog) continue;
          gl.useProgram(prog.p); worldUniforms(prog, cam, tgt); setU(prog, 'uTime', time); setU(prog, 'uOrigin', V.sub(it.origin || [0, 0, 0], cam.pos));
          gl.bindVertexArray(partVao); gl.bindBuffer(gl.ARRAY_BUFFER, partBuf); gl.bufferData(gl.ARRAY_BUFFER, it.data, gl.STREAM_DRAW); gl.drawArraysInstanced(gl.TRIANGLE_STRIP, 0, 4, it.data.length / 32); }
      }
    }
    function renderFrame(fr) {
      const cam = cameraOf(fr.camera || {}); const time = fr.time || 0; const post = fr.post || {}; const items = fr.items || [];
      gl.disable(gl.BLEND); gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.ALWAYS); gl.depthMask(true);
      bind(T.scene); const sp = program('labscene', 'fullscreen.vsh', 'labscene.fsh', [], ['aPosition']); gl.useProgram(sp.p);
      setU(sp, 'uViewProj', cam.vp); setU(sp, 'uInvViewProj', cam.ivp); setU(sp, 'uCamPos', cam.pos); setU(sp, 'uDay', fr.day || 0); fullscreen();
      gl.disable(gl.DEPTH_TEST); gl.depthMask(false);
      bind(T.vfx); gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT); gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
      drawItems(cam, items, T.vfx, time, false);
      bind(T.distort); gl.clear(gl.COLOR_BUFFER_BIT); gl.blendFunc(gl.ONE, gl.ONE); drawItems(cam, items, T.distort, time, true);
      gl.disable(gl.BLEND);
      const pre = program('bloom_prefilter', 'fullscreen.vsh', 'post/bloom_prefilter.fsh', [], ['aPosition']);
      const down = program('bloom_down', 'fullscreen.vsh', 'post/bloom_down.fsh', [], ['aPosition']);
      const up = program('bloom_up', 'fullscreen.vsh', 'post/bloom_up.fsh', [], ['aPosition']);
      if (pre && down && up) {
        bind(T.bloom[0]); gl.useProgram(pre.p); useTex(0, T.vfx.c); setU(pre, 'uSource', 0); useTex(1, T.scene.c); setU(pre, 'uScene', 1); setU(pre, 'uTexel', [1 / W, 1 / H]); setU(pre, 'uSceneBloom', post.sceneBloom || 0); fullscreen();
        for (let i = 1; i < T.bloom.length; i++) { bind(T.bloom[i]); gl.useProgram(down.p); useTex(0, T.bloom[i - 1].c); setU(down, 'uSource', 0); setU(down, 'uTexel', [1 / T.bloom[i - 1].w, 1 / T.bloom[i - 1].h]); fullscreen(); }
        gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE);
        for (let i = T.bloom.length - 1; i > 0; i--) { bind(T.bloom[i - 1]); gl.useProgram(up.p); useTex(0, T.bloom[i].c); setU(up, 'uSource', 0); setU(up, 'uTexel', [1 / T.bloom[i].w, 1 / T.bloom[i].h]); setU(up, 'uWeight', 1.0); fullscreen(); }
        gl.disable(gl.BLEND);
      }
      bind(null); const cp = program('composite', 'fullscreen.vsh', 'post/composite.fsh', [], ['aPosition']); if (!cp) return; gl.useProgram(cp.p);
      useTex(0, T.scene.c); setU(cp, 'uScene', 0); useTex(1, T.vfx.c); setU(cp, 'uVfx', 1); useTex(2, T.bloom[0].c); setU(cp, 'uBloom', 2); useTex(3, T.distort.c); setU(cp, 'uDistort', 3); useTex(4, T.scene.d); setU(cp, 'uSceneDepth', 4);
      setU(cp, 'uInvViewProj', cam.ivp); setU(cp, 'uPrevViewProj', cam.vp); setU(cp, 'uCamDelta', [0, 0, 0]); setU(cp, 'uAspect', [W / H, 1]);
      setU(cp, 'uBloomStrength', post.bloom == null ? 0.25 : post.bloom); setU(cp, 'uDistortStrength', post.distort == null ? 1 : post.distort); setU(cp, 'uChroma', post.chroma || 0);
      setU(cp, 'uRadialBlur', post.radialBlur || [0.5, 0.5, 0, 0]); setU(cp, 'uMotionBlur', 0); setU(cp, 'uFlash', post.flash || [1, 1, 1, 0]);
      setU(cp, 'uGrade', post.grade || [1, 1, 1, 0]); setU(cp, 'uTint', post.tint || [1, 1, 1, 0]); setU(cp, 'uImpact', post.impact || [0, 1, 1, 1]); setU(cp, 'uLetterbox', post.letterbox || 0); setU(cp, 'uFrameSeed', time);
      const lights = (fr.lights || []).slice(0, 8); setU(cp, 'uLightCount', lights.length);
      if (lights.length) { const lp = [], lc = []; for (const l of lights) { const r = V.sub(l.pos, cam.pos); lp.push(r[0], r[1], r[2], l.radius); lc.push(l.color[0], l.color[1], l.color[2], 1); }
        gl.uniform4fv(cp.loc['uLightPos'], new Float32Array(lp)); gl.uniform4fv(cp.loc['uLightColor'], new Float32Array(lc)); }
      fullscreen();
    }

    // Compila todos os programas conhecidos mesmo que o cenario nao os use.
    const all = Object.keys(sources);
    for (const f of all.filter(n => n.startsWith('post/'))) program(f.slice(5, -4), 'fullscreen.vsh', f, [], ['aPosition']);
    for (const f of all.filter(n => n.startsWith('volume/'))) { program(f.slice(0, -4), 'proxy.vsh', f, ['VFX_STEPS 16'], ['aPosition']); program(f.slice(0, -4), 'proxy.vsh', f, ['VFX_STEPS 16', 'DISTORT_PASS'], ['aPosition']); }
    if (sources['strip.fsh']) program('strip', 'strip.vsh', 'strip.fsh', [], ['aPosition', 'aUv', 'aColor', 'aExtra']);
    if (sources['particle.fsh']) program('particle', 'particle.vsh', 'particle.fsh', [], ['aCorner', 'iPosBirth', 'iVelLife', 'iSizes', 'iColor0', 'iColor1', 'iMisc', 'iTarget', 'iAnim']);
    if (sources['solid.fsh']) program('solid', 'solid.vsh', 'solid.fsh', [], ['aPosition', 'aNormal']);
    log.push('programas: ' + Object.keys(programs).length + ' falhas: ' + Object.values(programs).filter(p => !p).length);

    function burst(o) { const rnd = api.rand(o.seed || 1); const n = o.count; const d = new Float32Array(n * 32);
      for (let i = 0; i < n; i++) { let dir; do { dir = [rnd() * 2 - 1, rnd() * 2 - 1, rnd() * 2 - 1]; } while (V.len(dir) > 1 || V.len(dir) < 0.05); dir = V.norm(dir);
        if (o.up) { dir[1] = Math.abs(dir[1]) * o.up + (1 - o.up) * dir[1]; dir = V.norm(dir); }
        const sp = o.speed[0] + (o.speed[1] - o.speed[0]) * rnd(); const rr = (o.spawnRadius || 0) * Math.cbrt(rnd()); const life = o.life[0] + (o.life[1] - o.life[0]) * rnd(); const sz = o.size[0] + (o.size[1] - o.size[0]) * rnd();
        const b = i * 32; const c0 = o.color0, c1 = o.color1 || o.color0; const tg = o.target || [0, 0, 0];
        d.set([dir[0] * rr, dir[1] * rr, dir[2] * rr, o.birth || 0, dir[0] * sp, dir[1] * sp, dir[2] * sp, life, sz, sz * (o.sizeEnd == null ? 0.3 : o.sizeEnd), o.drag || 0, o.gravity || 0,
          c0[0], c0[1], c0[2], c0[3], c1[0], c1[1], c1[2], c1[3], o.style || 0, rnd(), o.stretch || 0, o.turbulence || 0, tg[0], tg[1], tg[2], o.attract || 0, o.swirl || 0, o.fadeIn || 0, o.fadeOut == null ? 0.6 : o.fadeOut, o.flicker || 0], b); }
      return d; }
    const api = { V, M, burst, rand(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; } };
    const sc = (new Function('api', scenarioSrc))(api) || {}; const frames = sc.frames || [];
    if (!frames.length) return { log, failed };
    const cols = sc.cols || Math.min(frames.length, 4), rows = Math.ceil(frames.length / cols);
    const out = document.getElementById('out'); out.width = cols * W; out.height = rows * H; const ctx = out.getContext('2d');
    const t0 = performance.now();
    frames.forEach((fr, i) => { renderFrame(fr); gl.finish(); ctx.drawImage(glc, (i % cols) * W, Math.floor(i / cols) * H);
      if (fr.label) { ctx.fillStyle = '#fff'; ctx.font = '14px sans-serif'; ctx.fillText(fr.label, (i % cols) * W + 8, Math.floor(i / cols) * H + 18); } });
    const err = gl.getError(); if (err) { log.push('GL error 0x' + err.toString(16)); failed = true; }
    return { log, failed, width: out.width, height: out.height, msPerFrame: Math.round((performance.now() - t0) / frames.length) };
  };
})();
