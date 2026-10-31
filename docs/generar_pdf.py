"""Genera los PDF de la documentación a partir de los HTML de docs/ con Chrome (Playwright).

Los HTML usan el formato APA de las entregas anteriores (que se generaban con WeasyPrint). Chrome dibuja el
número de página con la caja de margen @top-right de la hoja de estilos, pero no soporta target-counter(), así
que este script completa los números de página del índice en dos pasadas: genera el PDF, busca en qué página
quedó cada título y vuelve a generarlo con esos números.

Requisitos: pip install playwright pymupdf  (y Google Chrome instalado)
Uso:  python docs/generar_pdf.py documento-tecnico/documento-tecnico-entrega4.html documento-tecnico/MediConecta_Documento_Tecnico_Entrega4.pdf
"""
import pathlib
import re
import sys

import pymupdf
from playwright.sync_api import sync_playwright

def imprimir(navegador, html: pathlib.Path, pdf: pathlib.Path):
    pagina = navegador.new_page()
    pagina.goto(html.resolve().as_uri())
    pagina.wait_for_load_state("networkidle")
    pagina.pdf(path=str(pdf), format="A4", print_background=True, prefer_css_page_size=True)
    pagina.close()


def titulos(html_texto: str):
    """id -> texto del título (h1/h2 con id), sin etiquetas."""
    encontrados = {}
    for m in re.finditer(r'<h[12][^>]*\bid="([^"]+)"[^>]*>(.*?)</h[12]>', html_texto, re.S):
        encontrados[m.group(1)] = re.sub(r"<[^>]+>", "", m.group(2)).strip()
    return encontrados


def paginas_de(pdf: pathlib.Path, buscados: dict, desde: int):
    doc = pymupdf.open(str(pdf))
    textos = [" ".join(doc[i].get_text().split()) for i in range(len(doc))]
    resultado = {}
    for ident, texto in buscados.items():
        normal = " ".join(texto.split())
        for i in range(desde, len(textos)):
            if normal in textos[i]:
                resultado[ident] = i + 1
                break
    return resultado, len(doc)


def main(entrada: str, salida: str):
    base = pathlib.Path(__file__).parent
    html = (base / entrada)
    pdf = (base / salida)
    texto = html.read_text(encoding="utf-8")
    with sync_playwright() as p:
        navegador = p.chromium.launch(channel="chrome", headless=True)
        temporal = html.with_name(html.stem + ".tmp.html")
        temporal.write_text(texto, encoding="utf-8")
        imprimir(navegador, temporal, pdf)
        indice_hasta = 2  # carátula (1) e índice (2): los títulos se buscan desde la página 3
        paginas, total = paginas_de(pdf, titulos(texto), indice_hasta)
        con_numeros = re.sub(r'<span class="pag" data-para="([^"]+)"></span>',
                             lambda m: '<span class="pag" data-para="%s">%s</span>' % (m.group(1), paginas.get(m.group(1), "")),
                             texto)
        temporal.write_text(con_numeros, encoding="utf-8")
        imprimir(navegador, temporal, pdf)
        temporal.unlink()
        navegador.close()
    faltan = [i for i in titulos(texto) if i not in paginas]
    print(f"{pdf.name}: {total} páginas" + (f" (sin página en el índice: {faltan})" if faltan else ""))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
