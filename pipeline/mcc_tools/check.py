"""Сверка JSON банка с исходными PDF — ловит опечатки при ручном переносе кодов.

    python -m mcc_tools.check ../data/banks/alfa.json ../sources/alfa/*.pdf

Печатает коды из JSON, которых нет в тексте PDF (почти наверняка опечатка),
и 4-значные числа из PDF, которых нет в JSON (там будут и годы, и ОГРН —
смотреть глазами, не потерялся ли код).
"""

import json
import re
import subprocess
import sys

from .codes import expand, parse_codes

INFERRED = "По описанию исключений"


def pdf_text(path: str) -> str:
    return subprocess.run(["pdftotext", "-layout", path, "-"], capture_output=True, text=True, check=True).stdout


def json_items(bank: dict) -> list[str]:
    items = list(bank.get("excluded", [])) + list(bank.get("excluded_notes", {}))
    for c in bank.get("categories", []):
        items += c.get("mcc", []) + list(c.get("mcc_notes", {}))
    return items


def main() -> None:
    bank_path, *pdfs = sys.argv[1:]
    bank = json.loads(open(bank_path, encoding="utf-8").read())
    text = "\n".join(pdf_text(p) for p in pdfs)
    in_pdf = set(re.findall(r"(?<!\d)\d{4,5}(?!\d)", text))

    # Коды, выведенные из словесного описания исключений, в PDF быть и не должно.
    inferred = {k for k, v in bank.get("excluded_notes", {}).items() if v.startswith(INFERRED)}
    items = [i for i in json_items(bank) if i not in inferred]
    missing = sorted({b for item in items for b in item.split("-") if b not in in_pdf})
    print("Нет в PDF (опечатка?):", ", ".join(missing) or "—")

    covered = expand([i for item in json_items(bank) for i in parse_codes(item)])
    extra = sorted(n for n in in_pdf if int(n) not in covered)
    print("Есть в PDF, нет в JSON:", ", ".join(extra) or "—")
    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    main()
