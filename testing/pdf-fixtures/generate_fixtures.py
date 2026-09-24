#!/usr/bin/env python3
# Copyright (C) 2026  Gabriel Fontán (BobbyESP)
"""
Generates the PDF fixtures used by the viewer's instrumented tests, plus a manifest of what each one
is expected to contain.

Standard library only, so it runs on any machine with Python 3 and nothing to install. Everything is
set in Courier, a monospaced standard font (600/1000 em per glyph), which is what lets the manifest
state *exactly* where each word and link sits: the expected rectangles are computed here, not
measured, and the viewer tests compare the platform's answer against them.

Coordinates in the manifest follow the viewer's convention: normalized to the displayed page,
[0,1] x [0,1], top-left origin, after applying CropBox and /Rotate.

Usage:
    python testing/pdf-fixtures/generate_fixtures.py [output_dir]

Default output: composepdf/src/androidTest/assets/fixtures
"""

import hashlib
import json
import random
import sys
import zlib
from pathlib import Path

# --------------------------------------------------------------------------------- PDF object model


class Name:
    def __init__(self, value):
        self.value = value


class Ref:
    def __init__(self, num):
        self.num = num


class Text:
    """A PDF string. Encrypted when the document is."""

    def __init__(self, value):
        self.value = value.encode("cp1252") if isinstance(value, str) else value


class Stream:
    def __init__(self, entries, data, compress=True):
        self.entries = dict(entries)
        self.data = zlib.compress(data) if compress else data
        if compress:
            self.entries["Filter"] = Name("FlateDecode")


def _num(value):
    if isinstance(value, bool):
        return b"true" if value else b"false"
    if isinstance(value, int):
        return str(value).encode()
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return (text if text not in ("", "-0") else "0").encode()


def _escape(raw):
    out = bytearray()
    for b in raw:
        if b in (0x28, 0x29, 0x5C):  # ( ) \
            out += b"\\" + bytes([b])
        elif b < 0x20 or b > 0x7E:
            out += f"\\{b:03o}".encode()
        else:
            out.append(b)
    return bytes(out)


# ----------------------------------------------------------------------- encryption (RC4 40-bit R2)

_PAD = bytes.fromhex(
    "28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A"
)


def _rc4(key, data):
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) % 256
        s[i], s[j] = s[j], s[i]
    i = j = 0
    out = bytearray()
    for b in data:
        i = (i + 1) % 256
        j = (j + s[i]) % 256
        s[i], s[j] = s[j], s[i]
        out.append(b ^ s[(s[i] + s[j]) % 256])
    return bytes(out)


class _StandardSecurity:
    """PDF 1.4 standard security handler, revision 2 (40-bit RC4)."""

    def __init__(self, user_password, owner_password, file_id):
        permissions = -4
        user = (user_password.encode() + _PAD)[:32]
        owner = (owner_password.encode() + _PAD)[:32]
        self.o = _rc4(hashlib.md5(owner).digest()[:5], user)
        self.key = hashlib.md5(
            user + self.o + permissions.to_bytes(4, "little", signed=True) + file_id
        ).digest()[:5]
        self.u = _rc4(self.key, _PAD)
        self.permissions = permissions

    def object_key(self, num):
        return hashlib.md5(self.key + num.to_bytes(3, "little") + b"\x00\x00").digest()[:10]

    def dictionary(self):
        return {
            "Filter": Name("Standard"),
            "V": 1,
            "R": 2,
            "O": Text(self.o),
            "U": Text(self.u),
            "P": self.permissions,
        }


# ---------------------------------------------------------------------------------------- writer


