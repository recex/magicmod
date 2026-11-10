// Laboratorio de shaders: compila os GLSL do mod em WebGL2 (GLSL ES 3.00)
// num Chromium sem tela e renderiza cenarios de teste em PNG.
// Uso: AWS_EXECUTION_ENV=AWS_Lambda_nodejs22.x node lab.mjs <cenario.js> <saida.png> [largura altura]
import chromium from '@sparticuz/chromium';
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const SHADER_DIR = process.env.SHADER_DIR || path.resolve(here, '../../src/main/resources/assets/magicmod/shaders/vfx');

function readShader(name) {
  const local = path.join(here, name);
  if (fs.existsSync(local)) return fs.readFileSync(local, 'utf8');
  return fs.readFileSync(path.join(SHADER_DIR, name), 'utf8');
}
function preprocess(name, seen = new Set()) {
  const out = [];
  for (const line of readShader(name).split('\n')) {
    const m = line.match(/^\s*#include\s+"([^"]+)"/);
    if (m) { if (!seen.has(m[1])) { seen.add(m[1]); out.push(preprocess(m[1], seen)); } }
    else out.push(line);
  }
  return out.join('\n');
}
function allShaders() {
  const res = {};
  const walk = (dir, rel) => { for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f); const r = rel ? rel + '/' + f : f;
    if (fs.statSync(p).isDirectory()) { if (f !== 'lib') walk(p, r); }
    else if (/\.(vsh|fsh)$/.test(f)) res[r] = preprocess(r); } };
  walk(SHADER_DIR, '');
  res['labscene.fsh'] = preprocess('labscene.fsh');
  return res;
}

const [scenarioFile, outFile, wArg, hArg] = process.argv.slice(2);
const W = parseInt(wArg || '960'), H = parseInt(hArg || '540');
const sources = allShaders();
const scenarioSrc = scenarioFile ? fs.readFileSync(scenarioFile, 'utf8') : 'return {frames: []};';
const pageSrc = fs.readFileSync(path.join(here, 'page.js'), 'utf8');

chromium.setGraphicsMode = true;
const browser = await puppeteer.launch({ args: chromium.args.filter(a => !a.startsWith('--single-process')),
  executablePath: await chromium.executablePath(), headless: 'shell', defaultViewport: { width: 1920, height: 1200 } });
const page = await browser.newPage();
page.on('console', m => console.log('[page]', m.text()));
page.on('pageerror', e => console.log('[pageerror]', e.message));
await page.setContent('<body style="margin:0;background:#000"><canvas id="out"></canvas></body>');
await page.addScriptTag({ content: pageSrc });
const result = await page.evaluate(async (sources, scenarioSrc, W, H) => {
  try { return await window.labRun(sources, scenarioSrc, W, H); } catch (e) { return { error: String(e && e.stack || e) }; }
}, sources, scenarioSrc, W, H);
if (result.error) { console.log('ERRO:', result.error); await browser.close(); process.exit(1); }
for (const line of result.log || []) console.log(line);
if (outFile && result.width) {
  const el = await page.$('#out');
  await el.screenshot({ path: outFile });
  console.log('gravado', outFile, result.width + 'x' + result.height, 'ms/quadro', result.msPerFrame);
}
await browser.close();
process.exit(result.failed ? 2 : 0);
