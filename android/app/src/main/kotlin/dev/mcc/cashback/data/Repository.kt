package dev.mcc.cashback.data

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DataException(message: String) : Exception(message)

data class SyncResult(val dataset: Dataset, val changes: List<BankChange>, val warnings: List<String>)

/**
 * Данные лежат в двух местах: снимок в assets/data (вшит при сборке) и
 * filesDir/data (последний успешный Sync). Если Sync был — показываем его.
 */
class Repository(private val context: Context) {
    private val syncedDir = File(context.filesDir, "data")

    suspend fun load(): Dataset = withContext(Dispatchers.IO) {
        if (File(syncedDir, INDEX).exists()) {
            try {
                val read = { path: String -> File(syncedDir, path).readBytes() }
                return@withContext withLogos(parseDataset(Dataset.Origin.SYNCED, read), read)
            } catch (e: Exception) {
                // Битые локальные файлы не должны оставлять пустой экран — откатываемся на снимок.
                syncedDir.deleteRecursively()
            }
        }
        loadBundled()
    }

    suspend fun sync(baseUrl: String, current: Dataset): SyncResult = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
        val fetched = linkedMapOf<String, ByteArray>()
        val parsed = parseDataset(Dataset.Origin.SYNCED) { path ->
            download(base + path).also { fetched[path] = it }
        }
        // Логотип не критичен: если не скачался, банк просто будет без него.
        val warnings = mutableListOf<String>()
        for (path in parsed.banks.mapNotNull { it.logo }.distinct()) {
            try {
                checkPath(path)
                fetched[path] = download(base + path)
            } catch (e: Exception) {
                warnings += "Логотип $path: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        val dataset = withLogos(parsed) { fetched[it] }
        // Пишем во временный каталог и подменяем целиком, чтобы не остаться с половиной файлов.
        val tmp = File(context.filesDir, "data.new").apply { deleteRecursively() }
        for ((path, bytes) in fetched) {
            File(tmp, path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        }
        syncedDir.deleteRecursively()
        if (!tmp.renameTo(syncedDir)) throw IOException("не удалось сохранить данные")
        SyncResult(dataset, diffDatasets(current, dataset), warnings)
    }

    /** Забыть Sync и вернуться к данным, вшитым в APK. */
    suspend fun resetToBundled(): Dataset = withContext(Dispatchers.IO) {
        syncedDir.deleteRecursively()
        loadBundled()
    }

    private fun loadBundled(): Dataset {
        val read = { path: String -> readAsset(path) }
        return withLogos(parseDataset(Dataset.Origin.BUNDLED, read), read)
    }

    private fun withLogos(dataset: Dataset, read: (String) -> ByteArray?): Dataset {
        val logos = dataset.banks.mapNotNull { bank ->
            val path = bank.logo ?: return@mapNotNull null
            val bytes = runCatching { read(path) }.getOrNull() ?: return@mapNotNull null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bank.id to it }
        }.toMap()
        return dataset.copy(logos = logos)
    }

    private fun readAsset(path: String): ByteArray =
        context.assets.open("data/$path").use { it.readBytes() }

    private fun download(url: String): ByteArray {
        // raw.githubusercontent.com кэширует ~5 минут; параметр в URL даёт свежую копию сразу после push.
        val conn = URL("$url?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Cache-Control", "no-cache")
            val code = conn.responseCode
            if (code != 200) throw DataException("${url.substringAfterLast('/')}: HTTP $code")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val INDEX = "index.json"
    }
}

/**
 * Разбирает index.json и всё, на что он ссылается. Общая часть для снимка,
 * локальной копии и Sync — так скачанное проверяется ровно так же, как вшитое.
 */
fun parseDataset(
    origin: Dataset.Origin,
    read: (String) -> ByteArray,
): Dataset {
    val index = parseFile(Repository.INDEX) { DataJson.decodeFromString(IndexFile.serializer(), read(Repository.INDEX).decodeToString()) }
    if (index.schema > SCHEMA) throw DataException("Данные нового формата (schema ${index.schema}) — обнови приложение")
    val banks = index.banks.map { path ->
        checkPath(path)
        val bank = parseFile(path) { DataJson.decodeFromString(BankFile.serializer(), read(path).decodeToString()) }
        if (bank.schema > SCHEMA) throw DataException("$path: schema ${bank.schema} — обнови приложение")
        if (bank.id.isBlank() || bank.name.isBlank()) throw DataException("$path: пустой id или name")
        bank
    }
    banks.groupBy { it.id }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
        throw DataException("id «$it» повторяется у нескольких банков")
    }
    return Dataset(index, banks, origin)
}

private fun <T> parseFile(path: String, block: () -> T): T =
    try {
        block()
    } catch (e: DataException) {
        throw e
    } catch (e: IOException) {
        throw DataException("$path: ${e.message ?: "ошибка сети"}")
    } catch (e: Exception) {
        throw DataException("$path: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}")
    }

internal fun checkPath(path: String) {
    if (path.isBlank() || path.startsWith("/") || ".." in path || '\\' in path) {
        throw DataException("недопустимый путь в index.json: «$path»")
    }
}
