// steps = 32
const frames = [];
const cam = { pos: [0, 1.62, 0], pitch: 6 };
// 1: ondas de choque (anel no chao + cupula) com distorcao
frames.push({ label: 'shockwave', time: 0.4, day: 1, camera: cam, items: [
  { type: 'proxy', shader: 'volume/shockwave', center: [-3.5, 0.3, -9], radius: 3.2, age: 0.35, time: 0.4, seed: 0.1, colorA: [1.0, 0.5, 0.15, 1], colorB: [1, 1, 1, 1], params0: [0.12, 0.14, 1.2, 1.0], distort: true, bound: 1.05 },
  { type: 'proxy', shader: 'volume/shockwave', center: [3.5, 1.5, -9], radius: 2.6, age: 0.3, time: 0.4, seed: 0.6, colorA: [0.4, 0.7, 1.0, 1], colorB: [1, 1, 1, 1], params0: [1.0, 0.08, 1.0, 1.5], distort: true, bound: 1.05 }], post: {} });
// 2: orbes (plasma, estrela, escudo, sombra)
frames.push({ label: 'orbs', time: 1.3, camera: cam, items: [
  { type: 'proxy', shader: 'volume/orb', center: [-5.2, 1.6, -9], radius: 0.9, time: 1.3, seed: 0.2, colorA: [0.6, 0.3, 1.0, 1], colorB: [0.1, 0.0, 0.3, 1], params0: [0, 0.8, 1.0, 1.2], params1: [1.0, 0, 0.0, 0], bound: 2.3 },
  { type: 'proxy', shader: 'volume/orb', center: [-1.8, 2.0, -9], radius: 1.1, time: 1.3, seed: 0.4, colorA: [1.0, 0.75, 0.3, 1], colorB: [0, 0, 0, 1], params0: [1, 0.3, 1.0, 1.6], params1: [1.0, 0, 1.0, 0], bound: 2.7 },
  { type: 'proxy', shader: 'volume/orb', center: [1.8, 1.4, -8], radius: 1.4, time: 1.3, seed: 0.5, axis: [0.3, 0.5, 0.8], colorA: [0.3, 0.8, 1.0, 1], colorB: [0, 0, 0, 1], params0: [2, 0.15, 1.0, 0.0], params1: [3.0, 0.35, 1.0, 0], bound: 1.05 },
  { type: 'proxy', shader: 'volume/orb', center: [5.2, 1.6, -9], radius: 1.0, time: 1.3, seed: 0.7, colorA: [0.7, 0.15, 1.0, 1], colorB: [0.25, 0.0, 0.4, 1], params0: [3, 0.6, 1.0, 0.6], params1: [1.0, 0, 1.0, 0], bound: 1.7 }], post: {} });
// 3: singularidade
frames.push({ label: 'blackhole', time: 2.1, day: 1, camera: cam, items: [
  { type: 'proxy', shader: 'volume/blackhole', center: [0, 3.0, -9], radius: 0.9, axis: [0.15, 1, 0.25], time: 2.1, seed: 0.3, colorA: [1.0, 0.55, 0.2, 1], colorB: [0.3, 0.1, 0.5, 1], params0: [4.2, 1.0, 1.0, 1.0], distort: true, bound: 6.8 }], post: {} });
// 4: pilares
frames.push({ label: 'pillars', time: 0.9, camera: cam, items: [
  { type: 'proxy', shader: 'volume/pillar', center: [-4.5, 0, -10], radius: 0.9, time: 0.9, seed: 0.2, colorA: [1.0, 0.85, 0.4, 1], colorB: [0.3, 0.2, 0.0, 1], params0: [14, 3.0, 0.5, 1.0], params1: [0, 0.22, 0, 0], bound: 16, anchor: 'pillar' },
  { type: 'proxy', shader: 'volume/pillar', center: [4.0, 0, -11], radius: 2.6, time: 0.9, seed: 0.5, colorA: [0.5, 0.65, 1.0, 1], colorB: [0.16, 0.17, 0.2, 1], params0: [9, 1.5, 1.2, 0.8], params1: [1, 0.2, 1.3, 0], bound: 10, anchor: 'pillar' }], post: {} });
// 5: marcas no chao
const dec = (x, z, style, colorA, colorB, extra) => Object.assign({ type: 'proxy', shader: 'volume/decal', center: [x, 0, z], radius: 1.9, age: 0.15, time: 1.7, seed: 0.3 + style * 0.1, colorA, colorB: colorB || [0, 0, 0, 1], params0: [style, 0.6, 1.0, 1.5], params1: [1, 0, 0, 0], bound: 1.1 }, extra || {});
frames.push({ label: 'decals', time: 1.7, day: 1, camera: { pos: [0, 4.5, 0], pitch: 38 }, items: [
  dec(-4.4, -5.5, 0, [0.6, 0.3, 1.0, 1]), dec(0, -5.5, 1, [1.0, 0.45, 0.1, 1]), dec(4.4, -5.5, 2, [0.6, 0.85, 1.0, 1]),
  dec(-4.4, -10, 3, [1.0, 0.3, 0.1, 1]), dec(0, -10, 4, [0.6, 0.1, 0.9, 1], [0.2, 0.0, 0.3, 1]), dec(4.4, -10, 5, [1.0, 0.2, 0.2, 1])], post: {} });
// 6: cristais
frames.push({ label: 'crystal', time: 1.0, day: 1, camera: cam, items: [
  { type: 'proxy', shader: 'volume/crystal', center: [-2.5, 0, -7], radius: 2.6, time: 1.0, seed: 0.3, colorA: [0.55, 0.8, 1.0, 1], colorB: [0.2, 0.5, 1.0, 1], params0: [1.0, 8, 1.0, 0.25], bound: 1.0 },
  { type: 'proxy', shader: 'volume/crystal', center: [3.0, 0, -8], radius: 2.2, time: 1.0, seed: 0.8, colorA: [0.55, 0.8, 1.0, 1], colorB: [0.2, 0.5, 1.0, 1], params0: [0.45, 6, 1.0, 0.8], bound: 1.0 }],
  lights: [{ pos: [-2.5, 1.2, -7], radius: 7, color: [0.5, 0.9, 1.6] }], post: {} });
return { cols: 3, frames };
