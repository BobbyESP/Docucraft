<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# PDFs de prueba del visor

Los genera `generate_fixtures.py` (Python 3, **solo biblioteca estándar**) en
`composepdf/src/androidTest/assets/fixtures/`, junto con `manifest.json`:

```sh
python testing/pdf-fixtures/generate_fixtures.py            # salida por defecto
python testing/pdf-fixtures/generate_fixtures.py <carpeta>  # otra salida (p. ej. los tests de :app)
```

Los PDF se versionan: los tests no ejecutan el script. Si se cambia el script, se regeneran y se
versionan también los nuevos.

## Por qué generados y no descargados

- **Las posiciones esperadas se calculan, no se miden.** Todo el texto está en Courier (monoespaciada,
  600/1000 em por carácter), así que el script sabe exactamente dónde cae cada palabra y cada enlace,
  y lo escribe en `manifest.json` en el sistema de coordenadas del visor: normalizado a la página
  mostrada, `[0,1] × [0,1]`, origen arriba a la izquierda, con CropBox y `/Rotate` aplicados.
- **Licencias limpias**: no hay fuentes ni documentos de terceros dentro.
- **Reproducibles**: mismo script, mismos bytes.

## Contenido

| Archivo | Qué prueba |
|---|---|
| `text-and-links.pdf` | Texto con acentos; enlaces `https`, `http`, `mailto`, `tel` y `javascript:`; internos con posición (`XYZ`), sin ella (`Fit`), con zoom explícito y por acción `/A /GoTo`; un enlace repartido en dos líneas (`QuadPoints`). Página 2: párrafo para la selección entre líneas |
| `scanned-image-only.pdf` | Páginas que son solo una imagen, sin capa de texto. **Simula** la salida de ML Kit; no es un escaneo real |
| `mixed-text-and-scanned.pdf` | Página con texto, página escaneada, página con texto: la disponibilidad se decide por página |
| `long-320-pages.pdf` | Rendimiento de extracción y scroll; también lo usa el test de restauración de B1 |
| `password-protected.pdf` | RC4 de 40 bits, contraseña de usuario `docucraft`: `PdfRenderer` lo rechaza |
| `rotated-mixed-sizes.pdf` | A4, A5, A4 apaisado, A4 con `/Rotate 90`, A4 con CropBox. Cada página tiene la palabra `TARGET` con un enlace encima, para comparar el texto, el enlace y la posición esperada |

`PdfFixturesTest` (en `:composepdf`) comprueba que todos abren con el número de páginas del manifiesto
y que el protegido se rechaza, antes de que ningún otro test se apoye en ellos.

## Pendientes

- **Un escaneo real de Docucraft** (`docucraft-scan.pdf`). El simulado reproduce «imagen sin texto»,
  pero no la compresión JPEG ni los metadatos de ML Kit. Hay que escanear en un dispositivo y copiarlo
  aquí.
- **Texto RTL (árabe) y CJK.** Necesitan fuentes incrustadas con su `ToUnicode`, y en árabe además
  *shaping*. Las fuentes de Windows no se pueden redistribuir. Opciones: generarlos con fuentes
  Noto (licencia OFL), o añadir documentos reales cuya licencia lo permita.
