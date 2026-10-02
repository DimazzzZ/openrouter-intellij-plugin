#!/usr/bin/env python3
"""Fail when the Kover report has missed code that nobody has explained.

Kover cannot leave a single branch, or a single line, out of the report, so a branch no input can
take - or a line the compiler emits that never runs - stays "missed" there for good. Each such
line carries an `// Unreachable branch: <why>` (or, for a line with no branch, `// Unreachable
code: <why>`) comment, on the line itself or in the comment block right above it, so the reason
sits next to the code and the gap cannot grow unnoticed: any other line with a missed branch or instruction is either a missing
test or an unexplained ghost, and this script lists it and exits non-zero. TESTING.md has the rules.

Usage:
    scripts/coverage-unexplained.py [report.xml] [source-root]

Defaults: build/reports/kover/report.xml and src/main/kotlin.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MARKERS = ("Unreachable branch:", "Unreachable code:")


def is_marked(lines, number):
    """Whether line [number] (1-based) or the comment block right above it carries the marker."""
    if any(marker in lines[number - 1] for marker in MARKERS):
        return True
    index = number - 2
    while index >= 0 and lines[index].strip().startswith("//"):
        if any(marker in lines[index] for marker in MARKERS):
            return True
        index -= 1
    return False


def main():
    report = sys.argv[1] if len(sys.argv) > 1 else "build/reports/kover/report.xml"
    sources = Path(sys.argv[2] if len(sys.argv) > 2 else "src/main/kotlin")
    root = ET.parse(report).getroot()
    unmarked = []
    marked = 0
    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            path = sources / package.get("name") / source.get("name")
            lines = None
            for line in source.findall("line"):
                if int(line.get("mb")) == 0 and int(line.get("mi")) == 0:
                    continue
                lines = lines or path.read_text(encoding="utf-8").split("\n")
                number = int(line.get("nr"))
                if is_marked(lines, number):
                    marked += 1
                else:
                    unmarked.append(f"{path}:{number}: {lines[number - 1].strip()}")
    print(f"{marked} line(s) with missed code are marked unreachable")
    if unmarked:
        print(f"{len(unmarked)} line(s) with missed code have no test and no 'Unreachable branch:' or 'Unreachable code:' comment:")
        print("\n".join(unmarked))
        sys.exit(1)


if __name__ == "__main__":
    main()
