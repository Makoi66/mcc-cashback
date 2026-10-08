package dev.mcc.cashback.data

import android.graphics.Bitmap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Версия формата JSON в data/; поднимаем только при несовместимых изменениях. */
const val SCHEMA = 1

val DataJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class IndexFile(
    val schema: Int,
    val generated: String? = null,
    /** Пути к файлам банков относительно data/, порядок = порядок в приложении. */
    val banks: List<String>,
)

@Serializable
data class BankFile(
    val schema: Int = SCHEMA,
    val id: String,
    val name: String,
    /** "#RRGGBB"; без цвета банк рисуется нейтральным. */
    val color: String? = null,
    /** Путь к PNG относительно data/, например "logos/tbank.png". */
    val logo: String? = null,
    val source: Source = Source(),
    val categories: List<CategoryDto> = emptyList(),
    val excluded: List<String> = emptyList(),
    /** Оговорки к кодам без кешбэка: {"4511": "кроме Аэрофлота…"}. */
    @SerialName("excluded_notes") val excludedNotes: Map<String, String> = emptyMap(),
    /** Что получаешь вне категорий, например «Все покупки (1%)». */
    val fallback: String? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class Source(
    val url: String? = null,
    @SerialName("doc_id") val docId: String? = null,
    @SerialName("doc_date") val docDate: String? = null,
)

@Serializable
data class CategoryDto(
    val name: String,
    val group: String? = null,
    val description: String = "",
    /** "5411" или диапазон "3000-3303". */
    val mcc: List<String> = emptyList(),
    val note: String? = null,
    /** Оговорки к отдельным кодам: {"4511": "только Победа, ЮТэйр…"}. */
    @SerialName("mcc_notes") val mccNotes: Map<String, String> = emptyMap(),
)

/** Всё, что загружено и показывается сейчас. */
data class Dataset(
    val index: IndexFile,
    val banks: List<BankFile>,
    val origin: Origin,
    /** Логотипы по id банка; банка без логотипа (или с битым файлом) здесь нет. */
    val logos: Map<String, Bitmap> = emptyMap(),
) {
    enum class Origin { BUNDLED, SYNCED }
}
