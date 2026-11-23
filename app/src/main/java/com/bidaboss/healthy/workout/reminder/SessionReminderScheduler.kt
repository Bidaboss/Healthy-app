package com.bidaboss.healthy.workout.reminder

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
import com.bidaboss.healthy.workout.data.ReminderSettings
import com.bidaboss.healthy.workout.data.nextReminderAt
import com.bidaboss.healthy.workout.data.sessionToRemind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Programme le rappel quotidien avec AlarmManager. Alarme non exacte (pas de permission spéciale) :
 * Android peut la décaler de quelques minutes pour économiser la batterie, ce qui suffit pour un rappel.
 * Chaque rappel programme le suivant ; le redémarrage du téléphone et la mise à jour de l'app aussi.
 */
class SessionReminderScheduler(private val context: Context) {

    fun apply(settings: ReminderSettings) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = reminderIntent()
        if (!settings.enabled) {
            alarms.cancel(intent)
            return
        }
        val at = nextReminderAt(LocalDateTime.now(), settings.time)
        val atMs = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)
    }

    private fun reminderIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SessionReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REMIND = "com.bidaboss.healthy.action.SESSION_REMINDER"
    }
}

/** Reçoit l'alarme du rappel, et le redémarrage du téléphone ou la mise à jour de l'app pour reprogrammer. */
class SessionReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HealthyApplication).container
        val settings = container.reminderSettings.settings.value
        container.reminderScheduler.apply(settings)
        if (intent.action != SessionReminderScheduler.ACTION_REMIND || !settings.enabled) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                val session = sessionToRemind(
                    plan = container.workoutRepository.getActivePlan(),
                    today = today,
                    sessionsDoneToday = container.workoutLogRepository.observeSessionCount(today, today).first(),
                )
                if (session != null) notify(context, session.title)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, sessionTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rappels de séance", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("C'est jour de sport")
            .setContentText("$sessionTitle t'attend. Appuie pour commencer.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "session_reminders"
        const val NOTIFICATION_ID = 1
    }
}
