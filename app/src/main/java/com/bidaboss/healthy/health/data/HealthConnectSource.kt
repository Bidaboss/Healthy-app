package com.bidaboss.healthy.health.data

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bidaboss.healthy.core.watch.SleepSegment
import com.bidaboss.healthy.core.watch.SleepStage
import com.bidaboss.healthy.core.watch.SleepStages
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

    override suspend fun distanceGranted(): Boolean =
        client().permissionController.getGrantedPermissions().contains(DISTANCE_PERMISSION)

    override suspend fun day(date: LocalDate): WatchDay {
        val core = coreDay(date)
        // Sommeil, cœur et distance sont facultatifs : sans autorisation ou en cas d'échec, on les laisse vides.
        val extrasOk = runCatching { extrasGranted() }.getOrDefault(false)
        val distanceOk = runCatching { distanceGranted() }.getOrDefault(false)
        val sleep = if (extrasOk) runCatching { sleepMinutes(date) }.getOrNull() else null
        val heart = if (extrasOk) runCatching { heartRate(date) }.getOrNull() else null
        val distance = if (distanceOk) runCatching { distanceMeters(date) }.getOrNull() else null
        val detail = if (extrasOk) runCatching { sleepDetail(date) }.getOrNull() else null
        return core.copy(sleepMin = sleep, minBpm = heart?.first, avgBpm = heart?.second, distanceM = distance, sleep = detail)
    }

    /** Distance du jour en mètres, ou null si la montre n'en envoie pas. */
    private suspend fun distanceMeters(date: LocalDate): Int? {
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    date.atStartOfDay(zone()).toInstant(),
                    date.plusDays(1).atStartOfDay(zone()).toInstant(),
                ),
            ),
        )
        return result[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.roundToInt()?.takeIf { it > 0 }
    }

    /**
     * Détail de la nuit qui finit le matin de [date] : la plus longue session, ses phases (seulement celles que la
     * montre distingue : profond, léger, paradoxal, éveillé) et le cœur pendant la nuit.
     */
    private suspend fun sleepDetail(date: LocalDate): SleepDetail? {
        val range = TimeRangeFilter.between(
            date.minusDays(1).atTime(18, 0).atZone(zone()).toInstant(),
            date.atTime(18, 0).atZone(zone()).toInstant(),
        )
        val sessions = client().readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
        val session = sessions.maxByOrNull { Duration.between(it.startTime, it.endTime) } ?: return null
        val segments = SleepStages.merge(
            session.stages.mapNotNull { stage ->
                val kind = when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
                    SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
                    SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                    -> SleepStage.AWAKE
                    // « Endormi » sans précision ou inconnu : on n'invente pas de phase.
                    else -> return@mapNotNull null
                }
                SleepSegment(kind, Duration.between(stage.startTime, stage.endTime).toMinutes().toInt())
            },
        )
        val night = runCatching {
            client().aggregate(
                AggregateRequest(
                    metrics = setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                ),
            )
        }.getOrNull()
        return SleepDetail(
            start = LocalDateTime.ofInstant(session.startTime, zone()),
            end = LocalDateTime.ofInstant(session.endTime, zone()),
            segments = if (SleepStages.hasPhases(segments)) segments else emptyList(),
            nightAvgBpm = night?.get(HeartRateRecord.BPM_AVG)?.toInt(),
            nightMinBpm = night?.get(HeartRateRecord.BPM_MIN)?.toInt(),
        )
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

        /** Facultative aussi, ajoutée par la refonte : la distance du jour. */
        val DISTANCE_PERMISSION: String = HealthPermission.getReadPermission(DistanceRecord::class)

        /** Tout ce qu'on demande, et rien d'autre : pas d'écriture. */
        val PERMISSIONS: Set<String> = CORE_PERMISSIONS + EXTRA_PERMISSIONS + DISTANCE_PERMISSION

        /** Pour demander l'autorisation : le contrat d'Android, à lancer depuis un écran. */
        fun permissionContract() = PermissionController.createRequestPermissionResultContract()

        /** Écran de Health Connect où l'on voit et retire les accès. */
        fun manageIntent(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)
    }
}
