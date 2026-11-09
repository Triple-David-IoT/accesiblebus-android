package dte.masteriot.accessiblebus.hmi

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/** One sound and one vibration per new request, remembered on disk by requestId. */
class NewRequestAlert(private val context: Context) {

    private val preferences = context.getSharedPreferences("hmi_alerted", Context.MODE_PRIVATE)

    /** True the first time this request is seen; false for every later time, also after a restart. */
    @Synchronized
    fun firstTime(requestId: String): Boolean {
        val known = preferences.getStringSet(KEY, emptySet()).orEmpty()
        if (requestId in known) return false
        preferences.edit().putStringSet(KEY, known.toList().takeLast(MAX - 1).toSet() + requestId).commit()
        return true
    }

    fun alert() {
        Log.i(TAG, "alert: new request")
        runCatching { RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play() }
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(400)
        }
    }

    private companion object {
        const val KEY = "ids"
        const val MAX = 200
        const val TAG = "HmiAlert"
    }
}