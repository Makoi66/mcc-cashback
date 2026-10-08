package dev.mcc.cashback.data

/** "5411" -> 5411..5411, "3000-3303" -> 3000..3303. Кривые строки пропускаем, а не роняем приложение. */
fun parseMccItem(item: String): IntRange? {
    val parts = item.split('-', '–', '—').map { it.trim() }
    return when (parts.size) {
        1 -> parts[0].toIntOrNull()?.let { it..it }
        2 -> {
            val lo = parts[0].toIntOrNull() ?: return null
            val hi = parts[1].toIntOrNull() ?: return null
            if (lo <= hi) lo..hi else null
        }
        else -> null
    }
}

fun expandMcc(items: List<String>): Set<Int> =
    items.mapNotNull(::parseMccItem).flatMapTo(HashSet()) { it.asIterable() }

/** {5411, 5412, 5413, 5422} -> "5411–5413, 5422". */
fun formatCodes(codes: Collection<Int>): String {
    val sorted = codes.toSortedSet().toList()
    if (sorted.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var start = sorted[0]
    var prev = start
    fun flush() {
        parts += when {
            start == prev -> mcc(start)
            prev == start + 1 -> "${mcc(start)}, ${mcc(prev)}"
            else -> "${mcc(start)}–${mcc(prev)}"
        }
    }
    for (c in sorted.drop(1)) {
        if (c == prev + 1) {
            prev = c
        } else {
            flush()
            start = c
            prev = c
        }
    }
    flush()
    return parts.joinToString(", ")
}

fun mcc(code: Int): String = code.toString().padStart(4, '0')

sealed interface Verdict {
    /** Код входит в одну или несколько категорий (у Т-Банка 5977 — и «Косметика», и «Красота»). */
    data class InCategories(val matches: List<Match>) : Verdict
    data class Excluded(val note: String?) : Verdict
    /** Ни в категориях, ни в исключениях; [fallback] — что банк обещает «на всё остальное». */
    data class Outside(val fallback: String?) : Verdict
}

data class Match(val category: CategoryDto, val note: String?)

data class BankResult(val bank: BankFile, val verdict: Verdict)

class BankIndex(val bank: BankFile) {
    private val ranges: List<Triple<IntRange, CategoryDto, String>> =
        bank.categories.flatMap { cat -> cat.mcc.mapNotNull { item -> parseMccItem(item)?.let { Triple(it, cat, item) } } }
    private val excluded: List<Pair<IntRange, String>> =
        bank.excluded.mapNotNull { item -> parseMccItem(item)?.let { it to item } }

    fun lookup(code: Int): BankResult {
        val matches = ranges.filter { code in it.first }
            .groupBy({ it.second }, { it.third })
            .map { (cat, items) ->
                // Оговорка может быть привязана к самому коду или к диапазону, в который он попал.
                val note = cat.mccNotes[mcc(code)] ?: items.firstNotNullOfOrNull { cat.mccNotes[it] }
                Match(cat, note)
            }
        val excludedBy = excluded.filter { code in it.first }.map { it.second }
        val verdict = when {
            matches.isNotEmpty() -> Verdict.InCategories(matches)
            excludedBy.isNotEmpty() ->
                Verdict.Excluded(bank.excludedNotes[mcc(code)] ?: excludedBy.firstNotNullOfOrNull { bank.excludedNotes[it] })
            else -> Verdict.Outside(bank.fallback)
        }
        return BankResult(bank, verdict)
    }
}

class Lookup(dataset: Dataset) {
    private val indexes = dataset.banks.map(::BankIndex)
    /** Сначала банки, где код попал в категорию, потом «вне категорий», в конце исключения. */
    fun lookup(code: Int, disabled: Set<String>): List<BankResult> =
        indexes.filter { it.bank.id !in disabled }
            .map { it.lookup(code) }
            .sortedBy {
                when (it.verdict) {
                    is Verdict.InCategories -> 0
                    is Verdict.Outside -> 1
                    is Verdict.Excluded -> 2
                }
            }
}