class Document:
    def __init__(self):
        self._objects = []

    def reserve(self):
        self._objects.append(None)
        return Ref(len(self._objects))

    def put(self, ref, obj):
        self._objects[ref.num - 1] = obj
        return ref

    def add(self, obj):
        return self.put(self.reserve(), obj)

    def _serialize(self, obj, num, security):
        if isinstance(obj, Name):
            return b"/" + obj.value.encode()
        if isinstance(obj, Ref):
            return f"{obj.num} 0 R".encode()
        if isinstance(obj, Text):
            raw = obj.value
            if security is not None and num is not None:
                raw = _rc4(security.object_key(num), raw)
            return b"(" + _escape(raw) + b")"
        if obj is None:
            return b"null"
        if isinstance(obj, (bool, int, float)):
            return _num(obj)
        if isinstance(obj, (list, tuple)):
            return b"[" + b" ".join(self._serialize(o, num, security) for o in obj) + b"]"
        if isinstance(obj, dict):
            parts = [b"/" + k.encode() + b" " + self._serialize(v, num, security) for k, v in obj.items()]
            return b"<<" + b" ".join(parts) + b">>"
        if isinstance(obj, Stream):
            data = obj.data
            if security is not None and num is not None:
                data = _rc4(security.object_key(num), data)
            entries = dict(obj.entries)
            entries["Length"] = len(data)
            return self._serialize(entries, num, security) + b"\nstream\n" + data + b"\nendstream"
        raise TypeError(f"Cannot serialize {obj!r}")

    def write(self, path, root, user_password=None):
        file_id = hashlib.md5(str(path.name).encode()).digest()
        security = None
        encrypt_ref = None
        if user_password is not None:
            security = _StandardSecurity(user_password, user_password + "-owner", file_id)
            encrypt_ref = self.add(security.dictionary())

        out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
        offsets = []
        for index, obj in enumerate(self._objects, start=1):
            offsets.append(len(out))
            # The encryption dictionary itself is never encrypted.
            obj_security = None if (encrypt_ref and index == encrypt_ref.num) else security
            out += f"{index} 0 obj\n".encode()
            out += self._serialize(obj, index, obj_security)
            out += b"\nendobj\n"

        xref = len(out)
        out += f"xref\n0 {len(self._objects) + 1}\n".encode()
        out += b"0000000000 65535 f \n"
        for offset in offsets:
            out += f"{offset:010d} 00000 n \n".encode()
        trailer = {
            "Size": len(self._objects) + 1,
            "Root": root,
            "ID": [Text(file_id), Text(file_id)],
        }
        if encrypt_ref:
            trailer["Encrypt"] = encrypt_ref
        out += b"trailer\n" + self._serialize(trailer, None, None) + b"\n"
        out += f"startxref\n{xref}\n%%EOF\n".encode()
        path.write_bytes(bytes(out))


# ------------------------------------------------------------------------------ page composition

A4 = (595, 842)
A5 = (420, 595)
A4_LANDSCAPE = (842, 595)
COURIER_ADVANCE = 0.6  # em per glyph
ASCENT = 0.629  # Courier ascender, em
DESCENT = 0.157  # Courier descender, em


