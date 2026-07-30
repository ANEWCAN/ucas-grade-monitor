#!/usr/bin/env python3
"""Small pre-commit/CI scan for common accidentally committed secrets."""

from __future__ import annotations

import re
from pathlib import Path

PATTERNS = {
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "CSTCloud token": re.compile(r"(?i)(?:cstcloud|api)[_-]?(?:token|key)\s*[:=]\s*[\"']?[A-Za-z0-9_-]{20,}"),
    "hard-coded password": re.compile(r"(?i)(?:ucas_)?password\s*[:=]\s*[\"'][^\"']{6,}[\"']"),
}

SKIP_PARTS = {".git", ".gradle", "build"}
TEXT_SUFFIXES = {".java", ".kt", ".kts", ".xml", ".md", ".txt", ".yml", ".yaml", ".properties"}


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    findings: list[str] = []
    for path in root.rglob("*"):
        if not path.is_file() or any(part in SKIP_PARTS for part in path.parts):
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for label, pattern in PATTERNS.items():
            if pattern.search(text):
                findings.append(f"{path.relative_to(root)}: possible {label}")

    if findings:
        print("Potential secrets found:")
        for finding in findings:
            print(f"- {finding}")
        return 1
    print("No common hard-coded secrets found.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
