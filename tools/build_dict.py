#!/usr/bin/env python3
"""
Builds the keyboard's English dictionary asset from AOSP LatinIME's wordlist.

Source: platform/packages/inputmethods/LatinIME, dictionaries/en_US_wordlist.combined.gz
Licence: Apache-2.0 (see core/src/main/assets/dict/NOTICE).

    python tools/build_dict.py [path/to/en_US_wordlist.combined(.gz)]

With no argument the wordlist is fetched from android.googlesource.com.

Output, core/src/main/assets/dict/en_US.tsv, one entry per line, best first:

    word<TAB>frequency[<TAB>o]      frequency is AOSP's 0..255 log-scale value;
                                    `o` marks a word that is valid but must never
                                    be *offered* (offensive), only accepted
    @typed<TAB>replacement          a shortcut: "dont" -> "don't", "im" -> "I'm"

Plain text on purpose: it compresses well inside the APK, diffs readably, and
loads in one pass with no parser beyond split().
"""
from __future__ import annotations

import base64
import gzip
import sys
import urllib.request
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
OUT = PROJECT / "core" / "src" / "main" / "assets" / "dict" / "en_US.tsv"
URL = ("https://android.googlesource.com/platform/packages/inputmethods/LatinIME/"
       "+/refs/heads/main/dictionaries/en_US_wordlist.combined.gz?format=TEXT")


def load(argv: list[str]) -> str:
    if argv:
        raw = Path(argv[0]).read_bytes()
    else:
        req = urllib.request.Request(URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=120) as r:
            raw = base64.b64decode(r.read())
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return raw.decode("utf-8")


def fields(line: str) -> dict[str, str]:
    out = {}
    for part in line.strip().split(","):
        if "=" in part:
            k, v = part.split("=", 1)
            out[k] = v
    return out


def main(argv: list[str]) -> None:
    text = load(argv)
    words: list[tuple[str, int, bool]] = []
    shortcuts: list[tuple[str, str]] = []
    current: dict[str, str] | None = None

    for line in text.splitlines():
        if line.startswith(" word="):
            current = fields(line)
            word = current["word"]
            if current.get("not_a_word") == "true":
                continue
            # Letters and apostrophes only: the keyboard composes words, not tokens.
            if not all(c.isalpha() or c == "'" for c in word):
                continue
            freq = int(current.get("f", "0"))
            offensive = (current.get("possibly_offensive") == "true"
                         or current.get("flags") == "offensive")
            words.append((word, freq, offensive))
        elif line.startswith("  shortcut=") and current is not None:
            sc = fields(line)
            if sc.get("f") == "whitelist":
                shortcuts.append((current["word"], sc["shortcut"]))

    words.sort(key=lambda w: (-w[1], w[0]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="\n") as f:
        for word, freq, offensive in words:
            f.write(f"{word}\t{freq}\to\n" if offensive else f"{word}\t{freq}\n")
        for typed, replacement in shortcuts:
            f.write(f"@{typed}\t{replacement}\n")
    print(f"{OUT}: {len(words)} words, {len(shortcuts)} shortcuts, "
          f"{OUT.stat().st_size // 1024} KiB")


if __name__ == "__main__":
    main(sys.argv[1:])
