"""Проверяет data/banks/*.json, пишет словарь MCC и data/index.json.

    python -m mcc_tools.publish            # из каталога pipeline/

index.json — просто список файлов, который приложение скачивает при Sync.
Его можно править и руками: новый банк = новый файл banks/<id>.json + строка в index.json.
"""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from .codes import parse_codes

SCHEMA = 1
ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data"


def write_json(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def validate_bank(path: Path) -> dict:
    bank = json.loads(path.read_text(encoding="utf-8"))
    errors = []
    if bank.get("schema") != SCHEMA:
        errors.append(f"schema != {SCHEMA}")
    if not bank.get("id") or not bank.get("name"):
        errors.append("нет id или name")
    names = [c["name"] for c in bank.get("categories", [])]
    if len(names) != len(set(names)):
        errors.append("повторяются названия категорий")
    for c in bank.get("categories", []):
        for item in c.get("mcc", []) + list(c.get("mcc_notes", {})):
            if parse_codes(item) != [item]:
                errors.append(f"«{c['name']}»: кривой код {item!r}")
    for item in bank.get("excluded", []) + list(bank.get("excluded_notes", {})):
        if parse_codes(item) != [item]:
            errors.append(f"исключения: кривой код {item!r}")
    logo = bank.get("logo")
    if logo and not (DATA / logo).is_file():
        errors.append(f"нет файла логотипа data/{logo}")
    if errors:
        raise SystemExit(f"{path.name}:\n  " + "\n  ".join(errors))
    return bank


def _ordered_bank_files() -> list[Path]:
    """Порядок из текущего index.json (его можно править руками), новые файлы — в конец."""
    existing = sorted((DATA / "banks").glob("*.json"))
    index = DATA / "index.json"
    order = json.loads(index.read_text(encoding="utf-8")).get("banks", []) if index.exists() else []
    known = [DATA / f for f in order if (DATA / f) in existing]
    return known + [p for p in existing if p not in known]


def main() -> None:
    files, ids = [], set()
    for path in _ordered_bank_files():
        bank = validate_bank(path)
        if bank["id"] in ids:
            raise SystemExit(f"{path.name}: id {bank['id']!r} уже занят другим файлом")
        ids.add(bank["id"])
        files.append(f"banks/{path.name}")
    write_json(DATA / "index.json", {
        "schema": SCHEMA,
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "banks": files,
    })
    print(f"index.json: {', '.join(files)}", file=sys.stderr)


if __name__ == "__main__":
    main()
