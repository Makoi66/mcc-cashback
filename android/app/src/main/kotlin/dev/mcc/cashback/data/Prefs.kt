package dev.mcc.cashback.data

import android.content.Context
import dev.mcc.cashback.BuildConfig

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var dataUrl: String
        get() = sp.getString(KEY_URL, null) ?: BuildConfig.DEFAULT_DATA_URL
        set(value) = sp.edit().putString(KEY_URL, value.trim()).apply()

    var disabledBanks: Set<String>
        get() = sp.getStringSet(KEY_DISABLED, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_DISABLED, value).apply()

    var recent: List<String>
        get() = sp.getString(KEY_RECENT, "")!!.split(',').filter { it.length == 4 }
        set(value) = sp.edit().putString(KEY_RECENT, value.joinToString(",")).apply()

    var lastSync: Long
        get() = sp.getLong(KEY_LAST_SYNC, 0)
        set(value) = sp.edit().putLong(KEY_LAST_SYNC, value).apply()

    private companion object {
        const val KEY_URL = "data_url"
        const val KEY_DISABLED = "disabled_banks"
        const val KEY_RECENT = "recent_codes"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
