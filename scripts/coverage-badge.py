#!/usr/bin/env python3
"""Turn a Kover XML report into a shields.io endpoint badge.

Reads the report-level LINE and BRANCH counters - the same numbers
`koverXmlReport` computes after the exclusions in build.gradle.kts - and
writes the JSON that https://img.shields.io/endpoint renders. CI publishes the
result to the `badges` branch on every push to main, which is what the
Coverage badge in README.md reads.

Usage:
    scripts/coverage-badge.py [report.xml] [out.json]

Defaults: build/reports/kover/report.xml and build/reports/kover/coverage-badge.json.
"""

import json
import sys
import xml.etree.ElementTree as ET

# Colour steps keyed on line coverage, highest first.
COLOURS = [(90, "brightgreen"), (80, "green"), (70, "yellowgreen"), (60, "yellow"), (0, "orange")]


def percent(root, kind):
    counter = next((c for c in root.findall("counter") if c.get("type") == kind), None)
    if counter is None:
        sys.exit(f"error: the report has no top-level {kind} counter")
    covered, missed = int(counter.get("covered")), int(counter.get("missed"))
    total = covered + missed
    return 100.0 * covered / total if total else 0.0


def main():
    report = sys.argv[1] if len(sys.argv) > 1 else "build/reports/kover/report.xml"
    out = sys.argv[2] if len(sys.argv) > 2 else "build/reports/kover/coverage-badge.json"
    root = ET.parse(report).getroot()
    lines, branches = percent(root, "LINE"), percent(root, "BRANCH")
    colour = next(name for floor, name in COLOURS if lines >= floor)
    badge = {
        "schemaVersion": 1,
        "label": "coverage",
        "message": f"{lines:.1f}% lines | {branches:.1f}% branches",
        "color": colour,
        "namedLogo": "kotlin",
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(badge, f)
        f.write("\n")
    print(badge["message"])


if __name__ == "__main__":
    main()