class Page:
    """One page under construction: text runs, images and link annotations, all in user space."""

    def __init__(self, media=A4, crop=None, rotate=0):
        self.media = (0, 0, media[0], media[1])
        self.crop = crop or self.media
        self.rotate = rotate
        self.ops = []
        self.images = {}
        self.links = []
        self.words = []  # (text, user-space rect) for the manifest
        self.has_text = False

    # -- text

    def text(self, x, y, size, line, record_words=True):
        """Draws [line] with its baseline at (x, y). Records each word's box for the manifest."""
        self.has_text = True
        encoded = _escape(line.encode("cp1252"))
        self.ops.append(b"BT /F1 %s Tf %s %s Td (%s) Tj ET" % (_num(size), _num(x), _num(y), encoded))
        if record_words:
            column = 0
            for word in line.split(" "):
                if word:
                    self.words.append((word, self.word_rect(x, y, size, column, len(word))))
                column += len(word) + 1
        return self

    @staticmethod
    def word_rect(x, y, size, column, length):
        left = x + column * COURIER_ADVANCE * size
        return (left, y - DESCENT * size, left + length * COURIER_ADVANCE * size, y + ASCENT * size)

    def rect_of(self, x, y, size, line, word):
        column = line.index(word)
        return self.word_rect(x, y, size, column, len(word))

    # -- images

    def image(self, name, image_ref, x, y, width, height):
        self.images[name] = image_ref
        self.ops.append(b"q %s 0 0 %s %s %s cm /%s Do Q" % (
            _num(width), _num(height), _num(x), _num(y), name.encode()))
        return self

    # -- links

    def link(self, rects, action, record):
        """[rects] in user space; one rect is a plain link, several become QuadPoints."""
        self.links.append((rects, action, record))
        return self

    # -- geometry for the manifest

    def normalize(self, rect):
        """User-space rect -> displayed, normalized, top-left-origin rect (CropBox + /Rotate)."""
        cl, cb, cr, ct = self.crop
        width, height = cr - cl, ct - cb
        corners = []
        for x, y in ((rect[0], rect[1]), (rect[2], rect[3]), (rect[0], rect[3]), (rect[2], rect[1])):
            u, v = (x - cl) / width, (ct - y) / height
            for _ in range(self.rotate // 90 % 4):
                u, v = 1 - v, u  # rotate the displayed page 90° clockwise
            corners.append((u, v))
        us = [c[0] for c in corners]
        vs = [c[1] for c in corners]
        return [round(min(us), 4), round(min(vs), 4), round(max(us), 4), round(max(vs), 4)]


def build(pages, path, user_password=None, font=True):
    doc = Document()
    catalog = doc.reserve()
    tree = doc.reserve()
    font_ref = doc.add({
        "Type": Name("Font"),
        "Subtype": Name("Type1"),
        "BaseFont": Name("Courier"),
        "Encoding": Name("WinAnsiEncoding"),
    }) if font else None

    page_refs = [doc.reserve() for _ in pages]
    for page, ref in zip(pages, page_refs):
        annots = []
        for rects, action, _ in page.links:
            bounds = [min(r[0] for r in rects), min(r[1] for r in rects),
                      max(r[2] for r in rects), max(r[3] for r in rects)]
            annot = {
                "Type": Name("Annot"),
                "Subtype": Name("Link"),
                "Rect": bounds,
                "Border": [0, 0, 0],
            }
            if len(rects) > 1:
                quads = []
                for l, b, r, t in rects:
                    quads += [l, t, r, t, l, b, r, b]
                annot["QuadPoints"] = quads
            kind, target = action
            if kind == "uri":
                annot["A"] = {"S": Name("URI"), "URI": Text(target)}
            else:
                page_index, position = target
                dest = [page_refs[page_index], Name("XYZ"), position[0], position[1], None] \
                    if position else [page_refs[page_index], Name("Fit")]
                annot["Dest"] = dest
            annots.append(doc.add(annot))

        resources = {}
        if font_ref:
            resources["Font"] = {"F1": font_ref}
        if page.images:
            resources["XObject"] = dict(page.images)
        content = doc.add(Stream({}, b"\n".join(page.ops)))
        entries = {
            "Type": Name("Page"),
            "Parent": tree,
            "MediaBox": list(page.media),
            "Resources": resources,
            "Contents": content,
        }
        if page.crop != page.media:
            entries["CropBox"] = list(page.crop)
        if page.rotate:
            entries["Rotate"] = page.rotate
        if annots:
            entries["Annots"] = annots
        doc.put(ref, entries)

    doc.put(tree, {"Type": Name("Pages"), "Kids": page_refs, "Count": len(pages)})
    doc.put(catalog, {"Type": Name("Catalog"), "Pages": tree})
    doc.write(path, catalog, user_password)


def scanned_image(doc, seed, width=850, height=1100):
    """A grayscale raster that looks like a scanned page of text: dark bars, no text layer."""
    rng = random.Random(seed)
    rows = []
    for y in range(height):
        row = bytearray(b"\xf4" * width)  # off-white paper
        rows.append(row)
    line_y = 90
    while line_y < height - 90:
        x = 80
        while x < width - 80:
            word = rng.randint(20, 90)
            if x + word > width - 80:
                break
            for yy in range(line_y, line_y + 14):
                for xx in range(x, x + word):
                    rows[yy][xx] = 0x30
            x += word + rng.randint(10, 18)
        line_y += rng.choice((28, 28, 28, 60))
    raw = b"".join(bytes(r) for r in rows)
    return doc.add(Stream({
        "Type": Name("XObject"),
        "Subtype": Name("Image"),
        "Width": width,
        "Height": height,
        "ColorSpace": Name("DeviceGray"),
        "BitsPerComponent": 8,
    }, raw))


# ------------------------------------------------------------------------------------- fixtures

MANIFEST = {}


def record(name, pages, extra=None, page_text=None):
    entry = {
        "pageCount": len(pages),
        "pages": [],
    }
    for index, page in enumerate(pages):
        entry["pages"].append({
            "index": index,
            "hasText": page.has_text if page_text is None else page_text[index],
            "rotate": page.rotate,
            "words": [
                {"text": text, "bounds": page.normalize(rect)}
                for text, rect in page.words
                if extra is None or extra.get("allWords") or text in extra.get("keyWords", ())
            ],
            "links": [
                {**rec, "bounds": [page.normalize(r) for r in rects]}
                for rects, _, rec in page.links
            ],
        })
    if extra:
        entry.update({k: v for k, v in extra.items() if k not in ("allWords", "keyWords")})
    MANIFEST[name] = entry


def text_and_links(out):
    size = 14
    p1 = Page()
    lines = [
        "Docucraft: prueba de texto y enlaces.",
        "Acentos: canción, año, pingüino.",
        "Web segura: https-link",
        "Web insegura: http-link",
        "Correo: mail-link  Teléfono: tel-link",
        "Peligroso: js-link",
        "Interno con posición: goto-xyz",
        "Interno sin posición: goto-fit",
        "Interno por acción: goto-action",
        "Enlace en dos líneas: multi-line-link-start",
        "multi-line-link-end fin.",
    ]
    x, top = 60, 780
    for i, line in enumerate(lines):
        p1.text(x, top - i * 28, size, line)

    def at(row, word):
        return p1.rect_of(x, top - row * 28, size, lines[row], word)

    p1.link([at(2, "https-link")], ("uri", "https://example.com/docucraft?q=1"),
            {"kind": "external", "uri": "https://example.com/docucraft?q=1"})
    p1.link([at(3, "http-link")], ("uri", "http://example.com/insecure"),
            {"kind": "external", "uri": "http://example.com/insecure"})
    p1.link([at(4, "mail-link")], ("uri", "mailto:hola@example.com"),
            {"kind": "external", "uri": "mailto:hola@example.com"})
    p1.link([at(4, "tel-link")], ("uri", "tel:+34600000000"),
            {"kind": "external", "uri": "tel:+34600000000"})
    p1.link([at(5, "js-link")], ("uri", "javascript:alert(1)"),
            {"kind": "external", "uri": "javascript:alert(1)"})
    p1.link([at(6, "goto-xyz")], ("goto", (2, (72, 500))),
            {"kind": "internal", "pageIndex": 2, "position": "XYZ 72 500 (user space)"})
    p1.link([at(7, "goto-fit")], ("goto", (2, None)),
            {"kind": "internal", "pageIndex": 2, "position": None})
    p1.link([at(8, "goto-action")], ("goto-action", (2, (72, 500))),
            {"kind": "internal", "pageIndex": 2, "position": "XYZ 72 500 via /A /GoTo"})
    p1.link([at(9, "multi-line-link-start"), at(10, "multi-line-link-end")],
            ("uri", "https://example.com/two-lines"),
            {"kind": "external", "uri": "https://example.com/two-lines", "quadPoints": True})

    p2 = Page()
    paragraph = [
        "Segunda página: varias líneas para",
        "probar una selección que cruza de",
        "una línea a la siguiente y respeta",
        "el orden de lectura.",
    ]
    for i, line in enumerate(paragraph):
        p2.text(60, 780 - i * 24, 16, line)

    p3 = Page()
    p3.text(72, 500, 20, "Destino de los enlaces internos.")
    p3.text(72, 100, 12, "Pie de la tercera página.")

    pages = [p1, p2, p3]
    build(pages, out / "text-and-links.pdf")
    record("text-and-links.pdf", pages, {"allWords": True})


def scanned(out):
    doc_pages = []
    # Build manually: images need the document to exist first.
    doc = Document()
    catalog, tree = doc.reserve(), doc.reserve()
    page_refs = []
    for seed in (1, 2):
        image = scanned_image(doc, seed)
        content = doc.add(Stream({}, b"q 595 0 0 842 0 0 cm /Im1 Do Q"))
        page_refs.append(doc.add({
            "Type": Name("Page"),
            "Parent": tree,
            "MediaBox": [0, 0, 595, 842],
            "Resources": {"XObject": {"Im1": image}},
            "Contents": content,
        }))
        doc_pages.append(Page())
    doc.put(tree, {"Type": Name("Pages"), "Kids": page_refs, "Count": len(page_refs)})
    doc.put(catalog, {"Type": Name("Catalog"), "Pages": tree})
    doc.write(out / "scanned-image-only.pdf", catalog)
    record("scanned-image-only.pdf", doc_pages, {
        "note": "Simulates ML Kit's output: one raster per page, no text layer. Not a real scan.",
    })


def long_document(out, count=320):
    pages = []
    for i in range(count):
        page = Page()
        page.text(60, 760, 28, f"Página {i + 1} de {count}")
        for row in range(3):
            page.text(60, 700 - row * 22, 12, f"Línea {row + 1} del cuerpo de la página {i + 1}.",
                      record_words=False)
        pages.append(page)
    build(pages, out / "long-320-pages.pdf")
    record("long-320-pages.pdf", pages, {"keyWords": ()})


def password_protected(out):
    page = Page()
    page.text(72, 700, 18, "Documento protegido.")
    build([page], out / "password-protected.pdf", user_password="docucraft")
    record("password-protected.pdf", [page], {
        "userPassword": "docucraft",
        "expect": "PdfRenderer refuses to open it (SecurityException).",
    })


def rotated_mixed(out):
    specs = [
        ("A4 vertical", Page(media=A4)),
        ("A5", Page(media=A5)),
        ("A4 apaisado", Page(media=A4_LANDSCAPE)),
        ("A4 con /Rotate 90", Page(media=A4, rotate=90)),
        ("A4 con CropBox", Page(media=A4, crop=(50, 50, 545, 792))),
    ]
    for index, (label, page) in enumerate(specs):
        x = page.crop[0] + 60
        y = page.crop[3] - 120
        page.text(x, y + 40, 12, label, record_words=False)
        line = "Palabra TARGET aquí"
        page.text(x, y, 16, line, record_words=False)
        rect = page.rect_of(x, y, 16, line, "TARGET")
        page.words.append(("TARGET", rect))
        page.link([rect], ("uri", f"https://example.com/p{index + 1}"),
                  {"kind": "external", "uri": f"https://example.com/p{index + 1}"})
    pages = [p for _, p in specs]
    build(pages, out / "rotated-mixed-sizes.pdf")
    record("rotated-mixed-sizes.pdf", pages, {"keyWords": ("TARGET",)})


def mixed_text_and_scanned(out):
    doc = Document()
    catalog, tree = doc.reserve(), doc.reserve()
    font = doc.add({"Type": Name("Font"), "Subtype": Name("Type1"),
                    "BaseFont": Name("Courier"), "Encoding": Name("WinAnsiEncoding")})
    page_refs = []
    records = []
    for index in range(3):
        if index == 1:
            image = scanned_image(doc, 7)
            resources = {"XObject": {"Im1": image}}
            ops = b"q 595 0 0 842 0 0 cm /Im1 Do Q"
            page = Page()
        else:
            page = Page()
            page.text(72, 700, 16, f"Página {index + 1} con texto real.")
            resources = {"Font": {"F1": font}}
            ops = b"\n".join(page.ops)
        records.append(page)
        page_refs.append(doc.add({
            "Type": Name("Page"),
            "Parent": tree,
            "MediaBox": [0, 0, 595, 842],
            "Resources": resources,
            "Contents": doc.add(Stream({}, ops)),
        }))
    doc.put(tree, {"Type": Name("Pages"), "Kids": page_refs, "Count": len(page_refs)})
    doc.put(catalog, {"Type": Name("Catalog"), "Pages": tree})
    doc.write(out / "mixed-text-and-scanned.pdf", catalog)
    record("mixed-text-and-scanned.pdf", records, {"allWords": True})


def main():
    default = Path(__file__).resolve().parents[2] / "composepdf/src/androidTest/assets/fixtures"
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else default
    out.mkdir(parents=True, exist_ok=True)

    text_and_links(out)
    scanned(out)
    long_document(out)
    password_protected(out)
    rotated_mixed(out)
    mixed_text_and_scanned(out)

    (out / "manifest.json").write_text(
        json.dumps(MANIFEST, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    for path in sorted(out.iterdir()):
        print(f"{path.name:32} {path.stat().st_size:>9,} bytes")


if __name__ == "__main__":
    main()
