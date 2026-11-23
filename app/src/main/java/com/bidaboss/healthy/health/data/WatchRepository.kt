package com.bidaboss.healthy.health.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Ce qu'on montre de la montre : la journée, et les séances des derniers jours à proposer. */
data class WatchSnapshot(val day: WatchDay, val sessions: List<WatchSession>)

/**
 * Lecture des données de la montre. Une lecture qui échoue (Health Connect indisponible un instant,
 * autorisation retirée entre-temps) ne plante jamais : l'écran montre simplement moins.
 */
class WatchRepository(
    private val source: WatchSource,
    private val ignored: WatchIgnoredStore,
    private val history: WatchHistory,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val syncLock = Mutex()

    suspend fun status(): WatchStatus = runCatching { source.status() }.getOrDefault(WatchStatus.UNAVAILABLE)

    suspend fun extrasGranted(): Boolean = runCatching { source.extrasGranted() }.getOrDefault(false)

    suspend fun distanceGranted(): Boolean = runCatching { source.distanceGranted() }.getOrDefault(false)

    /**
     * La journée de [today] et les séances des [RECENT_DAYS] derniers jours : au moins [MIN_SESSION_MIN]
     * minutes (une marche de 3 minutes n'est pas une séance), pas écartées par le propriétaire, les plus récentes d'abord.
     * Rend null si la lecture échoue.
     */
    suspend fun snapshot(today: LocalDate): WatchSnapshot? = runCatching {
        val day = source.day(today)
        val skipped = ignored.ids()
        val sessions = source.sessions(today.minusDays(RECENT_DAYS - 1L), today)
            .filter { it.durationMin >= MIN_SESSION_MIN && it.id !in skipped }
            .sortedByDescending { it.startedAt }
        WatchSnapshot(day, sessions)
    }.getOrNull()

    /**
     * Garde les relevés de la montre sur le téléphone (PRD « Ma montre ») : à la première connexion, jusqu'à
     * [INITIAL_DAYS] jours de passé ; ensuite les [RESYNC_DAYS] derniers jours, relus et corrigés (la montre
     * se synchronise parfois en retard). Un jour sans rien n'écrit rien. À appeler en tâche de fond : ne plante
     * jamais, et deux appels en même temps se suivent au lieu de se doubler.
     */
    suspend fun syncHistory(today: LocalDate) = syncLock.withLock {
        runCatching {
            if (source.status() != WatchStatus.READY) return@runCatching
            val days = if (history.count() == 0) INITIAL_DAYS else RESYNC_DAYS
            for (back in (days - 1) downTo 0) {
                val date = today.minusDays(back.toLong())
                // Un jour qui ne se lit pas (trop ancien pour Health Connect) laisse un trou, pas une erreur.
                runCatching { source.day(date) }.getOrNull()?.let { history.save(date, it, now()) }
            }
        }
        Unit
    }

    /** Les relevés gardés entre deux jours inclus. */
    fun observeHistory(from: LocalDate, to: LocalDate): Flow<List<WatchDayRecord>> = history.observe(from, to)

    suspend fun historyCount(): Int = history.count()

    /** « Effacer l'historique de la montre » : ne touche ni aux repas, ni aux séances, ni aux activités. */
    suspend fun clearHistory() = history.clear()

    /** « Ignorer » une séance proposée : elle ne reviendra pas. */
    fun ignore(id: String) = ignored.add(id)

    companion object {
        const val RECENT_DAYS = 7
        const val MIN_SESSION_MIN = 10

        /** Jours de passé récupérés à la première connexion (ce que Health Connect rend par défaut). */
        const val INITIAL_DAYS = 30

        /** Jours relus à chaque lecture, pour corriger une synchronisation en retard. */
        const val RESYNC_DAYS = 3
    }
}

/** Séances écartées à la main. Préférences du téléphone : elles ne sont pas dans la sauvegarde. */
class WatchIgnoredStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("watch", Context.MODE_PRIVATE)

    fun ids(): Set<String> = ordered().toSet()

    // Les identifiants sont gardés dans l'ordre d'ajout (un Set de préférences n'a pas d'ordre).
    private fun ordered(): List<String> = prefs.getString(KEY_ORDERED, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }
        ?: prefs.getStringSet(KEY_IGNORED, emptySet()).orEmpty().toList()

    fun add(id: String) {
        // On ne garde que les plus récentes : une séance de plus de 7 jours n'est plus proposée de toute façon.
        val updated = (ordered() - id + id).takeLast(MAX_IDS)
        prefs.edit { putString(KEY_ORDERED, updated.joinToString(SEPARATOR)) }
    }

    private companion object {
        const val KEY_IGNORED = "ignored_session_ids"
        const val KEY_ORDERED = "ignored_session_ids_ordered"
        const val SEPARATOR = "\n"
        const val MAX_IDS = 200
    }
}
