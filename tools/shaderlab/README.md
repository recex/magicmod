# Laboratório de shaders

Ferramenta de desenvolvimento (não entra no jar). Compila **todos** os shaders de
`src/main/resources/assets/magicmod/shaders/vfx` em WebGL2 dentro de um Chromium
sem tela e renderiza cenários de teste em PNG — dá para ajustar a aparência de um
efeito em 2–3 segundos, sem abrir o Minecraft.

```
cd tools/shaderlab
npm install
node lab.mjs sc_vol.js saida.png 640 360
```

Em ambientes sem as bibliotecas de sistema do Chromium (contêineres mínimos),
rode com `AWS_EXECUTION_ENV=AWS_Lambda_nodejs22.x` para que o pacote
`@sparticuz/chromium` use as bibliotecas que ele mesmo traz.

- `lab.mjs` — resolve os `#include`, sobe o Chromium, grava a folha de contato.
  Imprime `programas: N falhas: M` e termina com código 2 se algum shader não compilar.
- `page.js` — mini-motor WebGL2 que espelha o `RenderEngine` do mod (buffer HDR,
  distorção, bloom, composição, quadriláteros dos volumes, fitas, partículas instanciadas).
- `labscene.fsh` — cenário falso (chão, blocos, céu) que grava profundidade.
- `sc_*.js` — cenários: cada quadro lista os itens desenhados e os parâmetros usados.

Os shaders são escritos no subconjunto comum de GLSL 1.50 (o que o jogo usa) e
GLSL ES 3.00 (o que o WebGL2 usa); o laboratório pega erros de sintaxe e de tipo,
mas a validação final é o autoteste dentro do jogo (ver `.github/workflows/build.yml`).
