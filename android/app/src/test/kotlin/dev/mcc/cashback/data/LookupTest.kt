package dev.mcc.cashback.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LookupTest {
    private val bank = BankFile(
        id = "test",
        name = "Тест",
        categories = listOf(
            CategoryDto("Супермаркеты", mcc = listOf("5411", "5422")),
            CategoryDto("Авиабилеты", mcc = listOf("3000-3303", "4511"), mccNotes = mapOf("4511" to "только Победа")),
            CategoryDto("Красота", mcc = listOf("5977")),
            CategoryDto("Косметика", mcc = listOf("5977")),
        ),
        excluded = listOf("4829", "6050-6051"),
        excludedNotes = mapOf("6050-6051" to "квази-кэш"),
        fallback = "1% на всё",
    )

    @Test
    fun parsesItems() {
        assertEquals(5411..5411, parseMccItem("5411"))
        assertEquals(3000..3303, parseMccItem("3000-3303"))
        assertEquals(3000..3303, parseMccItem("3000 – 3303"))
        assertNull(parseMccItem("3303-3000"))
        assertNull(parseMccItem("abc"))
    }

    @Test
    fun formatsCodesAsRanges() {
        assertEquals("0742, 5411–5413, 5422", formatCodes(listOf(5413, 742, 5411, 5412, 5422)))
        assertEquals("5411, 5412", formatCodes(listOf(5412, 5411)))
    }

    @Test
    fun verdicts() {
        val idx = BankIndex(bank)
        val superm = idx.lookup(5411).verdict as Verdict.InCategories
        assertEquals(listOf("Супермаркеты"), superm.matches.map { it.category.name })

        val air = idx.lookup(3100).verdict as Verdict.InCategories
        assertNull(air.matches.single().note)
        assertEquals("только Победа", (idx.lookup(4511).verdict as Verdict.InCategories).matches.single().note)

        val both = idx.lookup(5977).verdict as Verdict.InCategories
        assertEquals(listOf("Красота", "Косметика"), both.matches.map { it.category.name })

        assertEquals(Verdict.Excluded("квази-кэш"), idx.lookup(6051).verdict)
        assertEquals(Verdict.Excluded(null), idx.lookup(4829).verdict)
        assertEquals(Verdict.Outside("1% на всё"), idx.lookup(7995).verdict)
    }

    @Test
    fun diffReportsCodeChanges() {
        val newer = bank.copy(
            categories = bank.categories.map {
                if (it.name == "Супермаркеты") it.copy(mcc = listOf("5411", "5499")) else it
            } + CategoryDto("Такси", mcc = listOf("4121")),
        )
        val lines = diffBank(bank, newer)
        assertTrue(lines.toString(), "«Супермаркеты»: +5499  −5422" in lines)
        assertTrue(lines.toString(), "+ «Такси»: 4121" in lines)
    }

    @Test
    fun rejectsBadIndex() {
        val files = mapOf(
            "index.json" to """{"schema":1,"banks":["../evil.json"]}""",
        )
        assertThrows(DataException::class.java) {
            parseDataset(Dataset.Origin.SYNCED, emptyMap()) { files.getValue(it).encodeToByteArray() }
        }
        val dup = mapOf(
            "index.json" to """{"schema":1,"banks":["a.json","b.json"]}""",
            "a.json" to """{"schema":1,"id":"x","name":"A"}""",
            "b.json" to """{"schema":1,"id":"x","name":"B"}""",
        )
        assertThrows(DataException::class.java) {
            parseDataset(Dataset.Origin.SYNCED, emptyMap()) { dup.getValue(it).encodeToByteArray() }
        }
    }

    /** Настоящие JSON из data/ в репозитории должны читаться приложением. */
    @Test
    fun realDataParses() {
        val dir = File("../../data")
        val ds = parseDataset(Dataset.Origin.BUNDLED, emptyMap()) { File(dir, it).readBytes() }
        assertTrue(ds.banks.isNotEmpty())
        assertTrue(ds.dictionary.size > 500)
        val lookup = Lookup(ds)
        val tbank = lookup.lookup(5411, emptySet()).first { it.bank.id == "tbank" }
        assertEquals("Супермаркеты", (tbank.verdict as Verdict.InCategories).matches.single().category.name)
        assertEquals("Продукты", lookup.describe(5411)?.title)
        assertEquals(listOf("tbank", "alfa", "vtb", "ozon"), ds.banks.map { it.id })
        ds.banks.forEach { bank -> assertTrue(bank.id, File(dir, bank.logo!!).isFile) }

        // 3990 — общий код Яндекса: у Альфы он в нескольких категориях, у каждой своя оговорка.
        val alfa = lookup.lookup(3990, emptySet()).first { it.bank.id == "alfa" }.verdict as Verdict.InCategories
        assertTrue(alfa.matches.size > 5)
        assertEquals("Яндекс Заправки (экосистема Яндекс)", alfa.matches.first { it.category.name == "АЗС" }.note)

        val vtbAir = lookup.lookup(4511, emptySet()).first { it.bank.id == "vtb" }.verdict as Verdict.InCategories
        assertTrue(vtbAir.matches.all { it.note!!.contains("Победа") })
        assertTrue(lookup.lookup(6011, emptySet()).all { it.verdict is Verdict.Excluded })
    }
}
