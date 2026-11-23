package com.bidaboss.healthy.health.data

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Lecture seule de Health Connect (la base de santé d'Android, où Garmin Connect écrit). Aucun réseau,
 * aucun compte : tout se passe sur le téléphone, avec l'autorisation du propriétaire.
 * Fil Garmin, validé par le propriétaire le 22/11/2026.
 */
class HealthConnectSource(
    private val context: Context,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : WatchSource {

    override suspend fun status(): WatchStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> WatchStatus.UNAVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> WatchStatus.UPDATE_REQUIRED
        else -> {
            val granted = runCatching { client().permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
            if (granted.containsAll(CORE_PERMISSIONS)) WatchStatus.READY else WatchStatus.NEEDS_PERMISSION
        }
    }

    override suspend fun extrasGranted(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(EXTRA_PERMISSIONS)

    override suspend fun day(date: LocalDate): WatchDay {
        val core = coreDay(date)
        // Sommeil et cœur sont facultatifs : sans autorisation ou en cas d'échec, on les laisse vides.
        val extrasOk = runCatching { extrasGranted() }.getOrDefault(false)
        val sleep = if (extrasOk) runCatching { sleepMinutes(date) }.getOrNull() else null
        val heart = if (extrasOk) runCatching { heartRate(date) }.getOrNull() else null
        return core.copy(sleepMin = sleep, minBpm = heart?.first, avgBpm = heart?.second)
    }

    /** La nuit qui finit le matin de [date] : de la veille 18 h à [date] 18 h. */
    private suspend fun sleepMinutes(date: LocalDate): Int? {
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    date.minusDays(1).atTime(18, 0).atZone(zone()).toInstant(),
                    date.atTime(18, 0).atZone(zone()).toInstant(),
                ),
            ),
        )
        return result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toInt()?.takeIf { it > 0 }
    }

    /** (le plus bas, la moyenne) de la journée, en battements par minute. */
    private suspend fun heartRate(date: LocalDate): Pair<Int, Int>? {
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(
                    date.atStartOfDay(zone()).toInstant(),
                    date.plusDays(1).atStartOfDay(zone()).toInstant(),
                ),
            ),
        )
        val min = result[HeartRateRecord.BPM_MIN] ?: return null
        val avg = result[HeartRateRecord.BPM_AVG] ?: return null
        return min.toInt() to avg.toInt()
    }

    private suspend fun coreDay(date: LocalDate): WatchDay {
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    date.atStartOfDay(zone()).toInstant(),
                    date.plusDays(1).atStartOfDay(zone()).toInstant(),
                ),
            ),
        )
        return WatchDay(
            steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
            activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.roundToInt(),
        )
    }

    override suspend fun sessions(from: LocalDate, to: LocalDate): List<WatchSession> {
        val range = TimeRangeFilter.between(
            from.atStartOfDay(zone()).toInstant(),
            to.plusDays(1).atStartOfDay(zone()).toInstant(),
        )
        val sessions = mutableListOf<WatchSession>()
        var pageToken: String? = null
        do {
            val page = client().readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range, pageToken = pageToken))
            page.records.forEach { record ->
                sessions += WatchSession(
                    id = record.metadata.id,
                    type = watchActivityName(record.exerciseType, record.title),
                    startedAt = LocalDateTime.ofInstant(record.startTime, zone()),
                    durationMin = Duration.between(record.startTime, record.endTime).toMinutes().toInt(),
                )
            }
            pageToken = page.pageToken
        } while (pageToken != null)
        return sessions
    }

    private fun client() = HealthConnectClient.getOrCreate(context)

    companion object {
        /** Lectures indispensables à la connexion : pas, calories actives, séances. */
        val CORE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )

        /** Facultatives, ajoutées à l'étape 2 : sommeil et fréquence cardiaque. */
        val EXTRA_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )

        /** Tout ce qu'on demande, et rien d'autre : pas d'écriture. */
        val PERMISSIONS: Set<String> = CORE_PERMISSIONS + EXTRA_PERMISSIONS

        /** Pour demander l'autorisation : le contrat d'Android, à lancer depuis un écran. */
        fun permissionContract() = PermissionController.createRequestPermissionResultContract()

        /** Écran de Health Connect où l'on voit et retire les accès. */
        fun manageIntent(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)
    }
}
