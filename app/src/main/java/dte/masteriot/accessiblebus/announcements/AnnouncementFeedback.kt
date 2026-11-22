
package dte.masteriot.accessiblebus.announcements

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

class AnnouncementFeedback(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager

            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(
                Context.VIBRATOR_SERVICE
            ) as Vibrator
        }

    fun vibrate(enabled: Boolean) {
        if (!enabled || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    250L,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(250L)
        }
    }
}
