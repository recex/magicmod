// steps = 32
const frames = [];
const ages = [0.04, 0.12, 0.25, 0.42, 0.62, 0.85];
for (const age of ages) {
  const life = 1.6; const t = age * life;
  const grow = 1 - Math.pow(1 - Math.min(age / 0.5, 1), 3);
  const radius = 0.6 + grow * 3.2;
  const heat = Math.pow(1 - age, 1.5);
  frames.push({
    label: 'fireball age=' + age, time: t, day: 0,
    camera: { pos: [0, 1.62, 0], yaw: 0, pitch: 4, fov: 70 },
    items: [{ type: 'proxy', shader: 'volume/fireball', center: [0, 1.6 + age * 1.2, -9], radius, age, time: t, seed: 0.37,
      colorA: [1.0, 0.42, 0.10, 1], colorB: [0.05, 0.04, 0.04, 0.85], params0: [1.0, 0.6, 1.0, 1.0] }],
    lights: [{ pos: [0, 1.5, -9], radius: 14, color: [5.0 * heat, 2.0 * heat, 0.5 * heat] }],
    post: { bloom: 0.25 },
  });
}
return { cols: 3, frames };
