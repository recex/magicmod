#!/usr/bin/env python3
"""Publica o resultado do autoteste grafico (cliente real, GL por software).

Le run/selftest/report.txt e os quadros PNG gravados pelo mod e publica:

  * check run "relatorio-autoteste": relatorio + trechos do log do jogo;
  * check runs "quadros-autoteste-N": os quadros em JPEG (base64), para que a
    revisao consiga olhar o resultado visual sem baixar artefatos.

Sai com codigo 1 se o autoteste nao terminou com "RESULT: PASS".
"""
import base64
import glob
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request

LIMITE = 60000


def ler(caminho, limite=None):
    try:
        with open(caminho, "r", encoding="utf-8", errors="replace") as f:
            dados = f.read()
    except OSError:
        return ""
    return dados if limite is None else dados[-limite:]


def criar_check_run(nome, titulo, resumo, texto, conclusao):
    token = os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    sha = os.environ.get("CHECK_SHA") or os.environ.get("GITHUB_SHA")
    if not (token and repo and sha):
        print("selftest_report: sem credenciais, check run '%s' ignorado" % nome)
        return
    corpo = {
        "name": nome, "head_sha": sha, "status": "completed", "conclusion": conclusao,
        "output": {"title": titulo[:250], "summary": resumo[:65000], "text": texto[:65000]},
    }
    req = urllib.request.Request(
        "https://api.github.com/repos/%s/check-runs" % repo,
        data=json.dumps(corpo).encode("utf-8"), method="POST",
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json",
                 "User-Agent": "magicmod-ci-report"})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            print("selftest_report: check run '%s' criado (HTTP %s)" % (nome, resp.status))
    except urllib.error.HTTPError as e:
        print("selftest_report: falha em '%s': HTTP %s %s" % (nome, e.code, e.read()[:300]))
    except Exception as e:
        print("selftest_report: falha em '%s': %s" % (nome, e))


def log_filtrado(log):
    linhas = log.splitlines()
    marcas = re.compile(r"autoteste|magicmod|MagicMod|ERROR|FATAL|Exception|Caused by|\tat com\.arcanemod|"
                        r"OpenGL|GLFW|Backend library|Failed|crash|Crash|shader|Shader")
    manter = set()
    for i, linha in enumerate(linhas):
        if marcas.search(linha):
            manter.update(range(i, min(len(linhas), i + 3)))
    manter.update(range(max(0, len(linhas) - 40), len(linhas)))
    saida, anterior = [], -1
    for j in sorted(manter):
        if j != anterior + 1:
            saida.append("...")
        saida.append(linhas[j][:400])
        anterior = j
    return "\n".join(saida)


def quadros_em_jpeg(pasta, qualidade):
    try:
        from PIL import Image
    except ImportError:
        print("selftest_report: Pillow ausente, quadros nao publicados")
        return []
    saida = []
    for caminho in sorted(glob.glob(os.path.join(pasta, "*.png"))):
        try:
            img = Image.open(caminho).convert("RGB")
            buf = io.BytesIO()
            img.save(buf, "JPEG", quality=qualidade, optimize=True)
            saida.append((os.path.splitext(os.path.basename(caminho))[0], base64.b64encode(buf.getvalue()).decode("ascii")))
        except Exception as e:
            print("selftest_report: nao consegui converter %s: %s" % (caminho, e))
    return saida


def main():
    pasta = sys.argv[1] if len(sys.argv) > 1 else "run/selftest"
    log_console = sys.argv[2] if len(sys.argv) > 2 else "selftest.log"
    relatorio = ler(os.path.join(pasta, "report.txt"))
    passou = "RESULT: PASS" in relatorio

    partes = ["== RELATORIO ==", relatorio or "(report.txt ausente: o jogo nao chegou a iniciar o autoteste)", ""]
    extra = ler("glxinfo.txt", 3000)
    if extra:
        partes += ["== GLXINFO ==", extra, ""]
    jogo = ler("run/logs/latest.log") or ler(log_console)
    partes += ["== LOG DO JOGO (filtrado) ==", log_filtrado(jogo)[-30000:], ""]
    if not relatorio:
        partes += ["== CONSOLE (final) ==", ler(log_console, 12000)]
    for crash in sorted(glob.glob("run/crash-reports/*.txt"))[-1:]:
        partes += ["== CRASH REPORT ==", ler(crash)[:15000]]
    texto = "```\n" + "\n".join(partes)[:LIMITE] + "\n```"
    falhas = [l for l in relatorio.splitlines() if l.startswith("FAIL")]
    resumo = "Autoteste grafico: **%s** - %d falha(s), %d quadro(s)." % (
        "PASS" if passou else "FAIL", len(falhas), relatorio.count("FRAME: "))
    criar_check_run("relatorio-autoteste", "Autoteste %s" % ("PASS" if passou else "FAIL"), resumo, texto,
                    "success" if passou else "failure")

    quadros = quadros_em_jpeg(pasta, int(os.environ.get("QUADRO_QUALIDADE", "62")))
    lote, tamanho, indice = [], 0, 1
    orcamento = 2 * LIMITE - 4000

    def publicar(lote, indice):
        dados = json.dumps([{"name": n, "jpeg": b} for n, b in lote])
        criar_check_run("quadros-autoteste-%d" % indice, "Quadros %s" % ", ".join(n for n, _ in lote)[:200],
                        "```\n" + dados[:LIMITE] + "\n```", "```\n" + dados[LIMITE:] + "\n```", "neutral")

    for nome, b64 in quadros:
        if len(b64) > orcamento:
            print("selftest_report: quadro %s grande demais (%d), ignorado" % (nome, len(b64)))
            continue
        if lote and tamanho + len(b64) + 64 > orcamento:
            publicar(lote, indice)
            lote, tamanho, indice = [], 0, indice + 1
        lote.append((nome, b64))
        tamanho += len(b64) + 64
    if lote:
        publicar(lote, indice)

    resumo_arq = os.environ.get("GITHUB_STEP_SUMMARY")
    if resumo_arq:
        with open(resumo_arq, "a", encoding="utf-8") as f:
            f.write(resumo + "\n\n```\n" + relatorio[:20000] + "\n```\n")
    return 0 if passou else 1


if __name__ == "__main__":
    sys.exit(main())
