"""Разбор строк со списками MCC: "3000–3303, 3308, 4304,4415", "5712 - 5714"."""

import re

# Банки пишут диапазоны и через дефис, и через en/em dash, иногда с пробелами.
_DASHES = "‐‑‒–—−"
_RANGE_OR_CODE = re.compile(r"(?<!\d)(\d{4,5})(?:\s*-\s*(\d{4,5}))?(?!\d)")


def normalize_dashes(text: str) -> str:
    for d in _DASHES:
        text = text.replace(d, "-")
    return text


def parse_codes(text: str) -> list[str]:
    """Возвращает коды и диапазоны в виде ["5411", "3000-3303"] в порядке появления.

    5-значные коды оставляем: у Альфы бывают 39901/39912 (экосистемы на МИР).
    """
    out: list[str] = []
    for m in _RANGE_OR_CODE.finditer(normalize_dashes(text)):
        lo, hi = m.group(1), m.group(2)
        if hi:
            if int(hi) < int(lo):
                raise ValueError(f"перевёрнутый диапазон {lo}-{hi} в {text!r}")
            out.append(f"{lo}-{hi}")
        else:
            out.append(lo)
    return out


def expand(items: list[str]) -> set[int]:
    codes: set[int] = set()
    for it in items:
        if "-" in it:
            lo, hi = map(int, it.split("-"))
            codes.update(range(lo, hi + 1))
        else:
            codes.add(int(it))
    return codes


def sort_key(item: str) -> int:
    return int(item.split("-")[0])


def dedupe_sorted(items: list[str]) -> list[str]:
    return sorted(set(items), key=sort_key)
