package dev.mcc.cashback.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mcc.cashback.data.BankFile
import dev.mcc.cashback.data.CategoryDto
import dev.mcc.cashback.data.expandMcc
import dev.mcc.cashback.data.formatCodes
import dev.mcc.cashback.data.humanDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetailScreen(bank: BankFile, logo: Bitmap?, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val uri = LocalUriHandler.current
    val shown = remember(bank, query) { filter(bank.categories, query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BankLogo(bank.color, logo, 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(bank.name)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    val src = bank.source
                    val doc = listOfNotNull(src.docId, src.docDate?.let { "от ${humanDate(it)}" }).joinToString(" ")
                    if (doc.isNotEmpty()) Text("Документ: $doc", style = MaterialTheme.typography.bodyMedium)
                    src.url?.let { url ->
                        TextButton(onClick = { runCatching { uri.openUri(url) } }, contentPadding = PaddingValues(0.dp)) {
                            Text("Открыть источник")
                        }
                    }
                    bank.fallback?.let { Text("Вне категорий: $it", style = MaterialTheme.typography.bodyMedium) }
                    bank.notes.forEach { Caveat(it) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Категория или код") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // Без key: в отредактированном руками JSON названия категорий могут повториться.
            items(shown) { CategoryCard(it) }
            if (bank.excluded.isNotEmpty() && query.isBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Без кешбэка", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            SelectionContainer { Text(formatCodes(expandMcc(bank.excluded)), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: CategoryDto) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(cat.name, style = MaterialTheme.typography.titleMedium)
            cat.group?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (cat.description.isNotBlank()) Text(cat.description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            val codes = formatCodes(expandMcc(cat.mcc))
            SelectionContainer {
                Text(codes.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            cat.note?.let { Caveat(it) }
            cat.mccNotes.forEach { (code, note) -> Caveat("$code: $note") }
        }
    }
}

/** Поиск по названию/группе/описанию, а если ввели 4 цифры — по коду. */
private fun filter(categories: List<CategoryDto>, query: String): List<CategoryDto> {
    val q = query.trim()
    if (q.isEmpty()) return categories
    val code = q.takeIf { it.length == 4 && it.all(Char::isDigit) }?.toInt()
    return categories.filter { cat ->
        if (code != null) {
            code in expandMcc(cat.mcc)
        } else {
            listOfNotNull(cat.name, cat.group, cat.description).any { it.contains(q, ignoreCase = true) }
        }
    }
}
