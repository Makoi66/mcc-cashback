package dev.mcc.cashback.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mcc.cashback.data.BankResult
import dev.mcc.cashback.data.Verdict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupScreen(state: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    // Ввели 4 цифры — прячем клавиатуру, чтобы были видны результаты.
    LaunchedEffect(state.input.length == 4) {
        if (state.input.length == 4) keyboard?.hide()
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("MCC Кешбэк") },
            actions = { SyncButton(state, vm) },
        )
        CodeField(
            value = state.input,
            onValue = vm::onInput,
            onClear = {
                vm.onInput("")
                focus.requestFocus()
                keyboard?.show()
            },
            modifier = Modifier.focusRequester(focus).padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        when {
            state.loadError != null -> Message("Не удалось загрузить данные: ${state.loadError}")
            state.dataset == null -> Message("Загрузка…")
            state.dataset.banks.isEmpty() -> Message("Нет данных ни по одному банку. Нажми Sync вверху.")
            state.input.length < 4 -> Recent(state, vm)
            else -> Results(state)
        }
    }
}

@Composable
private fun CodeField(value: String, onValue: (String) -> Unit, onClear: () -> Unit, modifier: Modifier) {
    BasicTextField(
        // Курсор всегда в конце: цифры только дописываются или стираются с хвоста.
        value = TextFieldValue(value, selection = TextRange(value.length)),
        onValueChange = { new ->
            // Если код уже введён, следующая цифра начинает новый — не надо сначала стирать.
            val digits = new.text.filter(Char::isDigit)
            onValue(if (value.length == 4 && digits.length > 4) digits.drop(4) else digits)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = modifier.fillMaxWidth(),
        decorationBox = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { i ->
                        val active = i == value.length
                        Box(
                            Modifier
                                .weight(1f)
                                .height(72.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                .border(
                                    width = if (active) 2.dp else 0.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                value.getOrNull(i)?.toString() ?: "",
                                fontSize = 40.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                IconButton(onClick = onClear, enabled = value.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = "Стереть")
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Recent(state: UiState, vm: AppViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Введи 4 цифры MCC — покажу категорию в каждом банке.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.recent.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Недавние", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::clearRecent) { Text("Очистить") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recent.forEach { code ->
                    val title = state.dataset?.dictionary?.get(code)?.title
                    SuggestionChip(
                        onClick = { vm.onInput(code) },
                        label = { Text(if (title != null) "$code · $title" else code, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Results(state: UiState) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(
                    state.info?.title ?: "Нет в справочнике MCC",
                    style = MaterialTheme.typography.titleLarge,
                )
                state.info?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.results.isEmpty()) {
            item { Text("Все банки выключены на вкладке «Банки».", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.results, key = { it.bank.id }) { BankResultCard(it, state.dataset?.logos?.get(it.bank.id)) }
    }
}

@Composable
private fun BankResultCard(result: BankResult, logo: Bitmap?) {
    val verdict = result.verdict
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (verdict is Verdict.InCategories) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(bankColor(result.bank.color)),
            )
            Column(Modifier.padding(14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BankLogo(result.bank.color, logo, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(result.bank.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                when (verdict) {
                    is Verdict.InCategories -> verdict.matches.forEachIndexed { i, m ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Text(m.category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        m.category.group?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        (m.note ?: m.category.note)?.let { Caveat(it) }
                    }
                    is Verdict.Excluded -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Без кешбэка", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        }
                        verdict.note?.let { Caveat(it) }
                    }
                    is Verdict.Outside -> {
                        Text("Не входит в категории", style = MaterialTheme.typography.titleMedium)
                        verdict.fallback?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

@Composable
fun Caveat(text: String) {
    Text(
        "⚠ $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun Message(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}
