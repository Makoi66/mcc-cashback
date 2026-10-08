package dev.mcc.cashback.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mcc.cashback.data.BankChange
import dev.mcc.cashback.data.BankResult
import dev.mcc.cashback.data.Dataset
import dev.mcc.cashback.data.Lookup
import dev.mcc.cashback.data.Prefs
import dev.mcc.cashback.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val dataset: Dataset? = null,
    val loadError: String? = null,
    val input: String = "",
    val results: List<BankResult> = emptyList(),
    val recent: List<String> = emptyList(),
    val disabled: Set<String> = emptySet(),
    val dataUrl: String = "",
    val lastSync: Long = 0,
    val syncing: Boolean = false,
    val syncOutcome: SyncOutcome? = null,
)

sealed interface SyncOutcome {
    data class Success(val changes: List<BankChange>, val warnings: List<String>) : SyncOutcome
    data class Failure(val message: String) : SyncOutcome
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = Prefs(app)
    private var lookup: Lookup? = null

    private val _state = MutableStateFlow(
        UiState(recent = prefs.recent, disabled = prefs.disabledBanks, dataUrl = prefs.dataUrl, lastSync = prefs.lastSync),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                setDataset(repo.load())
            } catch (e: Exception) {
                _state.update { it.copy(loadError = e.message ?: e.toString()) }
            }
        }
    }

    fun onInput(raw: String) {
        val digits = raw.filter(Char::isDigit).take(4)
        _state.update { it.copy(input = digits) }
        recompute()
        if (digits.length == 4) remember(digits)
    }

    fun toggleBank(id: String) {
        val disabled = _state.value.disabled.let { if (id in it) it - id else it + id }
        prefs.disabledBanks = disabled
        _state.update { it.copy(disabled = disabled) }
        recompute()
    }

    fun setDataUrl(url: String) {
        prefs.dataUrl = url
        _state.update { it.copy(dataUrl = prefs.dataUrl) }
    }

    fun clearRecent() {
        prefs.recent = emptyList()
        _state.update { it.copy(recent = emptyList()) }
    }

    fun sync() {
        val current = _state.value.dataset ?: return
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val outcome = try {
                val result = repo.sync(prefs.dataUrl, current)
                prefs.lastSync = System.currentTimeMillis()
                setDataset(result.dataset)
                SyncOutcome.Success(result.changes, result.warnings)
            } catch (e: Exception) {
                SyncOutcome.Failure(e.message ?: e.toString())
            }
            _state.update { it.copy(syncing = false, syncOutcome = outcome, lastSync = prefs.lastSync) }
        }
    }

    fun resetToBundled() {
        viewModelScope.launch {
            setDataset(repo.resetToBundled())
            prefs.lastSync = 0
            _state.update { it.copy(lastSync = 0) }
        }
    }

    fun dismissSyncOutcome() = _state.update { it.copy(syncOutcome = null) }

    private fun setDataset(dataset: Dataset) {
        lookup = Lookup(dataset)
        _state.update { it.copy(dataset = dataset, loadError = null) }
        recompute()
    }

    private fun recompute() {
        val s = _state.value
        val l = lookup
        val code = s.input.takeIf { it.length == 4 }?.toInt()
        _state.update {
            if (l == null || code == null) {
                it.copy(results = emptyList())
            } else {
                it.copy(results = l.lookup(code, s.disabled))
            }
        }
    }

    private fun remember(code: String) {
        val recent = (listOf(code) + _state.value.recent.filter { it != code }).take(12)
        prefs.recent = recent
        _state.update { it.copy(recent = recent) }
    }
}
