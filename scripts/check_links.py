#!/usr/bin/env python3
"""Fail when a page in the given directory links to a missing local file or anchor."""

import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlparse


class Collector(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = set()
        self.refs = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if a.get("id"):
            self.ids.add(a["id"])
        for key in ("href", "src"):
            if a.get(key):
                self.refs.append(a[key])


def main(directory):
    root = Path(directory)
    pages = {p.name: p for p in root.glob("*.html")}
    parsed = {}
    for name, path in pages.items():
        c = Collector()
        c.feed(path.read_text(encoding="utf-8"))
        parsed[name] = c

    errors = []
    for name, c in parsed.items():
        for ref in c.refs:
            u = urlparse(ref)
            if u.scheme in ("http", "https", "mailto"):
                if u.scheme == "http":
                    errors.append(f"{name}: insecure link {ref}")
                continue
            target = unquote(u.path) or name
            file = root / target
            if not file.exists():
                errors.append(f"{name}: missing file {ref}")
                continue
            if u.fragment and target.endswith(".html"):
                ids = parsed.get(target).ids if target in parsed else set()
                if u.fragment not in ids:
                    errors.append(f"{name}: missing anchor {ref}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"links ok ({len(pages)} pages)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "docs"))
