<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# PDF test fixtures

`generate_fixtures.py` writes the viewer's test PDFs, together with a `manifest.json`, to
`composepdf/src/androidTest/assets/fixtures/`. It needs Python 3 and nothing else.

```sh
python testing/pdf-fixtures/generate_fixtures.py            # default output folder
python testing/pdf-fixtures/generate_fixtures.py <folder>   # somewhere else
```

The PDFs are committed, and tests never run the script. If you change the script, regenerate and
commit the PDFs too. `:app`'s instrumented tests read the same folder.

## Why generated

- **Expected positions are computed, not measured.** All text is Courier: monospaced, 600/1000 em a
  character. The script therefore knows where every word and link sits, and writes it to
  `manifest.json` in the viewer's coordinates: normalized to the displayed page, top-left origin,
  crop box and rotation applied.
- **No third-party fonts or documents**, so there are no licensing questions.
- **Reproducible**: same script, same bytes.

## The fixtures

| File | Covers |
|---|---|
| `text-and-links.pdf` | Accented text. Links: `https`, `http`, `mailto`, `tel`, `javascript:`, internal ones in four forms, and one link spread over two lines. Page 2 has a paragraph for multi-line selection. |
| `scanned-image-only.pdf` | Image-only pages with no text layer. It *simulates* ML Kit's output; it is not a real scan. |
| `mixed-text-and-scanned.pdf` | Text, scan, text: whether there is text is decided page by page. |
| `long-320-pages.pdf` | Scrolling, restoration and extraction over many pages. |
| `password-protected.pdf` | 40-bit RC4, user password `docucraft`. `PdfRenderer` refuses it. |
| `rotated-mixed-sizes.pdf` | A4, A5, landscape A4, A4 with `/Rotate 90`, A4 with a crop box. Each page has the word `TARGET` with a link over it. |
| `prueba_motor_pdf.pdf` | *Added by hand*, not described in the manifest. 40 pages produced by ReportLab: a table of contents with internal links, typography, tables, code, images, vector graphics. It is the densest text fixture, and the proof that the platform reports no internal links. |

`PdfFixturesTest` checks that each generated fixture opens with the page count the manifest says,
and that the protected one is refused, before any other test relies on them.

**Never add a document with personal data.** To reproduce a real document's layout, build an
equivalent with invented content.

## Missing

- **A real Docucraft scan**, with ML Kit's JPEG compression and metadata.
- **Right-to-left (Arabic) and CJK text.** These need embedded fonts with `ToUnicode` maps, and
  Arabic also needs shaping. Noto fonts (OFL licence) would do.
