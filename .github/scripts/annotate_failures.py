#!/usr/bin/env python3
"""Publica como anotaciones de GitHub Actions los fallos de las pruebas JUnit."""

import glob
import xml.etree.ElementTree as ET


def clean(text: str) -> str:
    text = (text or "").replace("%", "%25").replace("::", ": ")
    return " ".join(text.split())[:400]


for path in sorted(glob.glob("build/test-results/test/TEST-*.xml")):
    tree = ET.parse(path)
    for case in tree.iter("testcase"):
        failures = list(case.findall("failure")) + list(case.findall("error"))
        if not failures:
            continue
        message = clean(failures[0].get("message") or failures[0].text)
        stack = failures[0].text or ""
        cause = next(
            (line.strip() for line in stack.splitlines() if line.strip().startswith("Caused by")),
            "",
        )
        if cause:
            message = clean(f"{message} | {cause}")
        title = clean(f"{case.get('classname')}.{case.get('name')}")
        print(f"::error title={title}::{message}")
