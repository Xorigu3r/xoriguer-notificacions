"""Genera el logo i les icones de Xoriguer Notificacions (falcó de la casa + campana de notificació).

Ús:  python -I eines/genera_logo.py   (des de l'arrel del projecte)
Llenç de 512 com Aparcament/Radars: falcó a (36, 66) amb 440 d'ample, motiu del projecte a sota.
Rètol (logo complet): marca.py de LOGOS-XORIGUER (X original + Montserrat ExtraBold + Segoe UI).
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ARREL = Path(__file__).resolve().parent.parent
WWW = ARREL / 'www'
ICONS = WWW / 'icons'
ASSETS = ARREL / 'assets'
sys.path.insert(0, str(Path('C:/Users/Xorigu3r/Documents/LOGOS-XORIGUER/_eines')))
import marca  # noqa: E402

FONS = (10, 11, 14, 255)
CREMA = (246, 239, 226, 255)
TARONJA = (255, 168, 46, 255)
TARONJA_FOSC = (242, 101, 15, 255)
VERMELL = (217, 52, 30, 255)
S = 4


def cub(p0, p1, p2, p3, n=24):
    return [(((1 - t) ** 3) * p0[0] + 3 * ((1 - t) ** 2) * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t ** 3 * p3[0],
             ((1 - t) ** 3) * p0[1] + 3 * ((1 - t) ** 2) * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t ** 3 * p3[1])
            for t in (i / n for i in range(1, n + 1))]


def campana():
    """Contorn del cos de la campana (sense la vora), centre x=256."""
    esq = [(150, 404)]
    esq += cub(esq[-1], (174, 388), (180, 360), (182, 322))
    esq += cub(esq[-1], (185, 276), (214, 252), (256, 252))
    dre = [(512 - x, y) for x, y in reversed(esq)]
    return esq + dre[1:]


def degradat_vertical(N, c1, c2, y0, y1):
    g = Image.new('RGBA', (1, N))
    for y in range(N):
        t = min(1, max(0, (y - y0) / max(1, y1 - y0)))
        g.putpixel((0, y), tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4)))
    return g.resize((N, N))


def dibuixa(mida, escala=1.0, amb_fons=True, rodo=False, amb_falco=True):
    N = 512 * S
    k = S
    P = lambda pts: [(x * k, y * k) for x, y in pts]
    capa = Image.new('RGBA', (N, N), (0, 0, 0, 0))

    # resplendor taronja darrere la campana
    g = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(g).ellipse([150 * k, 250 * k, 362 * k, 450 * k], fill=TARONJA[:3] + (90,))
    capa.alpha_composite(g.filter(ImageFilter.GaussianBlur(40 * k)))

    if amb_falco:
        falco = Image.open(ICONS / 'falco.png').convert('RGBA')
        fw = 440 * k
        falco = falco.resize((fw, round(fw * falco.height / falco.width)), Image.LANCZOS)
        capa.alpha_composite(falco, (36 * k, 66 * k))

    d = ImageDraw.Draw(capa)
    # ones de so (crema a dins, taronja a fora)
    cx, cy = 256, 340
    for r, col, obert in ((150, CREMA, 22), (190, TARONJA, 18)):
        for mig in (180, 0):
            d.arc([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], mig - obert, mig + obert,
                  fill=col, width=9 * k)

    # cos de la campana amb degradat
    cos = campana()
    m = Image.new('L', (N, N), 0)
    ImageDraw.Draw(m).polygon(P(cos), fill=255)
    capa.paste(degradat_vertical(N, TARONJA, TARONJA_FOSC, 252 * k, 404 * k), (0, 0), m)
    d = ImageDraw.Draw(capa)
    d.line(P(cos), fill=CREMA, width=6 * k, joint='curve')
    # reflex
    d.line(P(cub((206, 380), (214, 350), (210, 300), (236, 276), 16)), fill=(255, 236, 205, 200), width=7 * k, joint='curve')
    # anella de dalt
    d.ellipse([244 * k, 228 * k, 268 * k, 252 * k], outline=CREMA, width=6 * k)
    # vora
    d.rounded_rectangle([136 * k, 400 * k, 376 * k, 424 * k], radius=12 * k, fill=CREMA)
    # batall
    d.pieslice([228 * k, 408 * k, 284 * k, 464 * k], 0, 180, fill=CREMA)
    # punt d'avís (insígnia)
    d.ellipse([318 * k, 236 * k, 378 * k, 296 * k], fill=VERMELL, outline=FONS, width=7 * k)
    d.ellipse([340 * k, 258 * k, 356 * k, 274 * k], fill=CREMA)

    if escala != 1.0:
        mm = round(N * escala)
        petita = capa.resize((mm, mm), Image.LANCZOS)
        capa = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        capa.alpha_composite(petita, ((N - mm) // 2, (N - mm) // 2))
    im = Image.new('RGBA', (N, N), FONS if amb_fons else (0, 0, 0, 0))
    im.alpha_composite(capa)
    if rodo:
        mk = Image.new('L', (N, N), 0)
        ImageDraw.Draw(mk).rounded_rectangle([0, 0, N - 1, N - 1], radius=N // 5, fill=255)
        im.putalpha(mk)
    return im.resize((mida, mida), Image.LANCZOS)


def logo_complet(mida=1024):
    im = Image.new('RGBA', (mida, mida), FONS)
    simbol = dibuixa(720, amb_fons=False)
    im.alpha_composite(simbol, ((mida - 720) // 2, 10))
    marca.enganxa(im, marca.rotul('XORIGUER', 'NOTIFICACIONS', amplada=int(mida * 0.74)), mida / 2, mida * 0.735)
    return im


if __name__ == '__main__':
    ASSETS.mkdir(exist_ok=True)
    dibuixa(512).save(ICONS / 'icon-512.png')
    dibuixa(192).save(ICONS / 'icon-192.png')
    dibuixa(512, escala=0.78).save(ICONS / 'maskable-512.png')
    dibuixa(180, escala=0.92).convert('RGB').save(ICONS / 'apple-touch-icon.png')
    dibuixa(64, rodo=True).save(ICONS / 'favicon.png')
    dibuixa(512, amb_fons=False).save(ICONS / 'simbol.png')
    logo_complet().convert('RGB').save(ICONS / 'logo-complet.png')
    # Android (@capacitor/assets)
    dibuixa(1024, escala=0.92).convert('RGB').save(ASSETS / 'icon-only.png')
    dibuixa(1024, escala=0.6, amb_fons=False).save(ASSETS / 'icon-foreground.png')
    Image.new('RGB', (1024, 1024), FONS[:3]).save(ASSETS / 'icon-background.png')
    sp = Image.new('RGBA', (2732, 2732), FONS)
    sp.alpha_composite(logo_complet(1000), (866, 866))
    sp.convert('RGB').save(ASSETS / 'splash.png')
    sp.convert('RGB').save(ASSETS / 'splash-dark.png')
    print('Icones generades a', ICONS)
