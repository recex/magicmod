#!/usr/bin/env python3
"""Publica o resultado do build como anotacoes e como um check run de texto.

O log bruto do GitHub Actions nem sempre esta ao alcance de quem (ou do que)
revisa o build. Este script le o log do Gradle gravado pelo passo de build e:

  * emite ate 10 anotacoes ``::error file=...,line=...::`` para os erros do
    javac, que aparecem direto no diff do commit/PR;
  * cria um check run "relatorio-build" com o trecho relevante do log (erros de
    compilacao, testes que falharam, causa da falha do Gradle).

So usa a biblioteca padrao do Python. Nunca falha o job: relatorio e
diagnostico, quem decide o resultado e o passo de build.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

LIMITE_TEXTO = 60000  # a API de checks aceita 65535 caracteres por campo


def ler(caminho):
    try:
        with open(caminho, "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return ""


def escapar(valor):
    return valor.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escapar_prop(valor):
    return escapar(valor).replace(":", "%3A").replace(",", "%2C")


def erros_javac(log, raiz):
    padrao = re.compile(r"^(?P<arq>/[^:\n]+\.java):(?P<lin>\d+): (?P<tipo>error|warning): (?P<msg>.*)$")
    achados = []
    linhas = log.splitlines()
    for i, linha in enumerate(linhas):
        m = padrao.match(linha)
        if not m or m.group("tipo") != "error":
            continue
        detalhe = [m.group("msg")]
        for extra in linhas[i + 1:i + 7]:
            if padrao.match(extra) or re.match(r"^\d+ errors?$", extra.strip()):
                break
            detalhe.append(extra)
        arq = m.group("arq")
        if arq.startswith(raiz + "/"):
            arq = arq[len(raiz) + 1:]
        achados.append((arq, int(m.group("lin")), "\n".join(detalhe).rstrip()))
    return achados


def trecho_relevante(log):
    linhas = log.splitlines()
    marcas = re.compile(
        r"error:|FAILED|FAILURE:|What went wrong|Caused by:|Exception|"
        r"tests completed|> Task .*(compileJava|compileTestJava|test|reobfJar|jar)\b|BUILD ")
    manter = set()
    for i, linha in enumerate(linhas):
        if marcas.search(linha):
            for j in range(max(0, i - 1), min(len(linhas), i + 8)):
                manter.add(j)
    for j in range(max(0, len(linhas) - 60), len(linhas)):
        manter.add(j)
    saida, anterior = [], -1
    for j in sorted(manter):
        if j != anterior + 1:
            saida.append("...")
        saida.append(linhas[j])
        anterior = j
    return "\n".join(saida)


def criar_check_run(nome, titulo, resumo, texto, conclusao):
    token = os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    sha = os.environ.get("CHECK_SHA") or os.environ.get("GITHUB_SHA")
    if not (token and repo and sha):
        print("ci_report: sem GITHUB_TOKEN/REPOSITORY/SHA, check run ignorado")
        return
    corpo = {
        "name": nome,
        "head_sha": sha,
        "status": "completed",
        "conclusion": conclusao,
        "output": {"title": titulo[:250], "summary": resumo[:LIMITE_TEXTO], "text": texto[-LIMITE_TEXTO:]},
    }
    req = urllib.request.Request(
        "https://api.github.com/repos/%s/check-runs" % repo,
        data=json.dumps(corpo).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
            "User-Agent": "magicmod-ci-report",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            print("ci_report: check run '%s' criado (HTTP %s)" % (nome, resp.status))
    except urllib.error.HTTPError as e:
        print("ci_report: falha ao criar check run '%s': HTTP %s %s" % (nome, e.code, e.read()[:300]))
    except Exception as e:  # rede fora do ar etc.
        print("ci_report: falha ao criar check run '%s': %s" % (nome, e))


def main():
    caminho_log = sys.argv[1] if len(sys.argv) > 1 else "build.log"
    resultado = (sys.argv[2] if len(sys.argv) > 2 else "unknown").lower()
    raiz = os.environ.get("GITHUB_WORKSPACE", os.getcwd())
    log = ler(caminho_log)

    erros = erros_javac(log, raiz)
    for arq, lin, msg in erros[:10]:
        print("::error file=%s,line=%d,title=javac::%s" % (escapar_prop(arq), lin, escapar(msg)))

    ok = resultado == "success"
    resumo = "Build: **%s** - %d erro(s) de compilacao no log (%d linhas)." % (resultado, len(erros), log.count("\n"))
    partes = []
    if erros:
        partes.append("== ERROS DE COMPILACAO (%d) ==" % len(erros))
        for arq, lin, msg in erros:
            partes.append("%s:%d: %s" % (arq, lin, msg))
        partes.append("")
    partes.append("== TRECHOS DO LOG ==")
    partes.append(trecho_relevante(log) if log else "(log vazio ou ausente: %s)" % caminho_log)
    texto = "```\n" + "\n".join(partes)[-(LIMITE_TEXTO - 10):] + "\n```"
    criar_check_run("relatorio-build", "Build %s" % resultado, resumo, texto, "success" if ok else "failure")

    resumo_arq = os.environ.get("GITHUB_STEP_SUMMARY")
    if resumo_arq:
        with open(resumo_arq, "a", encoding="utf-8") as f:
            f.write(resumo + "\n\n" + texto[:20000] + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
