#!/usr/bin/env python3
"""
Read JaCoCo coverage XML and print an LLM-friendly summary.

Usage:
    ./scripts/show-java-coverage.py [path/to/jacoco.xml]

Defaults to: ./target/jacoco-report/jacoco.xml
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEFAULT_JACOCO_XML = Path("./target/jacoco-report/jacoco.xml")

# Coverage thresholds from project rules
STMT_THRESHOLD = 80  # INSTRUCTION coverage (statement)
BRANCH_THRESHOLD = 70


def pct(covered, missed):
    total = covered + missed
    return (covered / total * 100) if total > 0 else 0.0


def parse_counter(element, counter_type):
    for c in element.findall("counter"):
        if c.get("type") == counter_type:
            return int(c.get("missed", 0)), int(c.get("covered", 0))
    return 0, 0


def main():
    jacoco_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_JACOCO_XML

    if not jacoco_path.exists():
        print(f"[ERROR] JaCoCo report not found: {jacoco_path}")
        print("  Run './scripts/run-java-tests.py' first to generate coverage data.")
        sys.exit(1)

    try:
        tree = ET.parse(jacoco_path)
    except ET.ParseError as e:
        print(f"[ERROR] Failed to parse {jacoco_path}: {e}")
        sys.exit(1)

    root = tree.getroot()

    # ------------------------------------------------------------------
    # Per-package coverage
    # ------------------------------------------------------------------
    print("# PER-PACKAGE COVERAGE")
    print(f"  {'Package':<45} {'INSTRUCTION':>12} {'BRANCH':>10}")
    print(f"  {'-' * 45} {'-' * 12} {'-' * 10}")

    packages = []
    for pkg in root.findall("package"):
        pkg_name = pkg.get("name", "")
        i_missed, i_covered = parse_counter(pkg, "INSTRUCTION")
        b_missed, b_covered = parse_counter(pkg, "BRANCH")
        packages.append({
            "name": pkg_name,
            "stmt_pct": pct(i_covered, i_missed),
            "branch_pct": pct(b_covered, b_missed),
            "stmt_covered": i_covered,
            "stmt_total": i_missed + i_covered,
            "branch_covered": b_covered,
            "branch_total": b_missed + b_covered,
        })

    # Sort by lowest statement coverage first
    packages.sort(key=lambda p: p["stmt_pct"])

    below_pkg = []
    for p in packages:
        if p["stmt_total"] == 0:
            continue
        stmt_flag = "*" if p["stmt_pct"] < STMT_THRESHOLD else " "
        if p["branch_total"] == 0:
            branch_str = "   n/a"
        else:
            branch_flag = "*" if p["branch_pct"] < BRANCH_THRESHOLD else " "
            branch_str = f"{branch_flag}{p['branch_pct']:5.1f}% ({p['branch_covered']}/{p['branch_total']})"
        if stmt_flag == "*" or (p["branch_total"] > 0 and p["branch_pct"] < BRANCH_THRESHOLD):
            below_pkg.append(p)
        stmt_str = f"{stmt_flag}{p['stmt_pct']:5.1f}% ({p['stmt_covered']}/{p['stmt_total']})"
        print(f"  {p['name']:<45} {stmt_str:>12} {branch_str:>10}")

    # ------------------------------------------------------------------
    # Classes below threshold
    # ------------------------------------------------------------------
    print()
    print("-" * 4)
    print("# CLASSES BELOW THRESHOLD")

    below_classes = []
    for pkg in root.findall("package"):
        pkg_name = pkg.get("name", "")
        for cls in pkg.findall("class"):
            cls_name = cls.get("name", "")
            i_missed, i_covered = parse_counter(cls, "INSTRUCTION")
            b_missed, b_covered = parse_counter(cls, "BRANCH")
            stmt_pct = pct(i_covered, i_missed)
            branch_pct = pct(b_covered, b_missed)

            stmt_low = i_missed + i_covered > 0 and stmt_pct < STMT_THRESHOLD
            branch_low = b_missed + b_covered > 0 and branch_pct < BRANCH_THRESHOLD

            if stmt_low or branch_low:
                below_classes.append({
                    "name": cls_name,
                    "stmt_pct": stmt_pct,
                    "stmt_covered": i_covered,
                    "stmt_total": i_missed + i_covered,
                    "branch_pct": branch_pct,
                    "branch_covered": b_covered,
                    "branch_total": b_missed + b_covered,
                    "stmt_low": stmt_low,
                    "branch_low": branch_low,
                })

    if below_classes:
        # Sort by lowest statement coverage
        below_classes.sort(key=lambda c: c["stmt_pct"])
        print(f"  {'Class':<55} {'INSTRUCTION':>12} {'BRANCH':>10}")
        print(f"  {'-' * 55} {'-' * 12} {'-' * 10}")
        for c in below_classes:
            stmt_str = f"{c['stmt_pct']:5.1f}% ({c['stmt_covered']}/{c['stmt_total']})" if c["stmt_total"] > 0 else "n/a"
            branch_str = f"{c['branch_pct']:5.1f}% ({c['branch_covered']}/{c['branch_total']})" if c["branch_total"] > 0 else "n/a"
            flags = []
            if c["stmt_low"]:
                flags.append("stmt")
            if c["branch_low"]:
                flags.append("branch")
            flag = f"  <-- {','.join(flags)}" if flags else ""
            print(f"  {c['name']:<55} {stmt_str:>12} {branch_str:>10}{flag}")
    else:
        print("  All classes meet the coverage thresholds.")

    # ------------------------------------------------------------------
    # Overall project coverage
    # ------------------------------------------------------------------
    overall = {}
    for c in root.findall("counter"):
        overall[c.get("type")] = (int(c.get("missed", 0)), int(c.get("covered", 0)))

    print()
    print("-" * 4)
    print("# JAVA BACKEND COVERAGE SUMMARY")
    print(f"  Source: {jacoco_path}")
    print()
    print("Thresholds: {}% statement (INSTRUCTION), {}% branch".format(STMT_THRESHOLD, BRANCH_THRESHOLD))
    print()

    for ctype in ("INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS", "COMPLEXITY"):
        missed, covered = overall.get(ctype, (0, 0))
        total = missed + covered
        p = pct(covered, missed)
        status = ""
        if ctype == "INSTRUCTION" and p < STMT_THRESHOLD:
            status = "  <-- BELOW THRESHOLD"
        elif ctype == "BRANCH" and p < BRANCH_THRESHOLD:
            status = "  <-- BELOW THRESHOLD"
        print(f"  {ctype:14s}  {p:6.1f}%  ({covered:>4}/{total:>4} covered){status}")

    # ------------------------------------------------------------------
    # Drill-down instructions
    # ------------------------------------------------------------------
    print()
    print("-" *4)
    print("# DRILL-DOWN INSTRUCTIONS")
    print("""
Extract specific package or class information by using grep on ./target/jacoco-report/jacoco.xml, 
searching for `<class name=\"...` or `<package name=\"...` tags.
""")


if __name__ == "__main__":
    main()
