package dev.mcc.cashback.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mcc.cashback.data.BankFile
import dev.mcc.cashback.data.Dataset
import dev.mcc.cashback.data.humanDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanksScreen(state: UiState, vm: AppViewModel, onOpenBank: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Банки") }, actions = { SyncButton(state, vm) })
        LazyColumn(
            // Поле адреса внизу списка не должно уходить под клавиатуру.
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val banks = state.dataset?.banks.orEmpty()
            if (banks.isEmpty()) item { Message("Банков в данных нет.") }
            items(banks, key = { it.id }) { bank ->
                BankRow(
                    bank,
                    logo = state.dataset?.logos?.get(bank.id),
                    enabled = bank.id !in state.disabled,
                    onToggle = { vm.toggleBank(bank.id) },
                    onOpen = { onOpenBank(bank.id) },
                )
            }
            item { DataCard(state, vm) }
        }
    }
}

@Composable
private fun BankRow(bank: BankFile, logo: Bitmap?, enabled: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BankLogo(bank.color, logo, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(bank.name, style = MaterialTheme.typography.titleMedium)
                val date = bank.source.docDate?.let { " · документ от ${humanDate(it)}" }.orEmpty()
                Text(
                    "${bank.categories.size} категорий$date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun DataCard(state: UiState, vm: AppViewModel) {
    val dataset = state.dataset ?: return
    var url by rememberSaveable(state.dataUrl) { mutableStateOf(state.dataUrl) }
    val focus = LocalFocusManager.current

    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Данные", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val origin = when (dataset.origin) {
            Dataset.Origin.BUNDLED -> "встроенные в приложение"
            Dataset.Origin.SYNCED -> "из Sync" + (state.lastSync.takeIf { it > 0 }?.let { " ${formatTime(it)}" } ?: "")
        }
        Text("Сейчас: $origin", style = MaterialTheme.typography.bodyMedium)
        dataset.index.generated?.let {
            Text("Собраны: ${humanDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = vm::sync, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.syncing) "Обновляю…" else "Sync — скачать актуальные")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Адрес данных (папка с index.json)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                vm.setDataUrl(url)
                focus.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (url.trim() != state.dataUrl) {
            TextButton(onClick = { vm.setDataUrl(url); focus.clearFocus() }) { Text("Сохранить адрес") }
        }
        if (dataset.origin == Dataset.Origin.SYNCED) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::resetToBundled, modifier = Modifier.fillMaxWidth()) {
                Text("Вернуть встроенные данные")
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(millis))
