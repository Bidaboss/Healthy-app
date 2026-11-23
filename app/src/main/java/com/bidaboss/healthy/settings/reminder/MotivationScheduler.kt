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
import com.bidaboss.healthy.core.motivation.Motivation
import com.bidaboss.healthy.core.motivation.MotivationMessage
import com.bidaboss.healthy.core.motivation.MotivationSlot
import com.bidaboss.healthy.core.motivation.MotivationTone
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
 * Programme les deux notifications de motivation du jour (12 h 30 et 19 h), comme le rappel de séance :
 * alarmes non exactes, chacune programme la suivante, et le redémarrage du téléphone les reprogramme.
 */
class MotivationScheduler(private val context: Context) {

    fun apply(tone: MotivationTone?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        for (slot in MotivationSlot.entries) {
            val intent = slotIntent(slot)
            if (tone == null) {
                alarms.cancel(intent)
                continue
            }
            val at = nextReminderAt(LocalDateTime.now(), LocalTime.of(slot.hour, slot.minute))
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                intent,
            )
        }
    }

    private fun slotIntent(slot: MotivationSlot): PendingIntent = PendingIntent.getBroadcast(
        context,
        slot.ordinal + 1,
        Intent(context, MotivationReceiver::class.java).setAction(ACTION_MOTIVATE).putExtra(EXTRA_SLOT, slot.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_MOTIVATE = "com.bidaboss.healthy.action.MOTIVATION"
        const val EXTRA_SLOT = "slot"
    }
}

/** Reçoit les alarmes de motivation, et le redémarrage du téléphone ou la mise à jour de l'app. */
class MotivationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HealthyApplication).container
        val tone = container.motivationStore.tone.value
        container.motivationScheduler.apply(tone)
        if (intent.action != MotivationScheduler.ACTION_MOTIVATE || tone == null) return
        val slot = MotivationSlot.entries.firstOrNull { it.name == intent.getStringExtra(MotivationScheduler.EXTRA_SLOT) }
            ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val day = container.motivationRepository.snapshot(LocalDate.now())
                MotivationNotifier.show(context, Motivation.message(tone, slot, day))
            } finally {
                pending.finish()
            }
        }
    }
}

/** Affiche une notification de motivation (aussi utilisé par « Essayer » dans Réglages). */
object MotivationNotifier {
    private const val CHANNEL_ID = "motivation"
    private const val NOTIFICATION_ID = 2

    fun show(context: Context, message: MotivationMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Motivation", NotificationManager.IMPORTANCE_DEFAULT),
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
