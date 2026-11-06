# Arcane Spells — Mod de Magia para Minecraft Forge 1.20.1

Mod de magia com 30 feitiços em 8 escolas (Fogo, Gelo, Arcano, Natureza,
Sombra, Luz, Tempestade, Sangue), sistema de mana/cooldown por jogador,
partículas customizadas e shader de pós-processamento de tela cheia
para os efeitos de impacto.

## Como compilar

Este repositório está configurado para compilar **automaticamente via
GitHub Actions** a cada push na branch `main`/`master`. Não é preciso
ter Java ou Gradle instalados para isso.

1. Suba este projeto para um repositório no GitHub.
2. Vá na aba **Actions** do repositório.
3. Espere o workflow "Build Mod" rodar (alguns minutos).
4. Se a build passar (ficar verde ✅), baixe o `.jar` gerado no final
   da execução, em **Artifacts > magicmod-jar**.
5. Se a build falhar (ficar vermelha ❌), clique nela, abra o passo
   "Build with Gradle" e leia a mensagem de erro — ela diz exatamente
   qual arquivo e linha tem problema.

## ⚠️ Pontos de atenção conhecidos

Este mod foi escrito inteiramente por IA, **sem poder ser compilado ou
testado localmente** antes de chegar até você (o ambiente usado para
gerá-lo não tem acesso à internet nem ao Gradle/Forge). A estrutura e
a lógica seguem as APIs documentadas do Forge 1.20.1, mas alguns
pontos têm risco maior de erro de compilação na primeira tentativa:

- **`client/shader/mixin/PostChainMixin.java`**: usa Mixin para
  atualizar os uniforms do shader (`Intensity`, `EffectColor`) a cada
  frame. Os nomes de método (`process`, `getPasses`, `getEffect`,
  `getUniform`) podem não bater exatamente com o mapeamento oficial
  desta versão. Se o build falhar aqui, o comentário no topo do
  arquivo explica como isolar o problema (esvaziar o método) para o
  resto do mod compilar enquanto você ajusta os nomes.
- **Shaders GLSL** (`spell_distortion.vsh`/`.fsh`): sintaxe revisada
  manualmente, mas shaders só são validados de verdade pela GPU em
  tempo de execução — um erro ali não quebra a compilação do Java,
  mas pode fazer o efeito não aparecer ou o jogo logar um erro de
  shader no console.
- **Texturas**: os PNGs de item/partícula foram gerados
  proceduralmente em Python (não são arte feita à mão) — funcionais,
  mas bem simples. Vale substituir por texturas suas depois.
- **Gradle Wrapper**: este projeto NÃO inclui `gradlew`/
  `gradle-wrapper.jar` (não há rede no ambiente onde foi gerado para
  baixá-los corretamente). O workflow do GitHub Actions não depende
  deles — instala o Gradle 8.1.1 diretamente no runner. Se você quiser
  compilar localmente também, rode `gradle wrapper` uma vez com o
  Gradle instalado na sua máquina para gerá-los.

## Se o build falhar

1. Abra a aba Actions → clique na execução que falhou → abra o log do
   passo "Build with Gradle".
2. Procure a primeira linha que começa com `error:` — ela aponta o
   arquivo e o número da linha exatos.
3. Cole esse trecho de erro de volta para o Claude (ou outro
   assistente) pedindo a correção pontual daquele arquivo.

## Estrutura do projeto

```
src/main/java/com/arcanemod/magicmod/
├── MagicMod.java              # classe principal do mod
├── ServerTickHandler.java     # tick de cooldown/regeneração de mana
├── capability/                # sistema de mana/cooldown por jogador
├── client/                    # setup de cliente, input, VFX, shader
│   ├── particle/               # partícula customizada reutilizável
│   └── shader/                 # gerenciador do shader de pós-processamento
├── item/                      # Grimório e Foco de Magia
├── network/                   # pacotes cliente↔servidor
├── registry/                  # registro de itens, partículas e das 30 magias
└── spell/                     # framework de magias (classe base + 30 implementações)
    └── impl/
```

## Como jogar (depois de instalar o mod)

- Pegue o **Grimório** e o **Foco de Magia** na aba de criativo
  "Ferramentas e Utilitários".
- **Tecla `R`**: segura para carregar e solta para conjurar a magia
  selecionada.
- **Tecla `]`** / **Tecla `[`**: navega entre as 30 magias.
- Use o Grimório (clique direito) para ver qual magia está selecionada
  e seu custo de mana.

## Licença

MIT — ajuste conforme desejar antes de publicar.
