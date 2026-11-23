package com.bidaboss.healthy.update.data

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
import com.bidaboss.healthy.core.update.RemoteApk
import com.bidaboss.healthy.workout.data.nextReminderAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Vérifie le dossier Drive une fois par jour, vers 10 h, même si l'app est fermée, et prévient par une
 * notification quand une nouvelle version attend (une seule fois par version). Même mécanique que les
 * rappels : alarme non exacte, qui programme la suivante ; le redémarrage du téléphone ou la mise à jour
 * de l'app la reprogramme. Seul réseau utilisé : le dossier des mises à jour (docs/architecture.md, « Réseau »).
 * Sans lien de dossier (build local), rien n'est programmé.
 */
class UpdateCheckScheduler(private val context: Context, private val hasFolder: () -> Boolean) {

    fun schedule() {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = intent()
        if (!hasFolder()) {
            alarms.cancel(intent)
            return
        }
        val at = nextReminderAt(LocalDateTime.now(), LocalTime.of(CHECK_HOUR, 0))
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), intent)
    }

    private fun intent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, UpdateCheckReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_CHECK = "com.bidaboss.healthy.action.CHECK_UPDATE"
        const val CHECK_HOUR = 10
    }
}

/** Reçoit l'alarme du matin, et le redémarrage du téléphone ou la mise à jour de l'app. */
class UpdateCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HealthyApplication).container
        container.updateCheckScheduler.schedule()
        if (intent.action != UpdateCheckScheduler.ACTION_CHECK) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Pas de réseau ou dossier muet : on réessaiera demain, sans bruit.
                val result = runCatching { container.updateRepository.checkAndRecord() }.getOrNull()
                val store = container.updateStore
                if (result is UpdateCheck.Available && store.notifiedVersionCode != result.apk.versionCode) {
                    if (UpdateNotifier.show(context, result.apk)) store.notifiedVersionCode = result.apk.versionCode
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Notification « Nouvelle version disponible » : elle ouvre l'app, qui affiche le bandeau de mise à jour. */
object UpdateNotifier {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 3

    /** Renvoie false si la permission de notifier manque (le bandeau dans l'app prend le relais). */
    fun show(context: Context, apk: RemoteApk): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "La version ${apk.versionName} de Healthy est prête. Ouvre l'app pour la mettre à jour."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nouvelle version disponible")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }
}
