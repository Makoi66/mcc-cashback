package dev.mcc.cashback.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Tab { LOOKUP, BANKS }

@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.LOOKUP) }
    var openBank by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = openBank != null) { openBank = null }
    BackHandler(enabled = openBank == null && tab != Tab.LOOKUP) { tab = Tab.LOOKUP }

    val bank = openBank?.let { id -> state.dataset?.banks?.firstOrNull { it.id == id } }
    if (bank != null) {
        BankDetailScreen(bank, state.dataset?.logos?.get(bank.id), onBack = { openBank = null })
    } else {
        Scaffold(
            // Отступ под статус-бар делает TopAppBar внутри экранов, иначе он задваивается.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.LOOKUP,
                        onClick = { tab = Tab.LOOKUP },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Код") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.BANKS,
                        onClick = { tab = Tab.BANKS },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        label = { Text("Банки") },
                    )
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when (tab) {
                Tab.LOOKUP -> LookupScreen(state, vm, modifier)
                Tab.BANKS -> BanksScreen(state, vm, onOpenBank = { openBank = it }, modifier)
            }
        }
    }

    state.syncOutcome?.let { SyncDialog(it, onDismiss = vm::dismissSyncOutcome) }
}

@Composable
fun SyncButton(state: UiState, vm: AppViewModel) {
    if (state.syncing) {
        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = vm::sync, enabled = state.dataset != null) {
            Icon(Icons.Default.Refresh, contentDescription = "Sync")
        }
    }
}

@Composable
private fun SyncDialog(outcome: SyncOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = {
            Text(
                when (outcome) {
                    is SyncOutcome.Success -> if (outcome.changes.isEmpty()) "Всё актуально" else "Данные обновлены"
                    is SyncOutcome.Failure -> "Не удалось обновить"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (outcome) {
                    is SyncOutcome.Success -> {
                        if (outcome.changes.isEmpty()) {
                            Text("Изменений нет.")
                        } else {
                            outcome.changes.forEach { change ->
                                Text(change.bankName, fontWeight = FontWeight.Bold)
                                change.lines.forEach { Text(it, fontSize = 14.sp) }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        outcome.warnings.forEach { Caveat(it) }
                    }
                    is SyncOutcome.Failure -> {
                        Text(outcome.message)
                        Spacer(Modifier.height(8.dp))
                        Text("Остались прежние данные.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}
