// steps = 32
const frames = [];
const C = [0, 1.6, -9];
for (const age of [0.05, 0.16, 0.34, 0.6]) {
  const life = 1.6; const t = age * life;
  const grow = 1 - Math.pow(1 - Math.min(age / 0.5, 1), 3);
  const radius = 0.7 + grow * 3.0; const heat = Math.pow(1 - age, 1.5);
  const sparks = api.burst({ count: 220, seed: 7, speed: [6, 19], life: [0.5, 1.3], size: [0.035, 0.08], sizeEnd: 0.2, drag: 2.2, gravity: 9, color0: [6, 2.6, 0.7, 0], color1: [2, 0.3, 0.02, 0], style: 1, stretch: 0.09, up: 0.5, spawnRadius: 0.4 });
  const smoke = api.burst({ count: 36, seed: 11, speed: [1, 4], life: [1.2, 2.4], size: [0.7, 1.4], sizeEnd: 2.4, drag: 1.5, gravity: -1.2, color0: [0.07, 0.06, 0.06, 0.55], color1: [0.03, 0.03, 0.03, 0.0], style: 2, spawnRadius: 1.0, fadeIn: 0.15, fadeOut: 0.4, turbulence: 0.2 });
  frames.push({ label: 'explosao age=' + age, time: t, camera: { pos: [0, 1.62, 0], pitch: 4 },
    items: [
      { type: 'particles', origin: C, data: smoke },
      { type: 'proxy', shader: 'volume/fireball', center: [0, 1.6 + age * 1.2, -9], radius, age, time: t, seed: 0.37, colorA: [1.0, 0.42, 0.10, 1], colorB: [0.05, 0.04, 0.04, 0.85], params0: [1.0, 0.6, 1.0, 1.0] },
      { type: 'particles', origin: C, data: sparks }],
    lights: [{ pos: [0, 1.5, -9], radius: 14, color: [5.0 * heat, 2.0 * heat, 0.5 * heat] }], post: { bloom: 0.25 } });
}
// feixe + raio + rastro + fluxo
function bolt(a, b, seed, n, amp) { const r = api.rand(seed); const pts = []; for (let i = 0; i <= n; i++) { const f = i / n; const env = Math.sin(f * Math.PI);
  pts.push([a[0] + (b[0] - a[0]) * f + (r() - 0.5) * amp * env, a[1] + (b[1] - a[1]) * f + (r() - 0.5) * amp * env, a[2] + (b[2] - a[2]) * f + (r() - 0.5) * amp * env]); } return pts; }
function arc(n, fn) { const pts = []; for (let i = 0; i <= n; i++) pts.push(fn(i / n)); return pts; }
for (const tt of [0.3, 0.8]) {
  frames.push({ label: 'fitas t=' + tt, time: tt, camera: { pos: [0, 1.62, 0], pitch: 2 },
    items: [
      { type: 'strip', style: 0, points: arc(24, f => [-6 + f * 3, 1.2 + f * 2.0, -4 - f * 8]), width: 0.7, color: [0.35, 0.6, 1.0, 0], seed: 0.2 },
      { type: 'strip', style: 2, points: bolt([1, 9, -10], [0.5, 0, -10], 5 + tt * 10, 18, 1.6), width: 0.45, color: [0.6, 0.7, 1.0, 0], seed: 0.5 },
      { type: 'strip', style: 2, points: bolt([0.8, 5, -10], [3.2, 2.6, -9], 9 + tt * 10, 9, 0.8), width: 0.25, color: [0.6, 0.7, 1.0, 0], seed: 0.7 },
      { type: 'strip', style: 1, points: arc(30, f => [3.5 + Math.cos(f * 5) * 1.3, 0.4 + f * 2.6, -7 + Math.sin(f * 5) * 1.3]), width: f => 0.1 + 0.45 * f, color: [1.0, 0.3, 0.9, 0], seed: 0.1 },
      { type: 'strip', style: 4, points: arc(30, f => [-3 + f * 2.5, 0.6 + Math.sin(f * 3.1) * 1.2, -6 - Math.sin(f * 4) * 0.8]), width: 0.9, color: [0.4, 1.0, 0.75, 0.6], seed: 0.9 },
      { type: 'strip', style: 3, age: 0.85, points: arc(40, f => [6 + Math.cos(f * 9) * (0.9 - f * 0.5), f * 3.2, -8 + Math.sin(f * 9) * (0.9 - f * 0.5)]), width: f => 0.32 * (1.1 - f), color: [0.16, 0.42, 0.10, 1], seed: 0.3 },
    ],
    lights: [{ pos: [0.5, 0.5, -10], radius: 10, color: [1.2, 1.5, 3.0] }], post: { bloom: 0.25 } });
}
return { cols: 3, frames };
