#!/usr/bin/env python3
"""Falla la integración continua si JUnit omitió alguna prueba.

Las pruebas que necesitan Docker (Testcontainers) se declaran con
``@Testcontainers(disabledWithoutDocker = true)`` o ``@EnabledIfDockerAvailable``: si el
runner no tiene Docker, JUnit las marca como *skipped* y el job pasaría en verde sin
haberlas ejecutado. Este script lee los reportes XML de JUnit
(``**/build/test-results/test/*.xml``), suma las pruebas omitidas y sale con código 1
cuando encuentra alguna, indicando las clases afectadas.

Uso:
    python3 .github/scripts/assert-no-skipped-tests.py
    python3 .github/scripts/assert-no-skipped-tests.py --root microservices/ms-03-activities-and-events
"""

from __future__ import annotations

import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ElementTree

# Mismo patrón que publica Gradle para la tarea `test`: un XML por clase de prueba.
REPORT_PATTERN = os.path.join("**", "build", "test-results", "test", "*.xml")

# Los mensajes de GitHub Actions se cortan a 4 KB y sobran con este límite.
MAX_NAMES_PER_CLASS = 5
MAX_CLASSES_REPORTED = 30


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Comprueba que ninguna prueba JUnit se haya omitido: lee los reportes "
            "build/test-results/test/*.xml y falla si la suma de <skipped> es mayor que cero."
        ),
        epilog=(
            "Salida: 0 si no hay pruebas omitidas, 1 si hay alguna, si un reporte está "
            "corrupto o si no se encontró ningún reporte (nunca se asume que todo está bien)."
        ),
    )
    parser.add_argument(
        "--root",
        default=".",
        help="Directorio raíz donde buscar los reportes JUnit (por defecto: el actual).",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="No imprime el resumen cuando no hay pruebas omitidas.",
    )
    return parser.parse_args()


def collect_skipped(
    root: str,
) -> tuple[int, int, dict[str, list[str]], list[str]]:
    """Recorre los reportes y agrupa las pruebas omitidas por clase.

    Devuelve (total_omitidas, total_reportes, {clase: [nombres]}, [reportes ilegibles]).
    """
    total_skipped = 0
    reports = sorted(glob.glob(os.path.join(root, REPORT_PATTERN), recursive=True))
    by_class: dict[str, list[str]] = {}
    broken: list[str] = []

    for path in reports:
        try:
            suite = ElementTree.parse(path).getroot()
        except ElementTree.ParseError as error:
            broken.append(f"{path} ({error})")
            continue

        for case in suite.iter("testcase"):
            if case.find("skipped") is None:
                continue
            total_skipped += 1
            # `classname` es el nombre completo de la clase; si falta, se usa el archivo.
            class_name = case.get("classname") or os.path.basename(path)
            by_class.setdefault(class_name, []).append(case.get("name") or "?")

    return total_skipped, len(reports), by_class, broken


def escape_annotation(message: str) -> str:
    """Escapa un mensaje para que GitHub Actions no lo interprete como comando."""
    return (
        message.replace("%", "%25")
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("::", ": ")
    )


def main() -> int:
    args = parse_args()
    root = os.path.abspath(args.root)

    if not os.path.isdir(root):
        print(f"ERROR: el directorio raíz no existe: {root}", file=sys.stderr)
        return 1

    total_skipped, report_count, by_class, broken = collect_skipped(root)

    if broken:
        print(
            f"ERROR: {len(broken)} reporte(s) JUnit ilegible(s); no se puede afirmar que no "
            "haya pruebas omitidas.",
            file=sys.stderr,
        )
        for item in broken[:MAX_CLASSES_REPORTED]:
            print(f"  - {item}", file=sys.stderr)
        return 1

    if report_count == 0:
        print(
            f"ERROR: no se encontró ningún reporte JUnit en {root} "
            f"(patrón {os.path.join(root, REPORT_PATTERN)}). "
            "Las pruebas no se ejecutaron, así que el resultado no es válido.",
            file=sys.stderr,
        )
        return 1

    if total_skipped == 0:
        if not args.quiet:
            print(
                f"OK: {report_count} reporte(s) JUnit, 0 pruebas omitidas."
            )
        return 0

    classes = sorted(by_class.items(), key=lambda item: (-len(item[1]), item[0]))
    names_summary = "; ".join(
        f"{class_name} ({len(names)}): {', '.join(names[:MAX_NAMES_PER_CLASS])}"
        for class_name, names in classes[:MAX_CLASSES_REPORTED]
    )

    print(
        f"ERROR: se omitieron {total_skipped} prueba(s) en {len(classes)} clase(s). "
        "En CI ninguna prueba puede omitirse (Testcontainers necesita Docker).",
        file=sys.stderr,
    )
    for class_name, names in classes[:MAX_CLASSES_REPORTED]:
        shown = ", ".join(names[:MAX_NAMES_PER_CLASS])
        extra = "" if len(names) <= MAX_NAMES_PER_CLASS else f", +{len(names) - MAX_NAMES_PER_CLASS} más"
        print(f"  - {class_name}: {len(names)} omitida(s) [{shown}{extra}]", file=sys.stderr)
    if len(classes) > MAX_CLASSES_REPORTED:
        print(f"  ... y {len(classes) - MAX_CLASSES_REPORTED} clase(s) más", file=sys.stderr)

    # Anotación en la pestaña "Checks" del pull request.
    print(
        "::error title=Pruebas omitidas::"
        + escape_annotation(
            f"{total_skipped} prueba(s) omitidas en {len(classes)} clase(s): {names_summary}"
        )
    )

    # Resumen del job, visible sin abrir el log.
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        try:
            with open(summary_path, "a", encoding="utf-8") as summary:
                summary.write("## Pruebas omitidas (no permitido en CI)\n\n")
                summary.write(f"Se omitieron **{total_skipped}** prueba(s):\n\n")
                for class_name, names in classes[:MAX_CLASSES_REPORTED]:
                    summary.write(f"- `{class_name}`: {len(names)} omitida(s)\n")
        except OSError as error:
            print(f"AVISO: no se pudo escribir el resumen: {error}", file=sys.stderr)

    return 1


if __name__ == "__main__":
    sys.exit(main())
