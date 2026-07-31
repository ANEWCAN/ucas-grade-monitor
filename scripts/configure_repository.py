#!/usr/bin/env python3
"""Replace repository placeholders before the first GitHub push."""

from __future__ import annotations

import argparse
from pathlib import Path

PLACEHOLDER = "ANEWCAN"
TEXT_SUFFIXES = {".md", ".yml", ".yaml", ".txt", ".kts", ".properties", ".py"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--github-user", required=True, help="GitHub user or organization")
    args = parser.parse_args()

    github_user = args.github_user.strip()
    if not github_user or "/" in github_user or "\\" in github_user:
        parser.error("--github-user must be a GitHub user or organization name")

    root = Path(__file__).resolve().parents[1]
    changed = 0
    for path in root.rglob("*"):
        if not path.is_file() or ".git" in path.parts or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        text = path.read_text(encoding="utf-8")
        updated = text.replace(PLACEHOLDER, github_user)
        if updated != text:
            path.write_text(updated, encoding="utf-8", newline="\n")
            changed += 1

    print(f"Updated {changed} file(s) for GitHub owner: {github_user}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
