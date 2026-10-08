package dev.mcc.cashback.data

/** Что поменялось в одном банке после Sync; [lines] — готовые к показу строки. */
data class BankChange(val bankName: String, val lines: List<String>)

fun diffDatasets(old: Dataset, new: Dataset): List<BankChange> {
    val oldBanks = old.banks.associateBy { it.id }
    val newBanks = new.banks.associateBy { it.id }
    val changes = mutableListOf<BankChange>()
    for (bank in new.banks) {
        val prev = oldBanks[bank.id]
        if (prev == null) {
            changes += BankChange(bank.name, listOf("Новый банк: ${bank.categories.size} категорий"))
            continue
        }
        val lines = diffBank(prev, bank)
        if (lines.isNotEmpty()) changes += BankChange(bank.name, lines)
    }
    for (bank in old.banks) {
        if (bank.id !in newBanks) changes += BankChange(bank.name, listOf("Банк удалён из данных"))
    }
    return changes
}

fun diffBank(old: BankFile, new: BankFile): List<String> {
    val lines = mutableListOf<String>()
    if (old.name != new.name) lines += "Название: «${old.name}» → «${new.name}»"
    if (old.source.docDate != new.source.docDate && new.source.docDate != null) {
        lines += "Документ от ${humanDate(new.source.docDate)}"
    }
    val oldCats = old.categories.associateBy { it.name }
    val newCats = new.categories.associateBy { it.name }
    for (cat in new.categories) {
        val prev = oldCats[cat.name]
        if (prev == null) {
            lines += "+ «${cat.name}»" + codesSuffix(expandMcc(cat.mcc))
            continue
        }
        codeDelta(expandMcc(prev.mcc), expandMcc(cat.mcc))?.let { lines += "«${cat.name}»: $it" }
    }
    for (cat in old.categories) {
        if (cat.name !in newCats) lines += "− «${cat.name}»"
    }
    codeDelta(expandMcc(old.excluded), expandMcc(new.excluded))?.let { lines += "Без кешбэка: $it" }
    if (old.fallback != new.fallback) lines += "Вне категорий: ${new.fallback ?: "—"}"
    return lines
}

private fun codeDelta(old: Set<Int>, new: Set<Int>): String? {
    val added = new - old
    val removed = old - new
    if (added.isEmpty() && removed.isEmpty()) return null
    return listOfNotNull(
        added.takeIf { it.isNotEmpty() }?.let { "+${formatCodes(it)}" },
        removed.takeIf { it.isNotEmpty() }?.let { "−${formatCodes(it)}" },
    ).joinToString("  ")
}

private fun codesSuffix(codes: Set<Int>) = if (codes.isEmpty()) "" else ": ${formatCodes(codes)}"

/** "2026-04-02" -> "02.04.2026"; всё нестандартное показываем как есть. */
fun humanDate(iso: String?): String {
    if (iso == null) return "—"
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    val (y, mo, d) = m.destructured
    return "$d.$mo.$y"
}
