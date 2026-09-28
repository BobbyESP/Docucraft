#!/usr/bin/env python3
# Copyright (C) 2026  Gabriel Fontán (BobbyESP)
"""
Downloads the app's default fonts from Google Fonts into app/src/main/res/font, one static TTF per
weight, plus each family's license into app/src/main/assets/licenses/fonts.

The fonts are under the SIL Open Font License, which asks for the license to travel with the font,
so it is shipped in the APK next to them.

Standard library only. Google Fonts serves TrueType to clients that do not announce support for
WOFF2, so the request uses a plain user agent.

Usage:
    python scripts/fonts/download_fonts.py
"""

import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FONT_DIR = ROOT / "app/src/main/res/font"
LICENSE_DIR = ROOT / "app/src/main/assets/licenses/fonts"

USER_AGENT = "Mozilla/4.0"
CSS_API = "https://fonts.googleapis.com/css2"
LICENSE_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/{slug}/OFL.txt"

WEIGHT_NAMES = {400: "regular", 500: "medium", 600: "semibold", 700: "bold"}

# The defaults in UserPreferences, with the weights createGoogleFontFamily asks for when the family
# has them. The DM Serif families only come in 400.
FAMILIES = {
    "DM Serif Display": [400],
    "DM Serif Text": [400],
    "Inter": [400, 500, 600, 700],
    "DM Sans": [400, 500, 600, 700],
    "JetBrains Mono": [400, 500, 600, 700],
}

FONT_FACE = re.compile(
    r"@font-face\s*{[^}]*?font-style:\s*normal;[^}]*?font-weight:\s*(\d+);[^}]*?"
    r"src:\s*url\(([^)]+\.ttf)\)",
    re.S,
)


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def resource_name(family: str) -> str:
    """Android resource names allow only lowercase letters, digits and underscores."""
    return re.sub(r"[^a-z0-9]+", "_", family.lower()).strip("_")


def download_family(family: str, weights: list[int]) -> None:
    query = f"{family}:wght@{';'.join(map(str, weights))}" if weights != [400] else family
    css = fetch(f"{CSS_API}?{urllib.parse.urlencode({'family': query})}").decode()
    served = {int(weight): url for weight, url in FONT_FACE.findall(css)}

    missing = [weight for weight in weights if weight not in served]
    if missing:
        sys.exit(f"{family}: Google Fonts did not serve weights {missing}")

    for weight in weights:
        target = FONT_DIR / f"{resource_name(family)}_{WEIGHT_NAMES[weight]}.ttf"
        target.write_bytes(fetch(served[weight]))
        print(f"  {target.relative_to(ROOT)}")

    slug = re.sub(r"[^a-z0-9]", "", family.lower())
    license_file = LICENSE_DIR / f"{resource_name(family)}_OFL.txt"
    license_file.write_bytes(fetch(LICENSE_URL.format(slug=slug)))
    print(f"  {license_file.relative_to(ROOT)}")


def main() -> None:
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)
    for family, weights in FAMILIES.items():
        print(family)
        download_family(family, weights)


if __name__ == "__main__":
    main()
