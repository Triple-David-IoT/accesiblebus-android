package dte.masteriot.accessiblebus.core.data

import android.content.Context

/**
 * Idempotency keys of the commands that have no answer yet, by command id. A key is saved before
 * the command is sent and removed when the server answers, so a retry after a timeout or after
 * the app is closed sends the same key and the server runs the command once.
 */
interface CommandKeys {
    fun get(commandId: String): String?

    fun put(commandId: String, key: String)

    fun remove(commandId: String)

    fun all(): Set<String>
}

class PreferencesCommandKeys(context: Context) : CommandKeys {

    private val preferences = context.getSharedPreferences("command_keys", Context.MODE_PRIVATE)

    override fun get(commandId: String): String? = preferences.getString(commandId, null)

    // commit, not apply: the key must be on disk before the command leaves the phone.
    override fun put(commandId: String, key: String) {
        preferences.edit().putString(commandId, key).commit()
    }

    override fun remove(commandId: String) {
        preferences.edit().remove(commandId).commit()
    }

    override fun all(): Set<String> = preferences.all.keys.toSet()
}
