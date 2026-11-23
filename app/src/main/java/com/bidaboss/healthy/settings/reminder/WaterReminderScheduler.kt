package com.bidaboss.healthy.settings.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bidaboss.healthy.HealthyApplication
import com.bidaboss.healthy.MainActivity
import com.bidaboss.healthy.R
import com.bidaboss.healthy.core.body.WaterCheck
import com.bidaboss.healthy.core.body.WaterReminder
import com.bidaboss.healthy.core.body.WaterReminderMessage
import com.bidaboss.healthy.workout.data.nextReminderAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Programme les quatre vérifications de l'eau (11 h 30, 14 h, 17 h 30, 20 h 30), comme les autres
 * rappels : alarmes non exactes, chacune programme la suivante, le redémarrage les reprogramme.
 */
class WaterReminderScheduler(private val context: Context) {

    fun apply(enabled: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        for (check in WaterCheck.entries) {
            val intent = checkIntent(check)
            if (!enabled) {
                alarms.cancel(intent)
                continue
            }
            val at = nextReminderAt(LocalDateTime.now(), LocalTime.of(check.hour, check.minute))
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                intent,
            )
        }
    }

    // Codes 101 à 104 : distincts de ceux des notifications de motivation (1 et 2).
    private fun checkIntent(check: WaterCheck): PendingIntent = PendingIntent.getBroadcast(
        context,
        101 + check.ordinal,
        Intent(context, WaterReminderReceiver::class.java).setAction(ACTION_CHECK).putExtra(EXTRA_CHECK, check.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_CHECK = "com.bidaboss.healthy.action.WATER_CHECK"
        const val EXTRA_CHECK = "check"
    }
}

/** Reçoit les vérifications de l'eau, et le redémarrage du téléphone ou la mise à jour de l'app. */
class WaterReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HealthyApplication).container
        val enabled = container.waterReminderStore.enabled.value
        container.waterReminderScheduler.apply(enabled)
        if (intent.action != WaterReminderScheduler.ACTION_CHECK || !enabled) return
        val check = WaterCheck.entries.firstOrNull { it.name == intent.getStringExtra(WaterReminderScheduler.EXTRA_CHECK) }
            ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val day = container.motivationRepository.snapshot(LocalDate.now())
                WaterReminder.message(check, day.waterMl, day.waterTargetMl)?.let { WaterReminderNotifier.show(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Affiche un rappel pour boire (aussi utilisé par « Essayer » dans Réglages). */
object WaterReminderNotifier {
    private const val CHANNEL_ID = "water"
    private const val NOTIFICATION_ID = 3

    fun show(context: Context, message: WaterReminderMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rappels pour boire", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
